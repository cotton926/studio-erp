package com.studio.erp.common.enums;

/**
 * 订单执行状态。
 *
 * <p>【为什么要把状态机写进代码，而不是散落在 Service 的 if-else 里】
 * 状态流转是最容易被写乱的东西：新加一个状态、改一条规则，往往只能靠人肉搜索
 * 所有 if (status == 3) 去找。集中成一个枚举 + 转移表之后：
 * 规则只有一份，改一处全系统生效；非法流转（如"已取消"的单又被改成"已交付"）
 * 在状态变更的唯一入口就被拦下，而不是等到报表出现脏数据才发现。
 *
 * <p>【注意】这只管"执行"这条线；收款进度由 {@link PaymentStatus} 单独表达，
 * 两条线互不阻塞 —— 已交付但未收款、或未交付但客户先付款，都是合法业务场景。
 */
public enum OrderStatus {

    /** 草稿：客服提单中，还未提交给主管 */
    DRAFT(0, "草稿"),

    /** 待定价：客服已提交，金额为空，等待主管录入明细与单价 */
    PENDING_PRICE(1, "待定价"),

    /** 已确认：主管录完明细与分成，签单额已生成，订单生效 */
    CONFIRMED(2, "已确认"),

    /** 执行中：已派单，设计师开始做 */
    IN_PROGRESS(3, "执行中"),

    /** 已交付：成品已交给客户 */
    DELIVERED(4, "已交付"),

    /** 已取消 / 废单：需审批，审批通过时生成冲销流水 */
    CANCELLED(9, "已取消");

    private final int code;
    private final String label;

    OrderStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 允许的状态流转表。
     *
     * <p>用静态块初始化而不是在构造器里引用其他枚举常量 —— 枚举构造期其他常量尚未初始化，
     * 直接引用会得到 null，这是一个很隐蔽的坑。
     */
    private static final java.util.Map<OrderStatus, java.util.Set<OrderStatus>> TRANSITIONS =
            new java.util.EnumMap<>(OrderStatus.class);

    static {
        TRANSITIONS.put(DRAFT, java.util.EnumSet.of(PENDING_PRICE, CANCELLED));
        TRANSITIONS.put(PENDING_PRICE, java.util.EnumSet.of(CONFIRMED, CANCELLED));
        TRANSITIONS.put(CONFIRMED, java.util.EnumSet.of(IN_PROGRESS, CANCELLED));
        TRANSITIONS.put(IN_PROGRESS, java.util.EnumSet.of(DELIVERED, CANCELLED));
        // 已交付不允许再取消：钱可能已经收了，取消要走"退款 + 冲销"流程，不能简单改状态
        TRANSITIONS.put(DELIVERED, java.util.EnumSet.noneOf(OrderStatus.class));
        TRANSITIONS.put(CANCELLED, java.util.EnumSet.noneOf(OrderStatus.class));
    }

    /** 当前状态能否流转到目标状态 */
    public boolean canTransitTo(OrderStatus target) {
        return TRANSITIONS.getOrDefault(this, java.util.EnumSet.noneOf(OrderStatus.class)).contains(target);
    }

    /** 是否终态（不可再流转） */
    public boolean isTerminal() {
        return TRANSITIONS.getOrDefault(this, java.util.EnumSet.noneOf(OrderStatus.class)).isEmpty();
    }

    public static OrderStatus of(int code) {
        for (OrderStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知的订单状态: " + code);
    }
}
