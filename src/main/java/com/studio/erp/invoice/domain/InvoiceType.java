package com.studio.erp.invoice.domain;

/**
 * 发票类型。
 *
 * <p>【为什么必须区分普票和专票】专票能让客户抵扣进项税，所以客户会指定要哪一种；
 * 财务开错类型要作废重开，一张票的作废流程比开票本身还长。
 * 申请时就让申请人选，别让财务猜。
 */
public enum InvoiceType {

    NORMAL(1, "普通发票"),
    SPECIAL(2, "专用发票");

    private final int code;
    private final String label;

    InvoiceType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static InvoiceType of(Integer code) {
        if (code == null) {
            return NORMAL;
        }
        for (InvoiceType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        throw new IllegalArgumentException("未知的发票类型: " + code);
    }

    public static String labelOf(Integer code) {
        try {
            return of(code).getLabel();
        } catch (IllegalArgumentException e) {
            return "普通发票";
        }
    }
}
