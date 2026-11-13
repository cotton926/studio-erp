package com.studio.erp.invoice.dto;

import java.math.BigDecimal;

/**
 * 开票台账概览。
 *
 * @param pendingCount     待开票张数（财务的待办）
 * @param pendingAmount    待开票金额
 * @param monthIssuedCount 本月已开张数
 * @param monthIssuedAmount 本月已开金额
 * @param totalIssuedAmount 累计已开金额（不含作废）
 */
public record InvoiceSummaryVO(
        int pendingCount,
        BigDecimal pendingAmount,
        int monthIssuedCount,
        BigDecimal monthIssuedAmount,
        BigDecimal totalIssuedAmount) {
}
