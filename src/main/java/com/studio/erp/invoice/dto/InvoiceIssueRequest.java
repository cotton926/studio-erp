package com.studio.erp.invoice.dto;

import java.time.LocalDate;

/**
 * 财务把票开出来之后的回填。
 *
 * @param invoiceNo 发票号码，必填 —— 没有票号就无法和税控系统对账
 * @param issueDate 开票日期，为空取今天
 */
public record InvoiceIssueRequest(String invoiceNo, LocalDate issueDate) {
}
