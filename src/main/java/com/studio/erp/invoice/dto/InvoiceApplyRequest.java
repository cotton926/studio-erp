package com.studio.erp.invoice.dto;

import java.math.BigDecimal;

/**
 * 申请开票。
 *
 * <p>【金额由申请人填，不由系统自动带"订单总额"】
 * 因为存在分两次开票（先开一半，验收后再开一半）和"扣掉赠品金额"的情况。
 * 系统只做"累计不超过订单总额"的校验，具体开多少由业务决定。
 *
 * @param orderId     订单ID
 * @param amount      本次开票金额（含税）
 * @param taxRatePercent 税率百分数，如 6 表示 6%；为空默认 6
 * @param invoiceType 1=普通 2=专用
 * @param title       发票抬头；为空取订单客户名
 * @param taxNo       纳税人识别号
 * @param content     开票内容，如"设计服务费"
 */
public record InvoiceApplyRequest(
        Long orderId,
        BigDecimal amount,
        BigDecimal taxRatePercent,
        Integer invoiceType,
        String title,
        String taxNo,
        String content,
        String remark) {
}
