package com.studio.erp.invoice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 开票记录。
 *
 * <p>【为什么开票和回款是两张表、不互相绑定】
 * 实务里两种顺序都存在：大客户要求"先票后款"（拿着发票走内部付款流程），
 * 小客户则是"先款后票"。强行绑在回款上，先票后款的单就永远开不出票。
 * 这里只做"金额不超过订单总额"的约束，把开票与收款解耦。
 *
 * <p>【为什么状态是 1待开 / 2已开 / 3已作废，而不是"开票"一个动作】
 * 发票是纸质/电子的实物凭证，存在"申请了但财务还没开出来"的窗口期。
 * 没有待开状态，客服只能口头催财务，没人知道有几张票压着。
 */
@Data
@TableName("invoice")
public class Invoice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long clientId;

    private Long orderId;

    /** 发票抬头（企业全称）。订单里的 clientName 是简称，不能直接用 */
    private String title;

    private String taxNo;

    private String invoiceNo;

    private BigDecimal amount;

    /** 税率，小数形式：0.0600 = 6% */
    private BigDecimal taxRate;

    /** 1=普通发票 2=专用发票 */
    private Integer invoiceType;

    private String content;

    private LocalDate issueDate;

    /** 1=待开 2=已开 3=已作废 */
    private Integer status;

    private Long applyBy;

    private LocalDateTime appliedAt;

    private String voidReason;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
