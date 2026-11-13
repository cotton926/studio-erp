package com.studio.erp.receivable.domain;

/**
 * 收款计划某一期的状态。
 *
 * <p>【为什么不复用订单的 PaymentStatus】
 * 订单级状态是 0/1/2（未收/部分/结清），计划级是 1/2/3，语义相同但编码不同。
 * 复用会让"计划行的 1 到底指未收还是部分收"变成每次都要想一下的问题，
 * 转账时一个 off-by-one 就是"该催的不催、不该催的猛催"。两种编号各自独立，反而清楚。
 */
public enum PlanStatus {

    OPEN(1, "未收"),
    PARTIAL(2, "部分收"),
    PAID(3, "已收讫");

    private final int code;
    private final String label;

    PlanStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 由"已收 / 应收"推导状态，不接受人工维护，理由同订单收款状态 */
    public static PlanStatus derive(java.math.BigDecimal received, java.math.BigDecimal planAmount) {
        if (planAmount == null || planAmount.signum() <= 0) {
            return OPEN;
        }
        java.math.BigDecimal r = received == null ? java.math.BigDecimal.ZERO : received;
        if (r.compareTo(planAmount) >= 0) {
            return PAID;
        }
        return r.signum() > 0 ? PARTIAL : OPEN;
    }

    public static PlanStatus of(Integer code) {
        if (code == null) {
            return OPEN;
        }
        for (PlanStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知的收款计划状态: " + code);
    }
}
