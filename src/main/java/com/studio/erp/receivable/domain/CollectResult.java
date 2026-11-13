package com.studio.erp.receivable.domain;

/**
 * 催收结果。
 *
 * <p>【为什么"已付款"要作为一个结果选项】
 * 客服打电话当天客户就转了账，如果只能选"承诺付款"，系统里会显示一条待跟进的承诺，
 * 而实际上钱已经到账。允许直接选"已付款"，提示客服去登记回款，
 * 比让他撒谎选"承诺付款"要好。
 */
public enum CollectResult {

    PROMISED(1, "承诺付款"),
    EXCUSED(2, "推诿拖延"),
    PAID(3, "已付款"),
    UNREACHABLE(4, "联系不上"),
    INTERNAL(5, "需内部协调");

    private final int code;
    private final String label;

    CollectResult(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static CollectResult of(Integer code) {
        if (code == null) {
            return EXCUSED;
        }
        for (CollectResult r : values()) {
            if (r.code == code) {
                return r;
            }
        }
        throw new IllegalArgumentException("未知的催收结果: " + code);
    }

    public static String labelOf(Integer code) {
        try {
            return of(code).getLabel();
        } catch (IllegalArgumentException e) {
            return "其他";
        }
    }
}
