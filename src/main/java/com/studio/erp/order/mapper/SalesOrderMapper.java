package com.studio.erp.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.studio.erp.order.dto.OrderListVO;
import com.studio.erp.order.dto.OrderQuery;
import com.studio.erp.order.entity.SalesOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 订单 Mapper。
 */
@Mapper
public interface SalesOrderMapper extends BaseMapper<SalesOrder> {

    /**
     * 订单分页列表。
     *
     * <p>【为什么要传两个"人员"参数，看起来很像重复】
     * scopeEmpId 是"数据范围"：非空表示只能看这个人参与的单（设计师视角）。
     * viewerEmpId 是"显示口径"：非空表示额外算一列"本单我能分多少"。
     * 两者取值恰好相同，但语义不同 —— 有一天"主管只看自己带的单"时，
     * scopeEmpId 会是主管的 id 而 viewerEmpId 为空，硬把两者合并成一个参数就改不动了。
     *
     * <p>【为什么设计师的过滤用 EXISTS 而不是 JOIN】
     * 一个订单有多条明细、每条明细又可能有多个人，JOIN 之后同一单会出现多行，
     * 分页的 total 变成"展开后行数"，出现"总共 10 条，翻第 2 页是空的"。
     * EXISTS 只判断存在性，不改变行数。
     */
    @Select("""
            <script>
            SELECT o.id,
                   o.order_no            AS orderNo,
                   o.client_name         AS clientName,
                   o.title,
                   o.category,
                   o.total_amount        AS totalAmount,
                   o.status,
                   o.payment_status      AS paymentStatus,
                   o.sign_date           AS signDate,
                   o.expect_deliver_date AS expectDeliverDate,
                   (SELECT GROUP_CONCAT(DISTINCT e.name ORDER BY a2.id)
                      FROM order_item oi2
                      JOIN order_item_assignee a2 ON a2.order_item_id = oi2.id
                      JOIN employee e ON e.id = a2.employee_id
                     WHERE oi2.order_id = o.id) AS designerNames,
                   (SELECT COALESCE(SUM(pa.amount), 0)
                      FROM payment_allocation pa
                      JOIN payment p ON p.id = pa.payment_id
                     WHERE pa.order_id = o.id AND p.status &lt;&gt; 3) AS paidAmount
                   <if test="viewerEmpId != null">
                   , (SELECT COALESCE(ROUND(SUM(oi3.amount * a3.ratio_bp / 10000), 2), 0)
                        FROM order_item oi3
                        JOIN order_item_assignee a3 ON a3.order_item_id = oi3.id
                       WHERE oi3.order_id = o.id
                         AND oi3.status = 1
                         AND a3.employee_id = #{viewerEmpId}) AS myAmount
                   </if>
              FROM sales_order o
             WHERE o.deleted = 0
            <if test="q.keyword != null and q.keyword != ''">
               AND (o.order_no LIKE CONCAT('%', #{q.keyword}, '%')
                    OR o.client_name LIKE CONCAT('%', #{q.keyword}, '%')
                    OR o.title LIKE CONCAT('%', #{q.keyword}, '%'))
            </if>
            <if test="q.status != null">
               AND o.status = #{q.status}
            </if>
            <if test="q.paymentStatus != null">
               AND o.payment_status = #{q.paymentStatus}
            </if>
            <if test="q.dateFrom != null">
               AND o.sign_date &gt;= #{q.dateFrom}
            </if>
            <if test="q.dateTo != null">
               AND o.sign_date &lt;= #{q.dateTo}
            </if>
            <if test="scopeEmpId != null">
               AND EXISTS (SELECT 1
                             FROM order_item oi4
                             JOIN order_item_assignee a4 ON a4.order_item_id = oi4.id
                            WHERE oi4.order_id = o.id
                              AND a4.employee_id = #{scopeEmpId})
            </if>
             ORDER BY o.id DESC
            </script>
            """)
    IPage<OrderListVO> selectOrderPage(IPage<OrderListVO> page,
                                       @Param("q") OrderQuery q,
                                       @Param("scopeEmpId") Long scopeEmpId,
                                       @Param("viewerEmpId") Long viewerEmpId);

    /**
     * 取本月最后一个订单号，并加行锁。
     *
     * <p>【为什么必须 FOR UPDATE】
     * "查最大值 +1"在并发下必然撞号：两个客服同时提单，都读到 003，都写 004，
     * 唯一索引让其中一个失败，用户看到的是一个看不懂的数据库报错。
     * FOR UPDATE 在唯一索引上取范围锁，把并发提单串行化 ——
     * 一个月十几单的场景，串行几毫秒完全无感。
     *
     * <p>唯一索引 uk_order_no 仍然保留：它是不依赖事务隔离级别的最后一道防线。
     */
    @Select("""
            SELECT order_no
              FROM sales_order
             WHERE order_no LIKE CONCAT(#{prefix}, '%')
             ORDER BY order_no DESC
             LIMIT 1
             FOR UPDATE
            """)
    String selectLastOrderNo(@Param("prefix") String prefix);

    /**
     * 定价确认：带状态条件的更新（CAS）。
     *
     * <p>【为什么用 WHERE status = 期望状态，而不是先查再改】
     * "先查状态、再更新"在并发下会让两个人同时通过检查、后一个覆盖前一个，
     * 导致第二次定价把第一次的明细冲掉而总额只保留一份 —— 账就乱了。
     * 把状态判断放进 WHERE，数据库的行锁保证只有一个能成功，另一个影响行数为 0。
     */
    @Update("""
            UPDATE sales_order
               SET total_amount     = #{totalAmount},
                   perf_base_amount = #{perfBaseAmount},
                   sign_date        = #{signDate},
                   supervisor_id    = #{supervisorId},
                   status           = #{targetStatus},
                   remark           = COALESCE(#{remark}, remark),
                   updated_at       = NOW()
             WHERE id = #{id}
               AND status = #{expectStatus}
               AND deleted = 0
            """)
    int markPriced(@Param("id") Long id,
                   @Param("totalAmount") BigDecimal totalAmount,
                   @Param("perfBaseAmount") BigDecimal perfBaseAmount,
                   @Param("signDate") LocalDate signDate,
                   @Param("supervisorId") Long supervisorId,
                   @Param("expectStatus") int expectStatus,
                   @Param("targetStatus") int targetStatus,
                   @Param("remark") String remark);

    /**
     * 只改执行状态，同样带 CAS。
     */
    @Update("""
            UPDATE sales_order
               SET status = #{targetStatus},
                   actual_deliver_date = CASE WHEN #{targetStatus} = 4
                                              THEN COALESCE(#{deliverDate}, CURDATE())
                                              ELSE actual_deliver_date END,
                   updated_at = NOW()
             WHERE id = #{id}
               AND status = #{expectStatus}
               AND deleted = 0
            """)
    int markStatus(@Param("id") Long id,
                   @Param("expectStatus") int expectStatus,
                   @Param("targetStatus") int targetStatus,
                   @Param("deliverDate") LocalDate deliverDate);

    /**
     * 收款状态由金额推导，不接收人工输入。
     *
     * <p>【为什么不让人手工选"已结清"】
     * 手工选必然和实际核销金额对不上，最后出现"状态显示已结清但还剩 3000 没收"。
     * 状态永远是派生值，这个 UPDATE 只被 refreshPaymentStatus 调用。
     */
    @Update("UPDATE sales_order SET payment_status = #{paymentStatus}, updated_at = NOW() WHERE id = #{id}")
    int updatePaymentStatus(@Param("id") Long id, @Param("paymentStatus") int paymentStatus);
}
