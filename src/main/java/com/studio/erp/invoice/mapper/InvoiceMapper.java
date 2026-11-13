package com.studio.erp.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studio.erp.invoice.entity.Invoice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 开票 Mapper。
 */
@Mapper
public interface InvoiceMapper extends BaseMapper<Invoice> {

    /**
     * 开票台账。
     *
     * <p>订单侧的三个金额改为【按订单分组的派生表 JOIN】而不是每行一个相关子查询：
     * 相关子查询是"每行跑一次聚合"，1000 张票 = 2000 次聚合；
     * 派生表是"整表各聚合一遍再 JOIN"，无论多少行都只有 2 次聚合。
     * <p>分页用显式 LIMIT 而不是传 Page 对象：count 走独立的 {@link #countRows}，
     * 不需要把派生表 JOIN 也算进 count。
     */
    @Select("""
            <script>
            SELECT i.id,
                   i.order_id    AS orderId,
                   o.order_no    AS orderNo,
                   o.client_name AS clientName,
                   i.title,
                   i.tax_no      AS taxNo,
                   i.invoice_no  AS invoiceNo,
                   i.amount,
                   i.tax_rate    AS taxRate,
                   i.invoice_type AS invoiceType,
                   i.content,
                   i.issue_date  AS issueDate,
                   i.status,
                   i.apply_by    AS applyBy,
                   e.name        AS applyByName,
                   i.applied_at  AS appliedAt,
                   i.void_reason AS voidReason,
                   i.remark,
                   i.created_at  AS createdAt,
                   o.total_amount AS orderTotal,
                   COALESCE(inv.invoiced, 0) AS orderInvoiced,
                   COALESCE(pay.paid, 0)     AS orderPaid
              FROM invoice i
              LEFT JOIN sales_order o ON o.id = i.order_id
              LEFT JOIN employee e ON e.id = i.apply_by
              LEFT JOIN (
                   SELECT i2.order_id, SUM(i2.amount) AS invoiced
                     FROM invoice i2
                    WHERE i2.status &lt;&gt; 3
                    <if test="orderId != null"> AND i2.order_id = #{orderId}</if>
                    GROUP BY i2.order_id
               ) inv ON inv.order_id = i.order_id
              LEFT JOIN (
                   SELECT pa.order_id, SUM(pa.amount) AS paid
                     FROM payment_allocation pa
                     JOIN payment p ON p.id = pa.payment_id AND p.status &lt;&gt; 3
                    <if test="orderId != null"> WHERE pa.order_id = #{orderId}</if>
                    GROUP BY pa.order_id
               ) pay ON pay.order_id = i.order_id
             WHERE 1 = 1
            <if test="status != null">
               AND i.status = #{status}
            </if>
            <if test="orderId != null">
               AND i.order_id = #{orderId}
            </if>
            <if test="from != null">
               AND i.applied_at &gt;= #{from}
            </if>
            <if test="to != null">
               AND i.applied_at &lt; #{to}
            </if>
            <if test="keyword != null and keyword != ''">
               AND (i.invoice_no LIKE CONCAT('%', #{keyword}, '%')
                    OR o.order_no LIKE CONCAT('%', #{keyword}, '%')
                    OR o.client_name LIKE CONCAT('%', #{keyword}, '%')
                    OR i.title LIKE CONCAT('%', #{keyword}, '%'))
            </if>
             ORDER BY i.id DESC
            <if test="limit != null">
               LIMIT #{offset}, #{limit}
            </if>
            </script>
            """)
    List<InvoiceRow> selectRows(@Param("limit") Integer limit,
                                @Param("offset") long offset,
                                @Param("status") Integer status,
                                @Param("orderId") Long orderId,
                                @Param("from") java.time.LocalDateTime from,
                                @Param("to") java.time.LocalDateTime to,
                                @Param("keyword") String keyword);

    /**
     * 台账总数。
     *
     * <p>【为什么不复用 selectRows 让框架生成 count】
     * 框架生成的 count 会原样保留派生表 JOIN —— 而 count 只看行数，
     * 根本不需要 orderInvoiced/orderPaid 两列。单独写 count 能省掉两次全表聚合。
     */
    @Select("""
            <script>
            SELECT COUNT(*)
              FROM invoice i
              LEFT JOIN sales_order o ON o.id = i.order_id
             WHERE 1 = 1
            <if test="status != null">
               AND i.status = #{status}
            </if>
            <if test="orderId != null">
               AND i.order_id = #{orderId}
            </if>
            <if test="from != null">
               AND i.applied_at &gt;= #{from}
            </if>
            <if test="to != null">
               AND i.applied_at &lt; #{to}
            </if>
            <if test="keyword != null and keyword != ''">
               AND (i.invoice_no LIKE CONCAT('%', #{keyword}, '%')
                    OR o.order_no LIKE CONCAT('%', #{keyword}, '%')
                    OR o.client_name LIKE CONCAT('%', #{keyword}, '%')
                    OR i.title LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            </script>
            """)
    long countRows(@Param("status") Integer status,
                   @Param("orderId") Long orderId,
                   @Param("from") java.time.LocalDateTime from,
                   @Param("to") java.time.LocalDateTime to,
                   @Param("keyword") String keyword);

    /**
     * 某订单已开票金额合计（不含作废）。
     *
     * <p>作废票必须排除，否则作废一张后会永远开不出第二张 —— 可开余额被一张废票吃掉了。
     */
    @Select("""
            SELECT COALESCE(SUM(amount), 0) FROM invoice
             WHERE order_id = #{orderId} AND status <> 3
            """)
    BigDecimal sumValidByOrder(@Param("orderId") Long orderId);

    /** 发票号是否已被占用（作废的票号仍然占用：同一票号不可能重开） */
    @Select("""
            SELECT COUNT(*) FROM invoice
             WHERE invoice_no = #{invoiceNo}
               AND (#{excludeId} IS NULL OR id <> #{excludeId})
            """)
    int countByInvoiceNo(@Param("invoiceNo") String invoiceNo,
                         @Param("excludeId") Long excludeId);

    /** 按状态统计张数与金额 */
    @Select("""
            SELECT COUNT(*) AS cnt, COALESCE(SUM(amount), 0) AS amt
              FROM invoice
             WHERE status = #{status}
            """)
    InvoiceSum sumByStatus(@Param("status") int status);

    /** 某月已开票金额（按开票日期） */
    @Select("""
            SELECT COUNT(*) AS cnt, COALESCE(SUM(amount), 0) AS amt
              FROM invoice
             WHERE status = 2
               AND issue_date BETWEEN #{from} AND #{to}
            """)
    InvoiceSum sumIssuedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 统计结果。
     *
     * <p>用普通类而不是 record：MyBatis 对单行聚合结果也会走构造映射，
     * 两个字段位置颠倒时 amount 会被当作 count 塞进去，编译器完全看不出来。
     */
    @lombok.Data
    class InvoiceSum {
        private Integer cnt;
        private BigDecimal amt;
    }
}
