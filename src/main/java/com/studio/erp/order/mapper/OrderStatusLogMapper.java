package com.studio.erp.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studio.erp.order.entity.OrderStatusLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderStatusLogMapper extends BaseMapper<OrderStatusLog> {

    /** 时间线按正序返回：前端从上往下读就是事情发生的顺序 */
    @Select("""
            SELECT id, order_id, from_status, to_status, operator_id, remark, created_at
              FROM order_status_log
             WHERE order_id = #{orderId}
             ORDER BY id ASC
            """)
    List<OrderStatusLog> selectByOrder(@Param("orderId") Long orderId);

    /**
     * 带操作人姓名的时间线。
     *
     * <p>LEFT JOIN 而不是 INNER：operator_id 可能是 0（系统自动），
     * 用 INNER JOIN 会把这类记录整条吞掉，时间线出现断档。
     */
    @Select("""
            SELECT l.id, l.from_status, l.to_status, l.operator_id,
                   e.name AS operator_name, l.remark, l.created_at
              FROM order_status_log l
              LEFT JOIN employee e ON e.id = l.operator_id
             WHERE l.order_id = #{orderId}
             ORDER BY l.id ASC
            """)
    List<StatusLogRow> selectTimeline(@Param("orderId") Long orderId);
}
