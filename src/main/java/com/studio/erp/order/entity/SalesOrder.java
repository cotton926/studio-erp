package com.studio.erp.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订单。
 *
 * <p>【本版本为什么不关联客户表】
 * 客户名称直接手填（clientName）。原设计里 client_id 是外键，但那样客服提一张单
 * 必须先维护客户档案、联系人、专属价 —— 一步没走完就下不了单，最终所有人绕开系统用微信。
 * 现在只留 client_name 文本快照，客户表保留但不用，等真正需要"按客户统计复购"时再上线。
 *
 * <p>【总金额为什么冗余存一份】
 * 列表页要按金额排序、业绩分摊要一个稳定的基数。如果每次 SUM 明细，
 * 一个字段改动就会让已生成的业绩和总额对不上。写入只走 setTotalAmount 的统一入口。
 */
@Data
@TableName("sales_order")
public class SalesOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号 PO{yyMM}-{三位流水}，由服务层加锁生成，唯一索引兜底 */
    private String orderNo;

    /** 客户ID：本期不做客户档案，恒为 null */
    private Long clientId;

    /** 客户名称（手填） */
    private String clientName;

    /** 项目名称（手填） */
    private String title;

    private String category;

    /** 客服（提单人） */
    private Long csEmpId;

    /** 定价主管 */
    private Long supervisorId;

    /** 签单日期；待定价状态下为空 */
    private LocalDate signDate;

    private LocalDate expectDeliverDate;

    private LocalDate actualDeliverDate;

    /** 订单总额 = Σ明细金额 */
    private BigDecimal totalAmount;

    /** 业绩分摊基数 = Σ正常行金额（赠品/返工不算） */
    private BigDecimal perfBaseAmount;

    /** 执行状态，见 OrderStatus */
    private Integer status;

    /** 收款状态，见 PaymentStatus，由金额推导而非人工维护 */
    private Integer paymentStatus;

    private Integer settleType;

    private Integer creditDays;

    private Integer version;

    private String remark;

    private Integer deleted;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
