package com.studio.erp.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studio.erp.auth.entity.SysRole;
import com.studio.erp.auth.entity.SysUser;
import com.studio.erp.auth.mapper.SysRoleMapper;
import com.studio.erp.auth.mapper.SysUserMapper;
import com.studio.erp.employee.entity.Employee;
import com.studio.erp.employee.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


/**
 * 首次启动创建管理员账号。
 *
 * <p>【为什么不在 Flyway 脚本里 INSERT 管理员】
 * 迁移脚本会进 Git。写明文密码等于把生产密码公开在代码仓库；
 * 写 BCrypt 哈希则意味着所有环境（开发、测试、生产）共用同一个初始密码，
 * 只要有一个环境的库泄露，全公司账号都能登。
 *
 * <p>【为什么密码只从环境变量读，且缺失时不兜底】
 * "没有配置就用 admin/123456"是无数真实事故的来源 —— 部署时忘了设变量，
 * 系统照样能用，直到被人登进去才发现。这里宁可少建一个账号并大声告警，
 * 也不留一个默认口令。运维看到告警会去补变量，忘了设反而不会出事。
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final String ADMIN_USERNAME = "admin";

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final EmployeeMapper employeeMapper;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.admin.init-password:}")
    private String initPassword;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        Long existing = userMapper.selectCount(new LambdaQueryWrapper<>());
        if (existing != null && existing > 0) {
            // 已有账号就什么都不做。这里绝不做"重置密码"——
            // 否则每次重启都会把管理员密码改回初始值，属于自己给自己开后门。
            log.info("已存在 {} 个登录账号，跳过管理员初始化", existing);
            return;
        }

        if (initPassword == null || initPassword.isBlank()) {
            log.warn("""
                    
                    ============================================================
                     未配置 ADMIN_INIT_PASSWORD，管理员账号未创建。
                     系统当前任何人都无法登录。
                     请在环境变量（或 .env.local）中设置后重启：
                       ADMIN_INIT_PASSWORD=至少8位且含字母和数字的密码
                    ============================================================
                    """);
            return;
        }

        if (!PasswordPolicy.isStrong(initPassword)) {
            log.error("ADMIN_INIT_PASSWORD 强度不足（需至少 8 位且包含字母和数字），管理员账号未创建");
            return;
        }

        SysRole adminRole = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, "ADMIN"));
        if (adminRole == null) {
            log.error("角色表中找不到 ADMIN，说明 V2 初始化数据没生效，管理员账号未创建");
            return;
        }

        Employee employee = new Employee();
        employee.setEmpNo("ADMIN001");
        employee.setName("系统管理员");
        employee.setPosition("ADMIN");
        // 管理员不参与业绩分成，否则会出现在业绩排行榜里
        employee.setIsDesigner(0);
        employee.setStatus(1);
        employeeMapper.insert(employee);

        SysUser user = new SysUser();
        user.setUsername(ADMIN_USERNAME);
        user.setPasswordHash(passwordEncoder.encode(initPassword));
        user.setEmployeeId(employee.getId());
        user.setStatus(1);
        // 首次登录必须改密：初始密码通过环境变量传递，可能留在部署脚本、
        // CI 日志或同事的聊天记录里，不能长期有效
        user.setMustChangePwd(1);
        userMapper.insert(user);

        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)",
                user.getId(), adminRole.getId());

        log.info("已创建管理员账号 username={} employeeId={}（首次登录需修改密码）",
                ADMIN_USERNAME, employee.getId());
    }
}
