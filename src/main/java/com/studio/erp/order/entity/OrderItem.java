package com.studio.erp.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细行。
 *
 * <p>【单价为什么是快照而不是引用价目表】
 * 本版本单价由主管手填（客户沟通好的价格个案化很强，同一个"画册设计"16P 和 48P 不是一个价）。
 * 手填后直接固化在 unit_price 里：明年调价、后年改规则，这一行的金额都不会变。
 * 如果改成"存 price_item_id、显示时 join 取价"，一次调价就会把三年前的业绩全部改写，
 * 员工会认为系统在篡改他的钱 —— 这是这类系统最致命的信任崩塌。
 */
@Data
@TableName("order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    /** 行号，从 1 开始，仅用于展示顺序 */
    private Integer lineNo;

    /** 来源价目项：本期手填，恒为 null */
    private Long priceItemId;

    private String itemName;

    private String spec;

    private String unit;

    private BigDecimal qty;

    /** 成交单价（主管手填） */
    private BigDecimal unitPrice;

    /** 小计 = qty × unitPrice，由服务层计算，不接受前端传值 */
    private BigDecimal amount;

    /** 1=正常 2=赠品 3=返工 */
    private Integer itemType;

    /** 1=正常 0=作废 */
    private Integer status;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
