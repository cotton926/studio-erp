package com.studio.erp.receivable.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 一条催收记录的展示对象。
 *
 * <p>同时返回码值和中文：前端下拉/标签用码值匹配样式，
 * 列表直接显示中文，不用在前端再维护一份映射（映射漏一项就会显示空白）。
 */
public record CollectLogVO(
        Long id,
        Long orderId,
        Long planId,
        Integer action,
        String actionText,
        Integer result,
        String resultText,
        LocalDate promiseDate,
        BigDecimal promiseAmount,
        String remark,
        String operatorName,
        LocalDateTime createdAt) {
}
