package com.studio.erp.report.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ContentStyle;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 开票台账导出行。
 *
 * <p>【为什么同时给含税、税额、不含税三列】
 * 财务报税要的是"不含税收入 + 税额"，和客户对账要的是"含税合计"。
 * 让财务在 Excel 里自己按 6% 反算，一列算错整张表都得重来。
 */
@Data
public class InvoiceExportRow {

    @ExcelProperty("发票号")
    private String invoiceNo;

    @ExcelProperty("订单号")
    private String orderNo;

    @ExcelProperty("客户")
    private String clientName;

    @ExcelProperty("发票抬头")
    private String title;

    @ExcelProperty("类型")
    private String invoiceTypeText;

    @ExcelProperty("含税金额")
    @ContentStyle(dataFormat = 4)
    private BigDecimal amount;

    @ExcelProperty("税率")
    private String taxRateText;

    @ExcelProperty("税额")
    @ContentStyle(dataFormat = 4)
    private BigDecimal taxAmount;

    @ExcelProperty("不含税金额")
    @ContentStyle(dataFormat = 4)
    private BigDecimal netAmount;

    @ExcelProperty("开票日")
    private LocalDate issueDate;

    @ExcelProperty("状态")
    private String statusText;

    @ExcelProperty("申请人")
    private String applyByName;

    @ExcelProperty("申请时间")
    private LocalDateTime appliedAt;

    @ExcelProperty("作废原因")
    private String voidReason;
}
