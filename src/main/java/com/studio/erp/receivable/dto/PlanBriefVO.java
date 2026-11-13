package com.studio.erp.receivable.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订单详情里的收款计划摘要。
 *
 * <p>字段比催收清单少：这里只用来说明"这一单的钱怎么分期、收到哪一步"，
 * 不需要客服电话、逾期天数这些催收动作才需要的上下文。
 */
public record PlanBriefVO(
        Long id,
        Integer seq,
        String planName,
        BigDecimal planAmount,
        BigDecimal receivedAmount,
        BigDecimal unpaidAmount,
        LocalDate planDate,
        Integer status,
        String statusText,
        Integer remindCount,
        LocalDateTime lastRemindAt,
        LocalDate promiseDate) {
}
