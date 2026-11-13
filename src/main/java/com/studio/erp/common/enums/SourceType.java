package com.studio.erp.common.enums;

/**
 * 业绩流水的来源单据类型。
 *
 * <p>【作用】
 * 配合 sourceId 定位"这条业绩是哪张单、哪笔回款算出来的"，
 * 员工在业绩明细里点"追溯"，就能一路看到订单 → 回款 → 核销。
 */
public enum SourceType {

    ORDER(1, "订单"),
    PAYMENT_ALLOCATION(2, "回款核销"),
    MANUAL(3, "人工");

    private final int code;
    private final String label;

    SourceType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static SourceType of(int code) {
        for (SourceType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        throw new IllegalArgumentException("未知的来源类型: " + code);
    }
}
