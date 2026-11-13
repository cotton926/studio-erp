package com.studio.erp.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 回款核销记录：这笔钱是核销到哪张订单上的。
 *
 * <p>【为什么必须有这张表，而不是在 payment 上直接挂 order_id】
 * 月结客户的 3 万块可能同时清掉 5 张单，一张单的尾款也分两次到账。
 * 一对一撑不住这两种场景，期末对账时只能靠人工摊，而人工摊的结果没人能复核。
 *
 * <p>【这条记录的 id 就是业绩的幂等键来源】
 * 业绩流水的幂等键是 "CONFIRM:{核销ID}:{员工ID}:{明细行ID}"，
 * 所以同一条核销记录重复处理也不会重复计业绩 —— 这是系统里最重要的防重设计。
 */
@Data
@TableName("payment_allocation")
public class PaymentAllocation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long paymentId;

    private Long orderId;

    /** 本期整单核销，恒为 null（按明细行金额占比自动分摊） */
    private Long orderItemId;

    /** 收款计划节点：本期不做收款计划，恒为 null */
    private Long planId;

    private BigDecimal amount;

    private LocalDateTime createdAt;

    private Long createdBy;
}
