package com.studio.erp.performance.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * 业绩计算所需的只读查询。
 *
 * <p>【为什么单独建一个 QueryMapper 而不是塞进 OrderItemMapper】
 * 这些 SQL 只服务于业绩计算，字段口径必须稳定。和订单管理用的查询混在一起，
 * 一旦有人为了页面展示去改 SQL（比如多加个筛选条件），业绩就算错了，
 * 而且这种错很隐蔽——页面上看不出来，只有对账时才发现少了几百块。
 */
@Mapper
public interface PerfQueryMapper {

    /**
     * 查询订单下所有明细行及其分成人（扁平结构）。
     *
     * <p>【为什么用 LEFT JOIN】
     * 还没派单的行也要查出来（employeeId 为 null），这样上层能明确报出
     * "第 2 行没有指定设计师"，而不是静默地少算一笔业绩。
     *
     * <p>【为什么过滤 oi.status = 1】
     * 被作废的明细行不参与业绩，但数据要保留可追溯。
     */
    @Select("""
            SELECT oi.id            AS itemId,
                   oi.item_name     AS itemName,
                   oi.amount        AS amount,
                   oi.item_type     AS itemType,
                   a.employee_id    AS employeeId,
                   a.ratio_bp       AS ratioBp
              FROM order_item oi
              LEFT JOIN order_item_assignee a ON a.order_item_id = oi.id
             WHERE oi.order_id = #{orderId}
               AND oi.status = 1
             ORDER BY oi.line_no, a.id
            """)
    List<PerfItemRow> selectItemRows(@Param("orderId") Long orderId);

    /** 订单总额（用于校验核销是否超额） */
    @Select("SELECT total_amount FROM sales_order WHERE id = #{orderId}")
    BigDecimal selectOrderTotal(@Param("orderId") Long orderId);

    /**
     * 订单已核销金额。
     *
     * <p>【excludeId 为什么必须存在】
     * 核销流程是"先插入核销记录拿 id（业绩幂等键要用），再校验是否超收"。
     * 此时这次核销已经在表里了，如果不排掉它，就会把"本次金额"算两遍：
     * 一张 3 万的单收第一笔 2 万，会被算成"已核销 2 万 + 本次 2 万 = 4 万，超收"，
     * 结果是所有正常回款的第一笔都被拒 —— 而且报错信息看起来还挺有道理，极难察觉。
     *
     * <p>传 null 表示"要全量合计"（查详情、刷新收款状态时用）。
     */
    @Select("""
            <script>
            SELECT COALESCE(SUM(pa.amount), 0)
              FROM payment_allocation pa
              JOIN payment p ON p.id = pa.payment_id
             WHERE pa.order_id = #{orderId}
               AND p.status &lt;&gt; 3
            <if test="excludeId != null">
               AND pa.id &lt;&gt; #{excludeId}
            </if>
            </script>
            """)
    BigDecimal selectAllocatedAmount(@Param("orderId") Long orderId,
                                     @Param("excludeId") Long excludeId);
}
