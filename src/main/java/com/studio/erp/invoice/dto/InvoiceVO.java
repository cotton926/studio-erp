package com.studio.erp.invoice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 开票台账的一行。
 *
 * <p>【税率同时给"百分数"和"税额"】
 * 财务核对发票时看的是税率 6%、税额 566.04；数据库里存的是 0.0600。
 * 让前端每次乘 100 再算税额，一旦哪算错就是发票金额对不上，
 * 所以换算和税额计算都放在服务端，只返回可直接展示的数字。
 *
 * @param taxRatePercent 税率百分数，如 6.00
 * @param taxAmount      税额（价内税）= 含税金额 - 含税金额/(1+税率)
 * @param netAmount      不含税金额
 * @param orderTotal     订单总额
 * @param orderInvoiced  该单累计有效开票（不含作废）
 * @param orderPaid      该单累计到账，用于对照"已收 vs 已开"
 */
public record InvoiceVO(
        Long id,
        Long orderId,
        String orderNo,
        String clientName,
        String title,
        String taxNo,
        String invoiceNo,
        BigDecimal amount,
        BigDecimal taxRatePercent,
        BigDecimal taxAmount,
        BigDecimal netAmount,
        Integer invoiceType,
        String invoiceTypeText,
        String content,
        LocalDate issueDate,
        Integer status,
        String statusText,
        Long applyBy,
        String applyByName,
        LocalDateTime appliedAt,
        String voidReason,
        String remark,
        LocalDateTime createdAt,
        BigDecimal orderTotal,
        BigDecimal orderInvoiced,
        BigDecimal orderPaid) {
}
