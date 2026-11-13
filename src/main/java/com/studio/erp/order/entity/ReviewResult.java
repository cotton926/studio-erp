package com.studio.erp.order.entity;

/**
 * 稿件审核结果。
 *
 * <p>【为什么用枚举而不是三个魔法数字】
 * 1/2/3 在代码里读起来毫无意义，且极易把"通过"和"退回"写反 ——
 * 这类 bug 不会报错，只会在某天发现所有稿子都显示"已通过"。
 */
public enum ReviewResult {

    PENDING(1, "待审"),
    APPROVED(2, "客户通过"),
    REJECTED(3, "客户退回");

    private final int code;
    private final String label;

    ReviewResult(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static ReviewResult of(Integer code) {
        if (code != null) {
            for (ReviewResult r : values()) {
                if (r.code == code) {
                    return r;
                }
            }
        }
        return PENDING;
    }

    public static String labelOf(Integer code) {
        return of(code).getLabel();
    }
}
