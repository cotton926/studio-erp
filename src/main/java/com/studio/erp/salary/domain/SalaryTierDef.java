package com.studio.erp.salary.domain;

import java.math.BigDecimal;

/**
 * 一个提成档位（只读视图，不是数据库实体）。
 *
 * <p>【为什么单独定义而不是直接用 Entity】
 * 计算逻辑要能脱离数据库跑单测。用 Entity 意味着每次测边界都得先造一个 MyBatis 对象，
 * 而档位计算恰恰是最需要穷举边界的地方（19999.99、20000.00、20000.01 各算一遍）。
 *
 * @param levelNo   档位序号，仅用于展示
 * @param minAmount 下限（含）
 * @param maxAmount 上限（不含）；null 表示无上限
 * @param rateBp    比例，万分比：4000 = 40%
 */
public record SalaryTierDef(int levelNo, BigDecimal minAmount, BigDecimal maxAmount, int rateBp) {

    public SalaryTierDef {
        if (minAmount == null) {
            throw new IllegalArgumentException("档位下限不能为空，档位 " + levelNo);
        }
        if (maxAmount != null && maxAmount.compareTo(minAmount) <= 0) {
            throw new IllegalArgumentException(String.format(
                    "档位 %d 的上限(%s)必须大于下限(%s)", levelNo, maxAmount, minAmount));
        }
        if (rateBp < 0 || rateBp > 10000) {
            throw new IllegalArgumentException("档位 " + levelNo + " 的比例必须在 0~10000bp 之间");
        }
    }

    /**
     * 该档是否覆盖给定业绩。
     *
     * <p>【为什么用左闭右开】
     * 闭区间会让 20000 同时属于第 1 档（≤20000）和第 2 档（≥20000），
     * 命中哪一档取决于遍历顺序 —— 同一笔业绩在两次计算里可能得到不同工资。
     * 左闭右开（min ≤ x &lt; max）保证每个数值只属于一档，边界没有歧义。
     */
    public boolean covers(BigDecimal amount) {
        if (amount.compareTo(minAmount) < 0) {
            return false;
        }
        return maxAmount == null || amount.compareTo(maxAmount) < 0;
    }

    /** 上限，无上限时返回 fallback（通常传当前业绩，用于累进计算时"截到业绩为止"） */
    public BigDecimal upperOr(BigDecimal fallback) {
        return maxAmount == null ? fallback : maxAmount;
    }
}
