package com.studio.erp.performance.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 「我的业绩」页面专用的只读查询。
 *
 * <p>【为什么单独一个 Mapper，不塞进 PerfQueryMapper】
 * PerfQueryMapper 服务的是"算业绩"这条写链路（核销 → 分摊 → 落流水），
 * 字段口径改一次就可能算错钱。这里服务的是"看业绩"这条读链路，
 * 会随页面调整而变动。混在一起后，某次"页面多加一列"的改动
 * 就可能悄悄影响分摊逻辑 —— 而这种错要到发工资才会暴露。
 *
 * <p>【为什么未收业绩按订单全量算，而不是按本月签单算】
 * 客户 9 月签的单、11 月才回款，这笔"未收"在 9 月就该被看见，
 * 只看本月签单会让员工以为"这个月没欠款"。所以口径是：
 * 我参与过、还没结清的所有订单。
 */
@Mapper
public interface PerfSummaryMapper {

    /**
     * 我参与的订单（订单粒度，含我的分成与整单收款进度）。
     *
     * <p>【为什么用 GROUP BY 订单而不是明细行】
     * 页面上一行就是一张单，员工关心"这单我还差多少"，不是"第 2 行还差多少"。
     * 明细行粒度留给订单详情页。
     *
     * <p>【myItemAmount 为什么要单独返回】
     * 我的分成比例 = mySignAmount / myItemAmount（我参与那几行的金额合计），
     * 只给一个 mySignAmount 算不出比例。用它除一下，页面上就能显示"70%"。
     *
     * <p>【orderPaid 用相关子查询而不是再 JOIN 一次 payment_allocation】
     * 同一订单可能有多笔回款、一笔回款又可能核销多个订单，JOIN 进来会把
     * oi.amount 复制成多行，SUM 直接翻倍。子查询只取一个标量，不会污染分组。
     */
    @Select("""
            <script>
            SELECT o.id            AS orderId,
                   o.order_no      AS orderNo,
                   o.client_name   AS clientName,
                   o.title         AS title,
                   o.sign_date     AS signDate,
                   o.status        AS orderStatus,
                   o.payment_status AS paymentStatus,
                   o.total_amount  AS orderTotal,
                   GROUP_CONCAT(DISTINCT oi.item_name ORDER BY oi.line_no SEPARATOR ' / ') AS itemNames,
                   ROUND(SUM(oi.amount), 2) AS myItemAmount,
                   ROUND(SUM(oi.amount * a.ratio_bp / 10000), 2) AS mySignAmount,
                   ROUND(COALESCE((SELECT SUM(pa.amount)
                                     FROM payment_allocation pa
                                     JOIN payment p ON p.id = pa.payment_id
                                    WHERE pa.order_id = o.id
                                      AND p.status &lt;&gt; 3), 0), 2) AS orderPaid
              FROM sales_order o
              JOIN order_item oi ON oi.order_id = o.id
              JOIN order_item_assignee a ON a.order_item_id = oi.id
             WHERE o.deleted = 0
               AND o.status IN (2, 3, 4)
               AND oi.status = 1
               AND oi.item_type = 1
               AND a.employee_id = #{employeeId}
            <if test="period != null and period != ''">
               AND DATE_FORMAT(o.sign_date, '%Y-%m') = #{period}
            </if>
             GROUP BY o.id, o.order_no, o.client_name, o.title, o.sign_date,
                      o.status, o.payment_status, o.total_amount
             ORDER BY o.sign_date DESC, o.id DESC
            </script>
            """)
    List<MyOrderRow> selectMyOrders(@Param("employeeId") Long employeeId,
                                    @Param("period") String period);

    /**
     * 近 N 个月的业绩（按 period 分组）。
     *
     * @param types 计入业绩的流水类型码，由枚举推导后传入，不在 SQL 里写死数字
     */
    @Select("""
            <script>
            SELECT period AS period, SUM(amount) AS amount
              FROM performance_ledger
             WHERE employee_id = #{employeeId}
               AND period &gt;= #{fromPeriod}
               AND ledger_type IN
               <foreach collection="types" item="t" open="(" separator="," close=")">#{t}</foreach>
             GROUP BY period
             ORDER BY period
            </script>
            """)
    List<PeriodAmountRow> selectMonthlyTrend(@Param("employeeId") Long employeeId,
                                             @Param("fromPeriod") String fromPeriod,
                                             @Param("types") List<Integer> types);

    /** 某年累计业绩；用于"本年累计"KPI */
    @Select("""
            <script>
            SELECT COALESCE(SUM(amount), 0)
              FROM performance_ledger
             WHERE employee_id = #{employeeId}
               AND period LIKE CONCAT(#{year}, '%')
               AND ledger_type IN
               <foreach collection="types" item="t" open="(" separator="," close=")">#{t}</foreach>
            </script>
            """)
    BigDecimal sumYearPerformance(@Param("employeeId") Long employeeId,
                                  @Param("year") String year,
                                  @Param("types") List<Integer> types);

    /** 我参与的订单行（扁平），用于订单明细表；按签单月过滤 */
    record MyOrderRow(
            Long orderId,
            String orderNo,
            String clientName,
            String title,
            LocalDate signDate,
            Integer orderStatus,
            Integer paymentStatus,
            BigDecimal orderTotal,
            String itemNames,
            BigDecimal myItemAmount,
            BigDecimal mySignAmount,
            BigDecimal orderPaid) {
    }

    /** 月度业绩点 */
    record PeriodAmountRow(String period, BigDecimal amount) {
    }
}
