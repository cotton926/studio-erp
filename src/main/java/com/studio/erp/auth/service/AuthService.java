package com.studio.erp.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import com.studio.erp.auth.CurrentUser;
import com.studio.erp.auth.LoginAttemptGuard;
import com.studio.erp.auth.PasswordPolicy;
import com.studio.erp.auth.RoleScopeResolver;
import com.studio.erp.auth.ScopeType;
import com.studio.erp.auth.dto.ChangePasswordRequest;
import com.studio.erp.auth.dto.CurrentUserVO;
import com.studio.erp.auth.dto.LoginRequest;
import com.studio.erp.auth.dto.LoginResponse;
import com.studio.erp.auth.entity.SysUser;
import com.studio.erp.auth.mapper.LoginUserRow;
import com.studio.erp.auth.mapper.SysUserMapper;
import com.studio.erp.common.exception.BizException;
import com.studio.erp.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;


/**
 * 认证服务。
 *
 * <p>【登录流程的每一步为什么这么排】
 * 1. 先查锁定状态 —— 已锁定就不必再算一次 BCrypt（60ms，白烧 CPU）；
 * 2. 查账号 + 校验密码 —— 无论账号不存在还是密码错，对外都是同一句话，
 *    防止用户名枚举；
 * 3. 校验账号状态 —— 放在密码之后，这样"账号已停用"只会告诉真正知道密码的人，
 *    不会变成探测账号是否存在的第二条路径；
 * 4. 生成令牌 —— 用 Sa-Token，登录态默认存内存，够这个体量用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * 账号不存在时也要跑一次 BCrypt 比对。
     * 为什么：直接 return 的耗时会明显短于"算出哈希再比"，攻击者靠响应时间差
     * 就能判断哪些用户名真实存在。这个假哈希是固定值，比对必然失败，
     * 但把两条分支的耗时拉平了。
     */
    private static final String DUMMY_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5B0G1H1Sb1n8Y5xq0o5z0oYQ2m3Ku";

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptGuard attemptGuard;

    /**
     * 登录。
     *
     * @return 令牌 + 当前登录人视图
     */
    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim();
        attemptGuard.assertNotLocked(username);

        LoginUserRow row = userMapper.selectLoginUserByUsername(username);
        if (row == null) {
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            attemptGuard.recordFailure(username);
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }

        if (!passwordEncoder.matches(request.password(), row.getPasswordHash())) {
            attemptGuard.recordFailure(username);
            log.warn("登录失败，密码错误 username={}", username);
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }

        // 密码正确后才告知停用状态，避免它变成"账号是否存在"的探测器
        if (row.getStatus() == null || row.getStatus() != 1) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }

        attemptGuard.clear(username);

        // loginId 用主键：用户名将来允许修改，主键不会变，令牌不会因此失效
        StpUtil.login(row.getId());
        touchLastLogin(row.getId());

        Set<String> roles = loadRoleCodes(row.getId());
        CurrentUser user = new CurrentUser(
                row.getEmployeeId(), row.getUsername(), roles, RoleScopeResolver.resolve(roles));

        log.info("登录成功 username={} employeeId={} roles={}", row.getUsername(), row.getEmployeeId(), roles);
        return new LoginResponse(
                StpUtil.getTokenValue(),
                StpUtil.getTokenName(),
                isMustChangePwd(row),
                new CurrentUserVO(row.getId(), row.getUsername(), row.getEmployeeId(),
                        row.getEmployeeName(), roles, user.scope(), isMustChangePwd(row)));
    }

    /** 退出登录 */
    public void logout() {
        StpUtil.logout();
    }

    /** 当前登录人信息，供前端刷新页面后重建菜单与权限 */
    public CurrentUserVO currentUserInfo(long userId) {
        LoginUserRow row = userMapper.selectLoginUserById(userId);
        if (row == null) {
            // 令牌有效但账号已被删除：立刻踢下线，不能让它继续访问接口
            StpUtil.logout(userId);
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        Set<String> roles = loadRoleCodes(userId);
        ScopeType scope = RoleScopeResolver.resolve(roles);
        return new CurrentUserVO(userId, row.getUsername(), row.getEmployeeId(),
                row.getEmployeeName(), roles, scope, isMustChangePwd(row));
    }

    /**
     * 每个请求构建一次登录上下文（拦截器调用）。
     *
     * <p>【为什么每个请求都查库，而不是登录时把身份塞进 Sa-Token 的 Session】
     * 塞进 Session 意味着"改了角色要等用户重新登录才生效"，
     * 而现实中"把某人从设计师提成主管"是最常见的操作，管理者会立刻去验证效果，
     * 发现没变就会认为系统坏了。这里用两次索引查询换来"权限改动立即生效"，
     * 几十人的系统每次请求多 1ms 完全值得。
     * 若将来 QPS 上去了，再在 SaSession 里加 30 秒短缓存即可。
     */
    public LoginContext loadContext(long userId) {
        LoginUserRow row = userMapper.selectLoginUserById(userId);
        if (row == null || row.getStatus() == null || row.getStatus() != 1) {
            // 账号被删除或停用：令牌还在也要立刻失效
            StpUtil.logout(userId);
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        Set<String> roles = loadRoleCodes(userId);
        CurrentUser user = new CurrentUser(
                row.getEmployeeId(), row.getUsername(), roles, RoleScopeResolver.resolve(roles));
        return new LoginContext(user, isMustChangePwd(row));
    }

    /**
     * 修改密码。
     *
     * <p>【改完为什么强制下线】
     * 改密码是对"账号可能已被他人掌握"的响应动作。如果其他设备上的令牌继续有效，
     * 改密码就毫无意义。这里直接把该账号所有会话清掉，要求重新登录。
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(long userId, ChangePasswordRequest request) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "当前密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.PASSWORD_SAME);
        }
        // 强度规则统一走 PasswordPolicy：改密、建号、重置密码必须是同一套标准，
        // 各写一份迟早会漂移，最后变成"重置出来的密码能通过校验，自己改却通不过"
        PasswordPolicy.assertStrong(request.newPassword());

        SysUser update = new SysUser();
        update.setId(userId);
        update.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        update.setMustChangePwd(0);
        userMapper.updateById(update);

        StpUtil.logout(userId);
        log.info("用户 {} 修改密码成功，已清除全部会话", user.getUsername());
    }

    private Set<String> loadRoleCodes(long userId) {
        List<String> codes = userMapper.selectRoleCodes(userId);
        return codes == null ? Set.of() : Set.copyOf(codes);
    }

    private boolean isMustChangePwd(LoginUserRow row) {
        return row.getMustChangePwd() != null && row.getMustChangePwd() == 1;
    }

    private void touchLastLogin(Long userId) {
        // 只更新这一列：用 updateById 传一个只带 id 和该字段的对象，
        // MyBatis-Plus 默认忽略 null 字段，不会把其他列覆盖成 null
        SysUser update = new SysUser();
        update.setId(userId);
        update.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(update);
    }

    /**
     * 请求级的登录上下文。
     *
     * @param user          身份快照，放进 CurrentUserHolder
     * @param mustChangePwd 是否必须先改密码（服务端也要拦，不能只靠前端弹窗）
     */
    public record LoginContext(CurrentUser user, boolean mustChangePwd) {
    }
}
