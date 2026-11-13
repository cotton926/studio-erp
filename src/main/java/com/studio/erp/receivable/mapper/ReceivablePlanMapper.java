package com.studio.erp.receivable.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studio.erp.receivable.entity.ReceivablePlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 收款计划 Mapper。
 *
 * <p>【催收清单的 WHERE 为什么要在这里而不是在 Service 里过滤】
 * "未收讫 + 应收日在窗口内"是催收页最核心的筛选，几十张单时内存过滤没问题，
 * 但几百张单、每张两期，一次全查再过滤会让页面打开变慢；
 * 更重要的是，内存过滤一旦条件写错（比如忘了排除已取消订单），
 * 催收员会去催一张已经废掉的单，客户体验极差。
 */
@Mapper
public interface ReceivablePlanMapper extends BaseMapper<ReceivablePlan> {

    @Select("""
            SELECT * FROM receivable_plan
             WHERE order_id = #{orderId}
             ORDER BY seq
            """)
    List<ReceivablePlan> selectByOrder(@Param("orderId") Long orderId);

    /**
     * 催收清单：未收讫的收款计划 + 订单 + 回款汇总。
     *
     * <p>scope 的三种取值对应页面上的三个页签：
     * WINDOW=本期窗口（月末最后 N 天内到期）、OVERDUE=已逾期、ALL=全部未结清。
     *
     * <p>回款汇总用 LEFT JOIN 子查询而不是相关子查询：相关子查询会对每一行再扫一次
     * payment_allocation，单量上来后是典型的 N+1 放大。
     */
    @Select("""
            <script>
            SELECT rp.id              AS planId,
                   rp.order_id        AS orderId,
                   o.order_no         AS orderNo,
                   o.client_name      AS clientName,
                   o.title            AS title,
                   rp.seq             AS seq,
                   rp.plan_name       AS planName,
                   rp.plan_amount     AS planAmount,
                   rp.received_amount AS receivedAmount,
                   rp.plan_date       AS planDate,
                   rp.status          AS planStatus,
                   rp.remind_count    AS remindCount,
                   rp.last_remind_at  AS lastRemindAt,
                   rp.promise_date    AS promiseDate,
                   rp.promise_amount  AS promiseAmount,
                   o.total_amount     AS orderTotal,
                   COALESCE(pay.paid, 0) AS orderPaid,
                   o.status           AS orderStatus,
                   o.payment_status   AS paymentStatus,
                   o.actual_deliver_date AS deliverDate,
                   e.name             AS csName,
                   e.phone            AS csPhone
              FROM receivable_plan rp
              JOIN sales_order o ON o.id = rp.order_id AND o.deleted = 0 AND o.status &lt;&gt; 9
              LEFT JOIN (SELECT pa.order_id, SUM(pa.amount) AS paid
                           FROM payment_allocation pa
                           JOIN payment p ON p.id = pa.payment_id AND p.status &lt;&gt; 3
                          GROUP BY pa.order_id) pay ON pay.order_id = o.id
              LEFT JOIN employee e ON e.id = o.cs_emp_id
             WHERE rp.status &lt;&gt; 3
            <if test="scope == 'WINDOW'">
               AND rp.plan_date BETWEEN #{winStart} AND #{winEnd}
            </if>
            <if test="scope == 'OVERDUE'">
               AND rp.plan_date &lt; #{today}
            </if>
            <if test="keyword != null and keyword != ''">
               AND (o.order_no LIKE CONCAT('%', #{keyword}, '%')
                    OR o.client_name LIKE CONCAT('%', #{keyword}, '%')
                    OR o.title LIKE CONCAT('%', #{keyword}, '%'))
            </if>
             ORDER BY rp.plan_date ASC, o.id ASC
            </script>
            """)
    List<CollectionRow> selectOpenPlans(@Param("scope") String scope,
                                        @Param("keyword") String keyword,
                                        @Param("today") LocalDate today,
                                        @Param("winStart") LocalDate winStart,
                                        @Param("winEnd") LocalDate winEnd);

    /**
     * 更新某期已收金额与状态。
     *
     * <p>【为什么要带 status 条件】recap: 已收讫的行不再被后续回款改写，
     * 否则一次补录回款可能把早已结清的期次改回"部分收"，历史就乱了。
     */
    @Update("""
            UPDATE receivable_plan
               SET received_amount = #{received},
                   status = #{status},
                   updated_at = NOW()
             WHERE id = #{id}
               AND (received_amount <> #{received} OR status <> #{status})
            """)
    int updateReceived(@Param("id") Long id,
                       @Param("received") BigDecimal received,
                       @Param("status") int status);

    /**
     * 记录一次催收：次数 +1，同时刷新客户承诺。
     *
     * <p>承诺值为 null 时保留原值（客户这次没给新承诺，不该把上次的承诺抹掉）。
     */
    @Update("""
            UPDATE receivable_plan
               SET remind_count   = remind_count + 1,
                   last_remind_at = NOW(),
                   promise_date   = COALESCE(#{promiseDate}, promise_date),
                   promise_amount = COALESCE(#{promiseAmount}, promise_amount),
                   updated_at     = NOW()
             WHERE id = #{id}
            """)
    int markReminded(@Param("id") Long id,
                     @Param("promiseDate") LocalDate promiseDate,
                     @Param("promiseAmount") BigDecimal promiseAmount);

    /**
     * 交付后把尾款应收日改到交付月月末。
     *
     * <p>只动未收讫的尾款：已经收完的期次改日期没有意义，反而会在历史里制造噪音。
     */
    @Update("""
            UPDATE receivable_plan
               SET plan_date = #{planDate},
                   updated_at = NOW()
             WHERE order_id = #{orderId}
               AND seq = 2
               AND status <> 3
               AND (plan_date IS NULL OR plan_date <> #{planDate})
            """)
    int moveTailDate(@Param("orderId") Long orderId, @Param("planDate") LocalDate planDate);

    /** 本月实际到账（作废回款不计），用于"本月催回来多少"这个对照数字 */
    @Select("""
            SELECT COALESCE(SUM(p.amount), 0)
              FROM payment p
              JOIN payment_allocation pa ON pa.payment_id = p.id
             WHERE p.status <> 3
               AND p.pay_date BETWEEN #{from} AND #{to}
            """)
    BigDecimal sumPaidBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 本月催收次数，用于首页"本月已催 N 次" */
    @Select("""
            SELECT COUNT(*) FROM collection_log
             WHERE created_at >= #{from} AND created_at < #{to}
            """)
    int countCollectBetween(@Param("from") java.time.LocalDateTime from,
                            @Param("to") java.time.LocalDateTime to);

    /** 需要补建计划的历史订单（已定价但还没有任何计划行） */
    @Select("""
            SELECT o.id
              FROM sales_order o
             WHERE o.deleted = 0
               AND o.total_amount > 0
               AND o.status IN (2, 3, 4)
               AND NOT EXISTS (SELECT 1 FROM receivable_plan p WHERE p.order_id = o.id)
            """)
    List<Long> selectOrdersWithoutPlan();
}
