package com.studio.erp.common.enums;

/**
 * 订单收款状态（与执行状态相互独立）。
 *
 * <p>【为什么独立】
 * "这个月交付了几单"和"这个月还有多少没收"是两个部门问的问题：
 * 主管关心前者，财务和老板关心后者。合成一个状态字段，任何一边推进都会覆盖另一边。
 */
public enum PaymentStatus {

    UNPAID(0, "未收"),
    PARTIAL(1, "部分收款"),
    SETTLED(2, "已结清");

    private final int code;
    private final String label;

    PaymentStatus(int code, String label) {
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
     * 根据「已收金额 / 订单总额」推导收款状态。
     *
     * <p>【为什么不让人手工选】
     * 手工选状态必然和实际核销金额对不上，最后出现"状态显示已结清但还剩 3000 没收"。
     * 状态永远由金额推导，是派生值，不接受人工维护。
     */
    public static PaymentStatus derive(java.math.BigDecimal paid, java.math.BigDecimal total) {
        if (paid == null || paid.signum() <= 0) {
            return UNPAID;
        }
        if (total == null || total.signum() <= 0) {
            return PARTIAL;
        }
        // 用 compareTo 而不是 equals：BigDecimal 的 equals 会比较 scale，
        // 6000.00 和 6000 用 equals 判不相等，这是最经典的坑之一
        return paid.compareTo(total) >= 0 ? SETTLED : PARTIAL;
    }

    public static PaymentStatus of(int code) {
        for (PaymentStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知的收款状态: " + code);
    }
}
