package com.studio.erp.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 明细行的分成人。
 *
 * <p>【比例为什么用整数万分比】
 * 10000 = 100%，7000 = 70%。用小数（0.7）存有两类必然发生的问题：
 * 一是 0.1+0.2+0.7 在浮点下不等于 1，校验"合计 100%"永远差一点点；
 * 二是"一单 3 人各 33.33%"这种场景，三份相加 99.99%，要么放宽校验（比例可超 100%），
 * 要么逼主管手工凑最后一份。整数相加能被精确判定，这个问题就不存在了。
 *
 * <p>【真实业务分布】
 * 十单里大约两单需要两人以上协作，所以绝大多数明细行只有一条 ratioBp=10000 的记录。
 * 界面默认只显示一个设计师选择框，点"添加协作人"才展开比例输入。
 */
@Data
@TableName("order_item_assignee")
public class OrderItemAssignee {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderItemId;

    private Long employeeId;

    /** 1=主设 2=协作 */
    private Integer assigneeRole;

    /** 分成比例，万分比 */
    private Integer ratioBp;

    private LocalDateTime createdAt;
}
