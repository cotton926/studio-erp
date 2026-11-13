package com.studio.erp.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 订单详情。
 *
 * <p>【同一张单，两种人看到的东西不一样，这是刻意的】
 * · 主管/老板：完整明细、单价、金额、所有人分成、已收未收；
 * · 设计师：能看到单里有哪些工作（他要干活，不知道做几项没法做），
 *   但看不到任何金额与他人分成，只看得到"我这行占多少、我能分多少钱"。
 *
 * <p>这件事必须在服务端完成。前端按角色隐藏字段只是"看不见"，
 * 抓包或改一行 JS 就全露了 —— 而同事之间私下比价这件事，一旦发生就无法回收。
 */
@Data
public class OrderDetailVO {

    private Long id;

    private String orderNo;

    private String clientName;

    private String title;

    private String category;

    /** 订单总额；低权限用户为 null */
    private BigDecimal totalAmount;

    /** 本单我的分成合计；高权限用户为 null */
    private BigDecimal myAmount;

    private BigDecimal paidAmount;

    /** 未收 = 总额 - 已收；低权限用户为 null */
    private BigDecimal unpaidAmount;

    private Integer status;

    private String statusText;

    private Integer paymentStatus;

    private String paymentStatusText;

    private LocalDate signDate;

    private LocalDate expectDeliverDate;

    private LocalDate actualDeliverDate;

    private String remark;

    private List<ItemVO> items;

    /**
     * 收款计划（订金 / 尾款）。
     *
     * <p>【为什么收款计划放在订单详情里，而不是只放催收页】
     * 客户问"我们还欠多少、什么时候该付"，客服打开订单就能回答，
     * 不用切到另一个页面再搜一次单号。金额相关，低权限用户（设计师）为 null。
     */
    private List<com.studio.erp.receivable.dto.PlanBriefVO> plans;

    /**
     * 开票记录。
     *
     * <p>同样只有能看金额的角色才有值。财务要核对"这单开了几张、开了多少"，
     * 让他去开票台账里按单号搜一遍，是可以用但会被抱怨的设计。
     */
    private List<com.studio.erp.invoice.dto.InvoiceVO> invoices;

    /**
     * 状态流转时间线（提单 → 定价 → 制作中 → …）。
     *
     * <p>给设计师也开放：它只含状态和时间，不含金额，
     * 但能回答"这单卡在哪一步、谁在等谁"，比反复问客服有效。
     */
    private List<TimelineVO> timeline;

    @Data
    public static class TimelineVO {
        private Integer fromStatus;
        private String fromStatusText;
        private Integer toStatus;
        private String toStatusText;
        private Long operatorId;
        private String operatorName;
        private String remark;
        private java.time.LocalDateTime createdAt;
    }

    /**
     * 明细行。
     *
     * <p>unitPrice / amount 在低权限用户下都是 null；
     * mine 表示这一行是否由当前用户参与，前端可据此高亮"我负责的活"。
     */
    @Data
    public static class ItemVO {
        private Long id;
        private Integer lineNo;
        private String itemName;
        private String spec;
        private String unit;
        private BigDecimal qty;
        private BigDecimal unitPrice;
        private BigDecimal amount;
        private Integer itemType;
        private String itemTypeText;
        private String remark;
        private Boolean mine;
        private List<AssigneeVO> assignees;
    }

    /**
     * 分成人。低权限用户只会看到自己这一条。
     */
    @Data
    public static class AssigneeVO {
        private Long employeeId;
        private String employeeName;
        private Integer assigneeRole;
        private Integer ratioBp;
        private String ratioText;
    }
}
