package com.studio.erp.salary.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * 工资核算用的只读查询。
 *
 * <p>【为什么业绩口径要由 Java 传进来，而不是在 SQL 里写死 ledger_type IN (2,3,4)】
 * "哪几种流水算业绩"这件事已经由 {@code LedgerType.countsAsPerformance()} 定义了。
 * 如果在 SQL 里再手写一遍数字，日后新增一种流水类型（比如"跨月补录"），
 * 极容易改了枚举忘了改 SQL —— 结果是那部分钱永远不算进工资，而页面一切正常。
 * 类型列表由枚举算出来传进来，规则就只有一份。
 */
@Mapper
public interface SalaryQueryMapper {

    /**
     * 按员工汇总某期间的业绩。
     *
     * @param types 计入业绩的流水类型码（由枚举推导，不写死）
     */
    @Select("""
            <script>
            SELECT employee_id AS employeeId, SUM(amount) AS amount
              FROM performance_ledger
             WHERE period = #{period}
               AND ledger_type IN
               <foreach collection="types" item="t" open="(" separator="," close=")">#{t}</foreach>
             GROUP BY employee_id
            </script>
            """)
    List<EmployeePerfRow> sumPerformanceByEmployee(@Param("period") String period,
                                                   @Param("types") List<Integer> types);

    /** 单个员工的期间业绩合计 */
    @Select("""
            <script>
            SELECT COALESCE(SUM(amount), 0)
              FROM performance_ledger
             WHERE period = #{period}
               AND employee_id = #{employeeId}
               AND ledger_type IN
               <foreach collection="types" item="t" open="(" separator="," close=")">#{t}</foreach>
            </script>
            """)
    BigDecimal sumPerformanceOf(@Param("employeeId") Long employeeId,
                                @Param("period") String period,
                                @Param("types") List<Integer> types);

    /**
     * 按员工汇总某期间的签单额。
     *
     * <p>【签单额为什么不走 performance_ledger，而是实时从订单汇总】
     * 业绩流水是用来记"真实发生的钱"的，只该有四种：确认、冲销、调整。
     * 签单额是"接了多少活"，它是订单的派生指标 —— 主管发现单价录错、改一次，
     * 签单额就该跟着变。如果也落成流水，改一次价就得补一条冲销再补一条正的，
     * 而冲销流的"计入业绩"标记会让工资跟着错（REVERSE 是算业绩的）。
     * 让它保持派生，整类问题就不存在了。
     *
     * <p>【哪些单算签单额】
     * 状态 2/3/4（已确认、执行中、已交付）且签单日期落在该月。
     * 排除待定价（还没价）与已取消（9 反而比 4 大，所以不能用 &gt;= 2 简单过滤）。
     */
    @Select("""
            SELECT a.employee_id AS employeeId,
                   ROUND(SUM(oi.amount * a.ratio_bp / 10000), 2) AS amount
              FROM sales_order o
              JOIN order_item oi ON oi.order_id = o.id
              JOIN order_item_assignee a ON a.order_item_id = oi.id
             WHERE o.deleted = 0
               AND o.status IN (2, 3, 4)
               AND oi.status = 1
               AND oi.item_type = 1
               AND DATE_FORMAT(o.sign_date, '%Y-%m') = #{period}
             GROUP BY a.employee_id
            """)
    List<EmployeePerfRow> sumSignAmountByEmployee(@Param("period") String period);

    @Select("""
            SELECT COALESCE(ROUND(SUM(oi.amount * a.ratio_bp / 10000), 2), 0)
              FROM sales_order o
              JOIN order_item oi ON oi.order_id = o.id
              JOIN order_item_assignee a ON a.order_item_id = oi.id
             WHERE o.deleted = 0
               AND o.status IN (2, 3, 4)
               AND oi.status = 1
               AND oi.item_type = 1
               AND DATE_FORMAT(o.sign_date, '%Y-%m') = #{period}
               AND a.employee_id = #{employeeId}
            """)
    BigDecimal sumSignAmountOf(@Param("employeeId") Long employeeId,
                               @Param("period") String period);

    /**
     * 在职人员清单。
     *
     * <p>【为什么把离职的排除掉】
     * 离职员工当月仍可能因为尾款到账产生业绩，但那笔钱应该走"离职结算"单独处理，
     * 不该混在常规工资表里 —— 否则主管每月都要在一堆离职人员里找在职的人签字。
     */
    @Select("""
            <script>
            SELECT e.id           AS employeeId,
                   e.emp_no       AS empNo,
                   e.name         AS name,
                   e.position     AS position,
                   e.emp_level    AS empLevel,
                   d.name         AS deptName,
                   e.base_salary  AS baseSalary,
                   e.is_designer  AS isDesigner
              FROM employee e
              LEFT JOIN dept d ON d.id = e.dept_id
             WHERE e.status = 1
            <if test="keyword != null and keyword != ''">
               AND (e.name LIKE CONCAT('%', #{keyword}, '%')
                    OR e.emp_no LIKE CONCAT('%', #{keyword}, '%'))
            </if>
             ORDER BY e.emp_no ASC
            </script>
            """)
    List<SalaryEmployeeRow> selectActiveEmployees(@Param("keyword") String keyword);

    @Select("""
            SELECT e.id           AS employeeId,
                   e.emp_no       AS empNo,
                   e.name         AS name,
                   e.position     AS position,
                   e.emp_level    AS empLevel,
                   d.name         AS deptName,
                   e.base_salary  AS baseSalary,
                   e.is_designer  AS isDesigner
              FROM employee e
              LEFT JOIN dept d ON d.id = e.dept_id
             WHERE e.id = #{employeeId}
            """)
    SalaryEmployeeRow selectEmployee(@Param("employeeId") Long employeeId);

    /** 改底薪前先确认人还在职，避免把工资调到一个已离职的人头上 */
    @Select("SELECT status FROM employee WHERE id = #{employeeId}")
    Integer selectEmployeeStatus(@Param("employeeId") Long employeeId);
}
