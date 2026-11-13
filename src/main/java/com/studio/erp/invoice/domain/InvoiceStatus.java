package com.studio.erp.invoice.domain;

/**
 * 开票状态。
 */
public enum InvoiceStatus {

    PENDING(1, "待开"),
    ISSUED(2, "已开"),
    VOIDED(3, "已作废");

    private final int code;
    private final String label;

    InvoiceStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static InvoiceStatus of(Integer code) {
        if (code == null) {
            return PENDING;
        }
        for (InvoiceStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知的开票状态: " + code);
    }

    public static String labelOf(Integer code) {
        try {
            return of(code).getLabel();
        } catch (IllegalArgumentException e) {
            return "未知";
        }
    }
}
