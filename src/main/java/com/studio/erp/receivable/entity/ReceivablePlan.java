package com.studio.erp.receivable.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 收款计划的一期（订金 / 尾款）。
 *
 * <p>【这个表存在的意义：把"什么时候该收钱"变成一条可查询的记录】
 * 订单上只有一个"未收 12000"，它回答不了"这笔钱什么时候该到、逾期多久、催过几次"。
 * 尾款不是自动到账的，到期前得有人打电话。没有计划行，催收就只能靠人脑记，
 * 而人脑记不住几十张单。
 *
 * <p>【received_amount 为什么冗余】
 * 催收清单要按"未收讫 + 应收日"排序，如果每次从 allocation 聚合，
 * 一条 SQL 里要套三层子查询，几十张单就开始慢。冗余值由 {@code recalc} 统一重算，
 * 和订单的 payment_status 一样，永远是派生值，不接受手工维护。
 */
@Data
@TableName("receivable_plan")
public class ReceivablePlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    /** 期次：1=订金 2=尾款；uk_order_seq 保证一单不会有两笔订金 */
    private Integer seq;

    private String planName;

    private BigDecimal planAmount;

    /** 计划收款日期；尾款固定取交付月（未交付取签单月）的最后一天 */
    private LocalDate planDate;

    private BigDecimal receivedAmount;

    /** 见 PlanStatus：1=未收 2=部分收 3=已收讫 */
    private Integer status;

    /** 累计催收次数，只增不减 —— 它是"这个客户被追了几次"的证据 */
    private Integer remindCount;

    private LocalDateTime lastRemindAt;

    /** 客户最近一次承诺的回款日期；过期未付即为"承诺跳票"，清单里要高亮 */
    private LocalDate promiseDate;

    private BigDecimal promiseAmount;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
