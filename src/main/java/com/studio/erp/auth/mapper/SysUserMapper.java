package com.studio.erp.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studio.erp.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 账号 Mapper。
 *
 * <p>【为什么这里要手写 SQL，而不是用 LambdaQueryWrapper 拼】
 * 鉴权查询需要联 employee 取姓名，而且必须"一条 SQL 出结果"。
 * 用 Wrapper 只能查单表，就得查两次；手写 SQL 一次搞定，也更好在日志里看懂。
 * 注意所有参数都用 #{} 绑定，绝不拼字符串 —— 登录接口是注入攻击的首选目标。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 按登录名查鉴权视图 */
    @Select("""
            SELECT u.id, u.username, u.password_hash, u.employee_id, u.status, u.must_change_pwd,
                   e.name AS employee_name
            FROM sys_user u
            LEFT JOIN employee e ON e.id = u.employee_id
            WHERE u.username = #{username}
            """)
    LoginUserRow selectLoginUserByUsername(@Param("username") String username);

    /** 按主键查鉴权视图（每个请求都要用） */
    @Select("""
            SELECT u.id, u.username, u.password_hash, u.employee_id, u.status, u.must_change_pwd,
                   e.name AS employee_name
            FROM sys_user u
            LEFT JOIN employee e ON e.id = u.employee_id
            WHERE u.id = #{userId}
            """)
    LoginUserRow selectLoginUserById(@Param("userId") Long userId);

    /** 取角色码集合；一人多角色（主管兼设计）会返回多条 */
    @Select("""
            SELECT r.code
            FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
            """)
    List<String> selectRoleCodes(@Param("userId") Long userId);
}
