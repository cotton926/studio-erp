package com.studio.erp.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.studio.erp.employee.dto.EmployeeOptionVO;
import com.studio.erp.employee.dto.EmployeeQuery;
import com.studio.erp.employee.dto.EmployeeVO;
import com.studio.erp.employee.entity.Employee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 员工 Mapper。
 *
 * <p>注意：不提供物理删除。员工离职用 status=0，因为他的历史业绩、
 * 派单记录、业绩明细都还指向这个 id，删了就是一堆孤儿数据
 * —— 更糟的是报表会把他名下的业绩算给"无人"，对账时永远差一块。
 */
@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {

    /**
     * 列表查询的公共字段部分。
     *
     * <p>【为什么用相关子查询拼角色，而不是 JOIN + GROUP BY】
     * 一个员工可能有多个角色，JOIN 之后一个员工会出现多行，必须 GROUP BY 去重。
     * 而 MySQL 8 默认开启 ONLY_FULL_GROUP_BY，SELECT 里每个非聚合列都得进 GROUP BY，
     * 一漏就报错；全写进去又极难维护。更要紧的是：GROUP BY 与分页同时使用时，
     * 分页的 total 会按"展开后的行数"算，出现"总共 30 条但翻到第 2 页就空了"的怪现象。
     * 子查询只取一个值，行数不变，分页天然正确。
     */
    String BASE_COLUMNS = """
            SELECT e.id, e.emp_no, e.name, e.dept_id, d.name AS dept_name,
                   e.position, e.emp_level, e.is_designer, e.phone, e.hire_date, e.status, e.created_at,
                   u.id AS user_id, u.username, u.status AS account_status, u.last_login_at,
                   (SELECT GROUP_CONCAT(r.code ORDER BY r.id)
                      FROM sys_user_role ur
                      JOIN sys_role r ON r.id = ur.role_id
                     WHERE ur.user_id = u.id) AS role_codes
              FROM employee e
              LEFT JOIN dept d ON d.id = e.dept_id
              LEFT JOIN sys_user u ON u.employee_id = e.id
            """;

    /**
     * 员工分页列表（含账号信息）。
     *
     * <p>第一个参数必须是 IPage 且由 MyBatis-Plus 自动改写 SQL 追加 LIMIT；
     * 前提是 PaginationInnerInterceptor 已注册，否则会静默查全表。
     */
    @Select("""
            <script>
            """ + BASE_COLUMNS + """
            <where>
                <if test="q.keyword != null and q.keyword != ''">
                    AND (e.name LIKE CONCAT('%', #{q.keyword}, '%')
                         OR e.emp_no LIKE CONCAT('%', #{q.keyword}, '%')
                         OR e.phone LIKE CONCAT('%', #{q.keyword}, '%'))
                </if>
                <if test="q.deptId != null">
                    AND e.dept_id = #{q.deptId}
                </if>
                <if test="q.position != null and q.position != ''">
                    AND e.position = #{q.position}
                </if>
                <if test="q.status != null">
                    AND e.status = #{q.status}
                </if>
                <if test="q.isDesigner != null">
                    AND e.is_designer = #{q.isDesigner}
                </if>
                <if test="q.hasAccount != null and q.hasAccount">
                    AND u.id IS NOT NULL
                </if>
                <if test="q.hasAccount != null and q.hasAccount == false">
                    AND u.id IS NULL
                </if>
            </where>
            ORDER BY e.status DESC, e.emp_no ASC
            </script>
            """)
    IPage<EmployeeVO> selectEmployeePage(IPage<EmployeeVO> page, @Param("q") EmployeeQuery q);

    /** 单个员工详情，字段口径与列表一致，避免"列表和详情对不上"的投诉 */
    @Select(BASE_COLUMNS + " WHERE e.id = #{id}")
    EmployeeVO selectEmployeeDetail(@Param("id") Long id);

    /**
     * 派单下拉：只返回在职且参与业绩分成的人。
     *
     * <p>【为什么按 is_designer 过滤而不是 position = 'DESIGNER'】
     * 主管自己出图在国内设计公司非常普遍。按岗位过滤会把他排除在派单之外，
     * 结果是他私下让人代录，业绩记到别人头上 —— 这种账最难查。
     */
    @Select("""
            <script>
            SELECT e.id, e.name, e.emp_no, d.name AS dept_name, e.position
              FROM employee e
              LEFT JOIN dept d ON d.id = e.dept_id
             WHERE e.status = 1
               AND e.is_designer = 1
            <if test="deptId != null">
               AND e.dept_id = #{deptId}
            </if>
             ORDER BY e.emp_no ASC
            </script>
            """)
    List<EmployeeOptionVO> selectDesignerOptions(@Param("deptId") Long deptId);
}
