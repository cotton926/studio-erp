package com.studio.erp.report.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ContentStyle;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 订单台账导出行。
 *
 * <p>【为什么带"未收"这一列】
 * 导出的订单台账最常被用来对账。只给总额和已收，接收方还要自己减一次；
 * 而减法在 Excel 里被手工改过一次就会错，且没人会重新核对。系统算好直接给。
 */
@Data
public class OrderExportRow {

    @ExcelProperty("订单号")
    private String orderNo;

    @ExcelProperty("客户")
    private String clientName;

    @ExcelProperty("项目")
    private String title;

    @ExcelProperty("品类")
    private String category;

    @ExcelProperty("签单日")
    private LocalDate signDate;

    @ExcelProperty("执行状态")
    private String statusText;

    @ExcelProperty("收款状态")
    private String paymentStatusText;

    @ExcelProperty("订单总额")
    @ContentStyle(dataFormat = 4)
    private BigDecimal totalAmount;

    @ExcelProperty("已收")
    @ContentStyle(dataFormat = 4)
    private BigDecimal paidAmount;

    @ExcelProperty("未收")
    @ContentStyle(dataFormat = 4)
    private BigDecimal unpaidAmount;

    @ExcelProperty("设计师")
    private String designerNames;

    @ExcelProperty("预计交付")
    private LocalDate expectDeliverDate;
}
