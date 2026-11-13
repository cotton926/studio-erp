package com.studio.erp.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studio.erp.order.entity.OrderItemAssignee;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 明细分成人 Mapper。
 */
@Mapper
public interface OrderItemAssigneeMapper extends BaseMapper<OrderItemAssignee> {

    @Select("""
            SELECT a.order_item_id  AS orderItemId,
                   a.employee_id    AS employeeId,
                   e.name           AS employeeName,
                   a.assignee_role  AS assigneeRole,
                   a.ratio_bp       AS ratioBp
              FROM order_item_assignee a
              JOIN order_item oi ON oi.id = a.order_item_id
              LEFT JOIN employee e ON e.id = a.employee_id
             WHERE oi.order_id = #{orderId}
             ORDER BY a.id
            """)
    List<AssigneeRow> selectByOrder(@Param("orderId") Long orderId);

    /**
     * 删除某单的全部分成人。
     *
     * <p>【为什么用 JOIN 删而不是先查明细 id 再 IN】
     * 一条 SQL 完成，不会出现"查出来的 id 列表和删除时的数据不一致"的窗口。
     * 数据量极小，性能不是这里的考量，一致性才是。
     */
    @Delete("""
            DELETE a
              FROM order_item_assignee a
              JOIN order_item oi ON oi.id = a.order_item_id
             WHERE oi.order_id = #{orderId}
            """)
    int deleteByOrder(@Param("orderId") Long orderId);
}
