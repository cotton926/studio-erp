package com.studio.erp.receivable.domain;

/**
 * 催收方式。
 *
 * <p>【为什么要留"其他"】真实场景里还有"老板亲自打电话"、"通过介绍人施压"，
 * 枚举穷举不完。留一个兜底项 + 备注，比逼着人硬选一个错误分类要好。
 */
public enum CollectAction {

    PHONE(1, "电话"),
    WECHAT(2, "微信/QQ"),
    EMAIL(3, "邮件"),
    VISIT(4, "上门"),
    OTHER(5, "其他");

    private final int code;
    private final String label;

    CollectAction(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static CollectAction of(Integer code) {
        if (code == null) {
            return OTHER;
        }
        for (CollectAction a : values()) {
            if (a.code == code) {
                return a;
            }
        }
        throw new IllegalArgumentException("未知的催收方式: " + code);
    }

    public static String labelOf(Integer code) {
        try {
            return of(code).getLabel();
        } catch (IllegalArgumentException e) {
            return "其他";
        }
    }
}
