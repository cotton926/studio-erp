package com.studio.erp.report.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ContentStyle;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 我的业绩明细导出行。
 *
 * <p>【为什么设计师也能导出这一张】
 * 员工对自己工资有疑问时，最有力的材料是"我参与的单、我的分成、已收未收"的明细。
 * 让他能导出，比让他截图微信聊天记录找主管对账要高效得多。
 * 但只包含他自己参与的单 —— 导出的数据范围与页面完全一致，不多给一行。
 */
@Data
public class PerformanceExportRow {

    @ExcelProperty("订单号")
    private String orderNo;

    @ExcelProperty("客户")
    private String clientName;

    @ExcelProperty("项目")
    private String title;

    @ExcelProperty("签单日")
    private LocalDate signDate;

    @ExcelProperty("执行状态")
    private String statusText;

    @ExcelProperty("收款状态")
    private String paymentStatusText;

    @ExcelProperty("我的分成")
    private String ratioText;

    @ExcelProperty("我的签单额")
    @ContentStyle(dataFormat = 4)
    private BigDecimal mySignAmount;

    @ExcelProperty("我的已收")
    @ContentStyle(dataFormat = 4)
    private BigDecimal myPaidAmount;

    @ExcelProperty("我的未收")
    @ContentStyle(dataFormat = 4)
    private BigDecimal myUnpaid;
}
