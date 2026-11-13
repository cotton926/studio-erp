package com.studio.erp.salary.domain;

import java.math.BigDecimal;

/**
 * 一次工资核算的结果（不可变快照）。
 *
 * <p>【为什么把"下一档差多少、到了能多拿多少"也算出来】
 * 员工端最容易被问的两个问题是"我这个月为什么只有 40%"和"再多做多少能升档"。
 * 如果系统只显示一个提成数字，答案只能由主管口头算，而且每个人算一遍都可能不一样。
 * 把这两个值随工资一起返回，员工自己就能看到差距，这一条比任何激励文案都有效。
 *
 * <p>注意 {@code gainIfReachNext} 在跳点模式下会是个"跳跃值"（例如业绩到 20000 那一下多拿 1000+），
 * 这是跳点算法本身的性质，不是 bug；界面上要如实展示，否则员工会怀疑系统算错。
 *
 * @param performance     本月业绩（= 已到账回款产生的业绩，不含签单额）
 * @param baseSalary      底薪
 * @param commission      提成 = 业绩 × 命中档比例
 * @param payable         应发 = 底薪 + 提成
 * @param rateBp          命中的比例，万分比
 * @param mode            本次使用的算法
 * @param clawback        true 表示本月业绩为负（退款/冲销大于新增），提成为负值，需在后续月份扣回
 * @param nextTierAt      下一档的业绩起点；已是最高档时为 null
 * @param gapToNextTier   距下一档还差多少业绩；已是最高档时为 null
 * @param gainIfReachNext 达到下一档起点后提成会变成多少，减去当前提成即为"多拿多少"；已是最高档时为 null
 * @param nextTierRateBp  下一档的比例；已是最高档时为 null
 */
public record SalaryBreakdown(
        BigDecimal performance,
        BigDecimal baseSalary,
        BigDecimal commission,
        BigDecimal payable,
        int rateBp,
        SalaryTierMode mode,
        boolean clawback,
        BigDecimal nextTierAt,
        BigDecimal gapToNextTier,
        BigDecimal gainIfReachNext,
        Integer nextTierRateBp) {
}
