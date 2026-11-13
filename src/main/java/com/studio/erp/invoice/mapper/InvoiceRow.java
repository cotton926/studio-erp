package com.studio.erp.invoice.mapper;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 开票台账的一行（发票 + 订单上下文）。
 *
 * <p>用普通类 + setter 映射，理由同催收清单：record 按位置映射，
 * 列顺序一改就会把日期塞进金额，且只在有数据时报错。
 */
@Data
public class InvoiceRow {

    private Long id;
    private Long orderId;
    private String orderNo;
    private String clientName;
    private String title;
    private String taxNo;
    private String invoiceNo;
    private BigDecimal amount;
    private BigDecimal taxRate;
    private Integer invoiceType;
    private String content;
    private LocalDate issueDate;
    private Integer status;
    private Long applyBy;
    private String applyByName;
    private LocalDateTime appliedAt;
    private String voidReason;
    private String remark;
    private LocalDateTime createdAt;

    /** 订单总额 */
    private BigDecimal orderTotal;

    /** 该订单累计有效开票（不含作废），用于判断"还能开多少" */
    private BigDecimal orderInvoiced;

    /** 该订单累计到账，用于对照"已收 vs 已开" */
    private BigDecimal orderPaid;
}
