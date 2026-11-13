package com.studio.erp.auth.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.studio.erp.auth.dto.AccountQuery;
import com.studio.erp.auth.dto.AccountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 账号管理用的只读查询。
 *
 * <p>【为什么不放进 SysUserMapper】
 * SysUserMapper 里的 SQL 是鉴权热路径（每个请求都跑），口径必须绝对稳定。
 * 管理页面的查询会随着"加个筛选条件、多显示一列"频繁改动，
 * 混在一起很容易顺手改坏了鉴权 SQL —— 那种故障是全员登不上系统，
 * 而且往往发生在改完一个看起来无关的页面的当天晚上。
 */
@Mapper
public interface AccountQueryMapper {

    String BASE_COLUMNS = """
            SELECT u.id, u.username, u.employee_id, u.status, u.must_change_pwd,
                   u.last_login_at, u.created_at,
                   e.name AS employee_name, e.emp_no, e.position,
                   (SELECT GROUP_CONCAT(r.code ORDER BY r.id)
                      FROM sys_user_role ur
                      JOIN sys_role r ON r.id = ur.role_id
                     WHERE ur.user_id = u.id) AS role_codes
              FROM sys_user u
              LEFT JOIN employee e ON e.id = u.employee_id
            """;

    /** 账号分页列表 */
    @Select("""
            <script>
            """ + BASE_COLUMNS + """
            <where>
                <if test="q.keyword != null and q.keyword != ''">
                    AND (u.username LIKE CONCAT('%', #{q.keyword}, '%')
                         OR e.name LIKE CONCAT('%', #{q.keyword}, '%')
                         OR e.emp_no LIKE CONCAT('%', #{q.keyword}, '%'))
                </if>
                <if test="q.status != null">
                    AND u.status = #{q.status}
                </if>
                <if test="q.roleCode != null and q.roleCode != ''">
                    AND EXISTS (SELECT 1 FROM sys_user_role ur2
                                  JOIN sys_role r2 ON r2.id = ur2.role_id
                                 WHERE ur2.user_id = u.id AND r2.code = #{q.roleCode})
                </if>
            </where>
            ORDER BY u.status DESC, u.id ASC
            </script>
            """)
    IPage<AccountVO> selectAccountPage(IPage<AccountVO> page, @Param("q") AccountQuery q);

    @Select(BASE_COLUMNS + " WHERE u.id = #{id}")
    AccountVO selectAccountById(@Param("id") Long id);

    @Select(BASE_COLUMNS + " WHERE u.employee_id = #{employeeId}")
    AccountVO selectAccountByEmployeeId(@Param("employeeId") Long employeeId);

    /**
     * 统计"除某个账号之外，还有几个启用的管理员"。
     *
     * <p>【为什么排除自己】
     * 判断的是"把目标账号的管理员角色去掉（或停用它）之后，系统里还有没有人能管"。
     * 不排除自己的话，最后一个管理员永远能被降级，降完就没人能改回来了 ——
     * 这种情况只能改数据库，客户现场做不到。
     *
     * <p>【为什么用 COALESCE 而不是 &lt;if&gt;】
     * SQL 里 `id &lt;&gt; NULL` 的结果是 NULL 而不是 true，整条语句会查不到任何行，
     * 于是"没传排除ID"反而被判定成"还有 0 个管理员"。用 COALESCE 兜成 -1，
     * 语义就稳定了。这种 NULL 陷阱写的时候完全看不出来，只有真跑起来才暴露。
     */
    @Select("""
            SELECT COUNT(DISTINCT u.id)
              FROM sys_user u
              JOIN sys_user_role ur ON ur.user_id = u.id
              JOIN sys_role r ON r.id = ur.role_id AND r.code = 'ADMIN'
             WHERE u.status = 1
               AND u.id <> COALESCE(#{excludeUserId}, -1)
            """)
    int countOtherActiveAdmins(@Param("excludeUserId") Long excludeUserId);
}
