package com.studio.erp.salary.domain;

import com.studio.erp.employee.domain.EmpLevel;
import com.studio.erp.performance.domain.MoneyUtil;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 底薪解析：员工到底按多少钱发底薪。
 *
 * <p>【为什么单独抽出来，而不是在 SalaryService 里写两行 if】
 * 这段逻辑只有三行，但它决定了每个人工资的基数，且有三个容易搞反的分支：
 * 个人覆盖优先、级别政策兜底、级别没配要报错而不是给 0。
 * 抽成纯函数后，可以在单测里把三种情况各跑一遍，不需要数据库和 Spring 上下文。
 *
 * <p>【为什么"级别没配"要报错，不能默认 0】
 * 默认 0 的表现是"这个人这个月底薪是 0"，页面数字合法、没有异常，
 * 只有员工拿着工资条来问才会发现。而报错会在主管打开工资页时立刻暴露，
 * 修一行数据就恢复。少发工资的事故成本远高于一次页面报错。
 */
public final class BaseSalaryResolver {

    /** 底薪的来源，需要随工资单返回：员工会问"我这 2000 是怎么来的" */
    public enum Source {
        /** 按员工级别取政策金额 */
        LEVEL("按级别"),
        /** 员工档案里单独指定的金额，覆盖级别政策 */
        PERSONAL("个人指定");

        private final String label;

        Source(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /**
     * 解析结果。
     *
     * @param amount 实际底薪（已取到分）
     * @param source 来源：按级别 / 个人指定
     * @param level  员工级别
     */
    public record Resolution(BigDecimal amount, Source source, EmpLevel level) {
    }

    private BaseSalaryResolver() {
    }

    /**
     * 解析某人的底薪。
     *
     * @param personalOverride 员工档案上的个人底薪；null = 未指定，跟随级别
     * @param level            员工级别；null 视为普通员工（老数据兼容）
     * @param policies         级别 → 政策底薪
     */
    public static Resolution resolve(BigDecimal personalOverride,
                                     EmpLevel level,
                                     Map<EmpLevel, BigDecimal> policies) {
        EmpLevel effectiveLevel = level == null ? EmpLevel.NORMAL : level;

        // 个人指定优先：谈好的特殊待遇必须能覆盖政策，否则每次调薪政策都要重新核对一遍名单
        if (personalOverride != null) {
            return new Resolution(MoneyUtil.yuan(personalOverride), Source.PERSONAL, effectiveLevel);
        }

        BigDecimal policy = policies == null ? null : policies.get(effectiveLevel);
        if (policy == null) {
            throw new IllegalArgumentException(String.format(
                    "员工级别「%s」的底薪未配置，请先在底薪政策里补上", effectiveLevel.getLabel()));
        }
        return new Resolution(MoneyUtil.yuan(policy), Source.LEVEL, effectiveLevel);
    }
}
