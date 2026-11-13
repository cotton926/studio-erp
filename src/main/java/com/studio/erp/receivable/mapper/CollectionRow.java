package com.studio.erp.receivable.mapper;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 催收清单的一行（收款计划 + 订单 + 客户 + 回款汇总）。
 *
 * <p>【为什么是普通类而不是 record】
 * MyBatis 对 record 走"按构造参数位置映射"，SQL 里列的顺序一改就会把
 * 日期塞进字符串、把姓名塞进金额，而且只在有数据时报错（空结果集不复现）。
 * 这里字段多、以后还会加，用 setter 映射（按列名匹配）才安全。
 * 同样的坑在返工统计上踩过一次，不重复。
 */
@Data
public class CollectionRow {

    private Long planId;
    private Long orderId;
    private String orderNo;
    private String clientName;
    private String title;

    private Integer seq;
    private String planName;
    private BigDecimal planAmount;
    private BigDecimal receivedAmount;
    private LocalDate planDate;
    private Integer planStatus;

    private Integer remindCount;
    private LocalDateTime lastRemindAt;
    private LocalDate promiseDate;
    private BigDecimal promiseAmount;

    /** 订单级金额，用于页面提示"整单还差多少"，与计划级未收不是同一个口径 */
    private BigDecimal orderTotal;
    private BigDecimal orderPaid;

    private Integer orderStatus;
    private Integer paymentStatus;
    private LocalDate deliverDate;

    private String csName;
    private String csPhone;
}
