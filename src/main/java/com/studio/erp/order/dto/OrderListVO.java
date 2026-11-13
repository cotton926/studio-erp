package com.studio.erp.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 订单列表行。
 *
 * <p>【为什么金额字段允许为 null】
 * 设计师看不到订单总额（需求明确"设计师仅能看到自己的那部分"）。
 * 服务端对低权限用户把 totalAmount 置空、改为返回 myAmount（本单我能分多少）。
 * 前端拿到 null 就不渲染这一列 —— 注意这个隐藏动作必须在服务端做，
 * 前端隐藏只是"看不见"，抓包一样能拿到。
 */
@Data
public class OrderListVO {

    private Long id;

    private String orderNo;

    private String clientName;

    private String title;

    private String category;

    /** 订单总额；低权限用户返回 null */
    private BigDecimal totalAmount;

    /** 本人在这张单上的分成金额；高权限用户返回 null（他们看 totalAmount） */
    private BigDecimal myAmount;

    private Integer status;

    private String statusText;

    private Integer paymentStatus;

    private String paymentStatusText;

    private LocalDate signDate;

    private LocalDate expectDeliverDate;

    /** 参与的设计师姓名，逗号分隔；用于列表上直接看出"这单是谁做的" */
    private String designerNames;

    /** 已收金额（仅财务与主管需要，列表里显示"还差多少没收"） */
    private BigDecimal paidAmount;
}
