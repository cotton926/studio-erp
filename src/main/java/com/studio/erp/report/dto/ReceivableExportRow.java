package com.studio.erp.report.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ContentStyle;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 催收清单导出行。
 *
 * <p>【客服电话必须带上】
 * 催收清单导出的真实用途有两种：① 主管在月末催收窗口前分派任务；
 * ② 客服打印出来逐个打电话。后者要求表格里有电话号码 —— 没有这一列，
 * 导出就得再回系统里点开每张单，导出等于白做。
 */
@Data
public class ReceivableExportRow {

    @ExcelProperty("订单号")
    private String orderNo;

    @ExcelProperty("客户")
    private String clientName;

    @ExcelProperty("项目")
    private String title;

    @ExcelProperty("期次")
    private String planName;

    @ExcelProperty("应收金额")
    @ContentStyle(dataFormat = 4)
    private BigDecimal planAmount;

    @ExcelProperty("已收")
    @ContentStyle(dataFormat = 4)
    private BigDecimal receivedAmount;

    @ExcelProperty("未收")
    @ContentStyle(dataFormat = 4)
    private BigDecimal unpaidAmount;

    @ExcelProperty("应收日")
    private LocalDate planDate;

    @ExcelProperty("逾期天数")
    private Long overdueDays;

    @ExcelProperty("催收次数")
    private Integer remindCount;

    @ExcelProperty("最近催收")
    private LocalDateTime lastRemindAt;

    @ExcelProperty("客户承诺日")
    private LocalDate promiseDate;

    @ExcelProperty("客服")
    private String csName;

    @ExcelProperty("客服电话")
    private String csPhone;
}
