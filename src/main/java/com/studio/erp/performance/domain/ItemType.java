package com.studio.erp.performance.domain;

/**
 * 明细行类型。
 *
 * <p>【为什么要有这个枚举】
 * 赠品和返工行金额都是 0，看起来不影响计算，但它们的"存在"会影响分母与单量统计。
 *
 * <p>【作用】
 * 决定该行是否参与业绩分摊（perfEligible）。
 *
 * <p>【不这么做会出什么问题】
 * 若把返工行也计入分摊基数，客户免费加做的三版返工会把正常订单的业绩摊薄，
 * 设计师做返工不但不挣钱，还会拉低自己的数字 —— 这是最伤士气的一种 bug。
 *
 * <p>【怎么解决】
 * 分摊时只取 perfEligible 的行；赠品与返工只计单量，不计金额。
 */
public enum ItemType {

    /** 正常收费项 */
    NORMAL(1, "正常", true),

    /** 赠品：金额 0，计单量但不计业绩 */
    GIFT(2, "赠品", false),

    /** 返工：不计金额、不计业绩，只统计返工次数 */
    REWORK(3, "返工", false);

    private final int code;
    private final String label;
    private final boolean perfEligible;

    ItemType(int code, String label, boolean perfEligible) {
        this.code = code;
        this.label = label;
        this.perfEligible = perfEligible;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 是否参与业绩分摊 */
    public boolean isPerfEligible() {
        return perfEligible;
    }

    public static ItemType of(int code) {
        for (ItemType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        throw new IllegalArgumentException("未知的明细行类型: " + code);
    }
}
