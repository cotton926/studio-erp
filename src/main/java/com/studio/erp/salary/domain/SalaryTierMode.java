package com.studio.erp.salary.domain;

/**
 * 阶梯提成的计法。
 *
 * <p>【为什么必须把"两种算法"显式摆出来，而不是只实现一种】
 * 中文里"阶梯比例"同时对应两种完全不同的算法，两者算出来的工资能差一两千块：
 *
 * <pre>
 * 以"0~20000 是 40%、20000~25000 是 45%、25000 以上 50%"为例，业绩 26000：
 *   WHOLE    跳点：26000 × 50%               = 13000
 *   MARGINAL 累进：20000×40% + 5000×45% + 1000×50% = 10750
 * </pre>
 *
 * 需求原话是"业绩 20000 是 45%，25000 是 50%"——"做到 20000 拿 45%"这种说法
 * 对应的是跳点（整笔按该档算），所以默认 {@link #WHOLE}。
 * 但老板真实的意图只有老板知道，所以做成配置项：
 * 改 {@code application.yml} 里 {@code studio.salary.tier-mode} 即可切换，
 * 不用改代码。切换后历史月份的数字会变，因此工资单必须标明用的是哪种算法。
 */
public enum SalaryTierMode {

    /** 跳点提成：命中哪一档，整笔业绩都按该档比例。业绩到 20000 那一下会跳一大截 */
    WHOLE("跳点提成（整笔按命中档次计算）"),

    /** 超额累进：把业绩分段，每段按各自比例累加，类似个人所得税 */
    MARGINAL("超额累进（分段累加，类似个税）");

    private final String label;

    SalaryTierMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 解析配置值。
     *
     * <p>【为什么非法值直接抛异常而不回退默认值】
     * 配置写错时静默回退，会让"这个月工资怎么和上个月算法不一样"变成一个查不出原因的悬案。
     * 启动即失败，反而能在上线前就发现问题。
     */
    public static SalaryTierMode parse(String text) {
        if (text == null || text.isBlank()) {
            return WHOLE;
        }
        try {
            return valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "studio.salary.tier-mode 只能是 WHOLE 或 MARGINAL，实际为：" + text);
        }
    }
}
