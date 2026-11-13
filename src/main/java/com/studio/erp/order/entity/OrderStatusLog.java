package com.studio.erp.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单状态流转日志。
 *
 * <p>【为什么状态不能只留一个当前值】
 * sales_order.status 只能回答"现在是什么状态"，回答不了"这单为什么卡在待定价三天"、
 * "谁把已交付改回了制作中"。业务上真正吵架的时刻都在这些地方，
 * 而状态字段每覆盖一次，上一段历史就永久消失了。
 */
@Data
@TableName("order_status_log")
public class OrderStatusLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    /** 变更前状态，首条记录为 null */
    private Integer fromStatus;

    private Integer toStatus;

    private Long operatorId;

    private String remark;

    private LocalDateTime createdAt;
}
