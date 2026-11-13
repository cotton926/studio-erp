package com.studio.erp.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 回款单（一笔真实到账）。
 *
 * <p>【为什么回款和订单不在同一张表上】
 * 一笔回款可能覆盖多张订单（月结大客户 2~3 个月一次性结账），
 * 一张订单也可能分多次回款（订金 + 尾款）。
 * 所以肯定是多对多，中间的对应关系放在 payment_allocation 里。
 *
 * <p>【pay_date 为什么是全系统最重要的一个日期】
 * 业绩归属月就是按它算的：本月未收到的尾款，下月收到就计入下月的业绩。
 * 一旦这里录错（比如按开票日填），员工的工资就会跨月错位，
 * 事后纠正需要冲销重做，非常麻烦。
 */
@Data
@TableName("payment")
public class Payment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String paymentNo;

    /** 客户ID：本期不做客户档案，恒为 null */
    private Long clientId;

    /** 客户名称快照，取自订单，保证与订单口径一致 */
    private String clientName;

    private BigDecimal amount;

    /** 到账日期，决定业绩归属月 */
    private LocalDate payDate;

    /** 1=银行转账 2=现金 3=承兑 4=其他 */
    private Integer payMethod;

    private String bankSerial;

    private String voucherUrl;

    /** 1=待核销 2=已核销 3=已作废 */
    private Integer status;

    private Long operatorId;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
