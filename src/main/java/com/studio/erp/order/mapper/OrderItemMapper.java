package com.studio.erp.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studio.erp.order.entity.OrderItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单明细 Mapper。
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

    @Select("SELECT * FROM order_item WHERE order_id = #{orderId} AND status = 1 ORDER BY line_no")
    List<OrderItem> selectByOrder(@Param("orderId") Long orderId);

    /**
     * 物理删除某单的全部明细（重新定价时使用）。
     *
     * <p>【为什么敢物理删除，而不是标记作废】
     * 只有"一分钱都没收"的订单才允许重新定价（服务层已拦截），
     * 此时该单不存在任何业绩流水，删明细不会留下孤儿数据。
     * 一旦收了钱，CONFIRM 流水就指向了这些行 id，删掉就再也追溯不了，
     * 所以那条路被直接堵死了。
     */
    @Delete("DELETE FROM order_item WHERE order_id = #{orderId}")
    int deleteByOrder(@Param("orderId") Long orderId);
}
