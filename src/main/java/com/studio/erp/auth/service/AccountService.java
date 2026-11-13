package com.studio.erp.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.studio.erp.auth.CurrentUserHolder;
import com.studio.erp.auth.PasswordPolicy;
import com.studio.erp.auth.dto.AccountCreateRequest;
import com.studio.erp.auth.dto.AccountCreateResult;
import com.studio.erp.auth.dto.AccountQuery;
import com.studio.erp.auth.dto.AccountVO;
import com.studio.erp.auth.dto.PasswordResetResult;
import com.studio.erp.auth.entity.SysRole;
import com.studio.erp.auth.entity.SysUser;
import com.studio.erp.auth.mapper.AccountQueryMapper;
import com.studio.erp.auth.mapper.SysRoleMapper;
import com.studio.erp.auth.mapper.SysUserMapper;
import com.studio.erp.auth.mapper.SysUserRoleMapper;
import com.studio.erp.common.api.PageResult;
import com.studio.erp.common.audit.AuditService;
import com.studio.erp.common.exception.BizException;
import com.studio.erp.common.exception.ErrorCode;
import com.studio.erp.employee.entity.Employee;
import com.studio.erp.employee.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 账号管理：开号、重置密码、启停用、调整角色。
 *
 * <p>【这个类里最要紧的三个约束】
 * 1) 建号时密码只出现一次，之后系统里只有哈希，管理员再也看不到；
 * 2) 最后一个启用的管理员不能被停用、也不能被摘掉 ADMIN 角色；
 * 3) 停用类操作不能对自己做，避免管理员把自己锁在门外。
 * 这三条都是为了同一个目标：**系统永远有人能管，且没人能通过界面把密码捞出来**。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final AccountQueryMapper accountQueryMapper;
    private final EmployeeMapper employeeMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    /** 账号分页列表 */
    public PageResult<AccountVO> page(AccountQuery query) {
        Page<AccountVO> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<AccountVO> result = accountQueryMapper.selectAccountPage(page, query);
        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(), result.getRecords());
    }

    /** 角色字典，供账号编辑弹窗渲染复选框 */
    public List<SysRole> listRoles() {
        return roleMapper.selectList(Wrappers.<SysRole>lambdaQuery().orderByAsc(SysRole::getId));
    }

    /**
     * 给员工开账号。
     *
     * <p>【为什么初始密码由系统生成并只返回一次】
     * 让管理员自己填密码，实际结果几乎总是 123456 / 公司名 + 年份，
     * 而且管理员会一直记得这个密码，账号实际上变成两个人共用。
     * 系统生成随机密码 + 强制首登修改，能保证"只有本人知道最终密码"。
     *
     * <p>【为什么初始密码不写日志】
     * 日志会被收集、会有人翻查、还可能被打包发给外部排查问题。
     * 密码一旦进日志，等于永久泄露 —— 而这份日志里恰好还有用户名。
     */
    @Transactional(rollbackFor = Exception.class)
    public AccountCreateResult create(AccountCreateRequest request) {
        Employee employee = employeeMapper.selectById(request.getEmployeeId());
        if (employee == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        if (employee.getStatus() == null || employee.getStatus() != 1) {
            throw new BizException(ErrorCode.EMPLOYEE_RESIGNED);
        }

        Long accountCount = userMapper.selectCount(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getEmployeeId, employee.getId()));
        if (accountCount != null && accountCount > 0) {
            throw new BizException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        }

        String username = StringUtils.hasText(request.getUsername())
                ? request.getUsername().trim()
                : employee.getEmpNo();

        // 先查一次给出友好提示；下面的唯一索引才是并发下的真正防线
        Long sameName = userMapper.selectCount(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
        if (sameName != null && sameName > 0) {
            throw new BizException(ErrorCode.USERNAME_DUPLICATED);
        }

        List<String> roleCodes = (request.getRoleCodes() == null || request.getRoleCodes().isEmpty())
                ? List.of(defaultRoleOf(employee))
                : request.getRoleCodes();
        List<Long> roleIds = resolveRoleIds(roleCodes);

        String rawPassword = StringUtils.hasText(request.getInitialPassword())
                ? request.getInitialPassword()
                : PasswordPolicy.generate();
        PasswordPolicy.assertStrong(rawPassword);

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setEmployeeId(employee.getId());
        user.setStatus(1);
        // 无论如何都置 1：即使管理员指定了初始密码，也要求本人登录后改掉
        user.setMustChangePwd(1);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            /*
             * 这里必须捕获。唯一索引冲突在不做处理时会变成 500「系统开小差」，
             * 而真实原因只是"另一个管理员刚刚给同一个人开了号"。
             * 并发场景下先查后插必然有这个窗口，靠唯一索引兜底、靠这里翻译成人话。
             */
            log.warn("建号唯一索引冲突 employeeId={} username={}", employee.getId(), username);
            throw new BizException(ErrorCode.ACCOUNT_ALREADY_EXISTS, "登录名或该员工的账号刚刚被创建，请刷新后重试");
        }

        userRoleMapper.insertBatch(user.getId(), roleIds);

        log.info("已开账号 userId={} username={} employeeId={} roles={}",
                user.getId(), username, employee.getId(), roleCodes);
        return new AccountCreateResult(user.getId(), username, employee.getName(), rawPassword);
    }

    /**
     * 重置密码。
     *
     * @param userId      目标账号
     * @param newPassword 为空则随机生成
     * @return 登录名 + 新的明文密码（本次响应返回，之后不再可查）
     */
    @Transactional(rollbackFor = Exception.class)
    public PasswordResetResult resetPassword(Long userId, String newPassword) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "账号不存在");
        }

        String rawPassword = StringUtils.hasText(newPassword) ? newPassword : PasswordPolicy.generate();
        PasswordPolicy.assertStrong(rawPassword);

        if (passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BizException(ErrorCode.PASSWORD_SAME, "新密码不能与原密码相同");
        }

        SysUser update = new SysUser();
        update.setId(userId);
        update.setPasswordHash(passwordEncoder.encode(rawPassword));
        // 重置后重新要求改密：重置通常是"员工忘了密码"，
        // 但这串密码经过管理员之手，必须让本人再换一次才算只有本人知道
        update.setMustChangePwd(1);
        userMapper.updateById(update);

        kickOut(userId);
        log.info("已重置密码 userId={} username={}", userId, user.getUsername());
        // 只记"谁重置了谁"，绝不记密码本身：审计表会被导出、被截图
        auditService.event("SYS_USER", userId, "重置密码");
        return new PasswordResetResult(user.getUsername(), rawPassword);
    }

    /**
     * 启用 / 停用账号。
     *
     * <p>停用不是删除：账号记录要保留，否则历史订单的创建人、业绩的确认人都成了空。
     * 停用后令牌立即失效，员工手上的页面下一次刷新就会跳登录页。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long userId, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "状态只能是 0（停用）或 1（启用）");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "账号不存在");
        }

        if (status == 0) {
            assertNotSelf(user);
            boolean isAdmin = userMapper.selectRoleCodes(userId).contains("ADMIN");
            if (isAdmin && accountQueryMapper.countOtherActiveAdmins(userId) == 0) {
                throw new BizException(ErrorCode.LAST_ADMIN_PROTECTED);
            }
        }

        SysUser update = new SysUser();
        update.setId(userId);
        update.setStatus(status);
        userMapper.updateById(update);

        if (status == 0) {
            kickOut(userId);
        }
        log.info("账号状态变更 userId={} username={} status={}", userId, user.getUsername(), status);
        // 停用/启用本身带状态旧值，记下来才能区分"从未启用"和"被停用过"
        auditService.record("SYS_USER", userId, "status",
                user.getStatus(), status);
    }

    /**
     * 调整角色（整体覆盖）。
     *
     * <p>不需要踢下线：每个请求都会重新查角色，改动下一个请求就生效。
     * 这也是当初不用 Session 存权限的原因 —— 提权、降权都能立刻看到效果。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateRoles(Long userId, List<String> roleCodes) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "账号不存在");
        }
        List<Long> roleIds = resolveRoleIds(roleCodes);

        boolean losingAdmin = roleCodes.stream().noneMatch("ADMIN"::equals);
        if (losingAdmin && user.getStatus() != null && user.getStatus() == 1
                && userMapper.selectRoleCodes(userId).contains("ADMIN")
                && accountQueryMapper.countOtherActiveAdmins(userId) == 0) {
            throw new BizException(ErrorCode.LAST_ADMIN_PROTECTED);
        }

        List<String> beforeRoles = userMapper.selectRoleCodes(userId);
        userRoleMapper.deleteByUserId(userId);
        userRoleMapper.insertBatch(userId, roleIds);
        log.info("账号角色变更 userId={} username={} roles={}", userId, user.getUsername(), roleCodes);
        // 角色决定能看多少钱，提权比改数据更值得留痕
        auditService.record("SYS_USER", userId, "roles", beforeRoles, roleCodes);
    }

    /**
     * 员工离职时同步停用账号（由员工模块调用）。
     *
     * <p>【为什么离职必须联动停号，而不是等管理员想起来】
     * 离职员工的账号能继续登录，等于前员工随时能导出客户名单和业绩数据。
     * 这类事故最常见的版本就是"人走了三个月，账号还在"。
     * 这里做成一件事：改员工状态的人自然会触发停号，不依赖记性。
     */
    @Transactional(rollbackFor = Exception.class)
    public void disableByEmployeeId(Long employeeId) {
        SysUser user = userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getEmployeeId, employeeId));
        if (user == null || user.getStatus() == null || user.getStatus() == 0) {
            return;
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setStatus(0);
        userMapper.updateById(update);
        kickOut(user.getId());
        log.info("员工离职，已同步停用账号 userId={} username={}", user.getId(), user.getUsername());
    }

    /* ------------------------------------------------------------------ */

    /**
     * 校验角色码并换成角色ID。
     * 不信任前端传来的角色码：它可能是不存在的、也可能已被删除，
     * 直接拿去插入会得到一个"有角色记录但没有对应角色"的账号，
     * 表现为登录后所有菜单都是空的，查半天查不出原因。
     */
    private List<Long> resolveRoleIds(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "至少选择一个角色");
        }
        List<String> distinct = roleCodes.stream().distinct().toList();
        List<SysRole> roles = roleMapper.selectList(
                Wrappers.<SysRole>lambdaQuery().in(SysRole::getCode, distinct));
        if (roles.size() != distinct.size()) {
            List<String> found = roles.stream().map(SysRole::getCode).toList();
            throw new BizException(ErrorCode.ROLE_INVALID,
                    "角色不存在：" + distinct.stream().filter(c -> !found.contains(c)).toList());
        }
        return roles.stream().map(SysRole::getId).toList();
    }

    /** 岗位 → 默认角色。新岗位没在这张表里时按设计师处理（最小权限） */
    private String defaultRoleOf(Employee employee) {
        String position = employee.getPosition();
        if (position == null) {
            return "DESIGNER";
        }
        return switch (position) {
            case "ADMIN" -> "ADMIN";
            case "SUPERVISOR" -> "SUPERVISOR";
            case "CS" -> "CS";
            case "FINANCE" -> "FINANCE";
            default -> "DESIGNER";
        };
    }

    /** 不能停用自己：一旦点错，管理员当场失去所有权限，且没有第二个入口能恢复 */
    private void assertNotSelf(SysUser target) {
        Long currentEmployeeId = CurrentUserHolder.get() == null
                ? null : CurrentUserHolder.get().employeeId();
        if (currentEmployeeId != null && currentEmployeeId.equals(target.getEmployeeId())) {
            throw new BizException(ErrorCode.CANNOT_OPERATE_SELF);
        }
    }

    /**
     * 让该账号所有在线会话立即失效。
     *
     * <p>只改数据库状态其实也够（每个请求都会查库校验），但那样用户要等到
     * 下一次点按钮才发现被踢；主动清会话能让页面在下次刷新时直接跳登录页，
     * 体验上"立刻生效"，也避免被停用后还能继续浏览已经加载好的页面。
     */
    private void kickOut(Long userId) {
        try {
            StpUtil.logout(userId);
        } catch (Exception e) {
            // 清会话失败不应影响业务结果（账号状态已经落库），记一条日志就够
            log.warn("清除会话失败 userId={}", userId, e);
        }
    }
}
