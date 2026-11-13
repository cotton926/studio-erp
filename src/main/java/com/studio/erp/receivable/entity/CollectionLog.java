package com.studio.erp.receivable.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 一次催收动作的留痕。
 *
 * <p>【为什么催收记录必须进系统，而不是记在微信里】
 * 尾款收不回来最常见的争议是"你们到底催过没有"。
 * 微信记录会丢、会换手机、客服会离职；系统里一条记录带时间、带人、带客户原话，
 * 才能证明公司尽到了催告义务。
 *
 * <p>【为什么一次催收是"方式 + 结果"两个字段】
 * 只记结果，没法复盘"打电话有用还是发函有用"；
 * 只记方式，等于记了一堆"已联系"，等于没记。
 */
@Data
@TableName("collection_log")
public class CollectionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    /** 针对哪一期；整单催收时为空 */
    private Long planId;

    /** 1=电话 2=微信/QQ 3=邮件 4=上门 5=其他 */
    private Integer action;

    /** 1=承诺付款 2=推诿 3=已付款 4=失联 5=需内部协调 */
    private Integer result;

    private LocalDate promiseDate;

    private BigDecimal promiseAmount;

    private String remark;

    private Long operatorId;

    private LocalDateTime createdAt;
}
