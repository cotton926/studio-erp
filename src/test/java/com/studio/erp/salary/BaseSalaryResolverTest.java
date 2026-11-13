package com.studio.erp.salary;

import com.studio.erp.employee.domain.EmpLevel;
import com.studio.erp.salary.domain.BaseSalaryResolver;
import com.studio.erp.salary.domain.SalaryBreakdown;
import com.studio.erp.salary.domain.SalaryCalculator;
import com.studio.erp.salary.domain.SalaryTierDef;
import com.studio.erp.salary.domain.SalaryTierMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 分级底薪解析的测试。
 *
 * <p>【为什么这条逻辑必须单独立测】
 * 它只有三个分支，但每个分支错了都是"几百块钱的差"，而且在页面上完全看不出来：
 * 个人覆盖没生效、级别没取到、缺配置被当成 0 —— 三种表现都是"底薪是个数字"。
 * 只有断言到"金额 + 来源"两个维度，才能把"按级别"和"个人指定"区分开。
 */
class BaseSalaryResolverTest {

    /** 与 V6 迁移里的 seed 一致：普通 1500 / 精英 2000 / 主管 3000 */
    private static final Map<EmpLevel, BigDecimal> POLICIES = new EnumMap<>(Map.of(
            EmpLevel.NORMAL, new BigDecimal("1500.00"),
            EmpLevel.ELITE, new BigDecimal("2000.00"),
            EmpLevel.SUPERVISOR, new BigDecimal("3000.00")));

    private static BaseSalaryResolver.Resolution resolve(String personal, EmpLevel level) {
        return BaseSalaryResolver.resolve(
                personal == null ? null : new BigDecimal(personal), level, POLICIES);
    }

    @Test
    @DisplayName("未指定个人底薪时，按级别取政策金额")
    void fallsBackToLevelPolicy() {
        BaseSalaryResolver.Resolution normal = resolve(null, EmpLevel.NORMAL);
        assertEquals(new BigDecimal("1500.00"), normal.amount());
        assertEquals(BaseSalaryResolver.Source.LEVEL, normal.source());
        assertEquals(EmpLevel.NORMAL, normal.level());

        assertEquals(new BigDecimal("2000.00"), resolve(null, EmpLevel.ELITE).amount());
        assertEquals(new BigDecimal("3000.00"), resolve(null, EmpLevel.SUPERVISOR).amount());
    }

    @Test
    @DisplayName("个人指定优先于级别政策")
    void personalOverridesPolicy() {
        BaseSalaryResolver.Resolution r = resolve("1800", EmpLevel.SUPERVISOR);
        assertEquals(new BigDecimal("1800.00"), r.amount());
        assertEquals(BaseSalaryResolver.Source.PERSONAL, r.source());
        // 级别仍要如实返回：工资单上要写"主管（个人指定 1800）"，两件事不冲突
        assertEquals(EmpLevel.SUPERVISOR, r.level());
    }

    @Test
    @DisplayName("个人指定 0 是合法特例，不能被当成未设置")
    void zeroOverrideIsNotUnset() {
        BaseSalaryResolver.Resolution r = resolve("0", EmpLevel.NORMAL);
        assertEquals(new BigDecimal("0.00"), r.amount());
        assertEquals(BaseSalaryResolver.Source.PERSONAL, r.source());
    }

    @Test
    @DisplayName("级别为空的老数据按普通员工兜底")
    void nullLevelFallsBackToNormal() {
        assertEquals(new BigDecimal("1500.00"), resolve(null, null).amount());
    }

    @Test
    @DisplayName("个人底薪四舍五入到分")
    void personalRoundsToCent() {
        assertEquals(new BigDecimal("1800.01"), resolve("1800.005", EmpLevel.ELITE).amount());
        assertEquals(new BigDecimal("1799.99"), resolve("1799.994", EmpLevel.ELITE).amount());
    }

    @Test
    @DisplayName("级别政策缺失时报错，而不是静默发 0 元底薪")
    void missingPolicyThrows() {
        Map<EmpLevel, BigDecimal> onlyNormal = new EnumMap<>(EmpLevel.class);
        onlyNormal.put(EmpLevel.NORMAL, new BigDecimal("1500.00"));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> BaseSalaryResolver.resolve(null, EmpLevel.ELITE, onlyNormal));
        // 提示里必须带级别名：只说"配置缺失"，主管不知道该补哪一条
        assertEquals(true, e.getMessage().contains("精英员工"));
    }

    @Test
    @DisplayName("底薪 + 提成 = 应发，与工资单口径一致")
    void basePlusCommissionIsPayable() {
        List<SalaryTierDef> tiers = List.of(
                new SalaryTierDef(1, new BigDecimal("0.00"), new BigDecimal("20000.00"), 4000),
                new SalaryTierDef(2, new BigDecimal("20000.00"), new BigDecimal("25000.00"), 4500),
                new SalaryTierDef(3, new BigDecimal("25000.00"), null, 5000));

        BigDecimal base = resolve(null, EmpLevel.ELITE).amount();
        SalaryBreakdown bd = SalaryCalculator.compute(
                new BigDecimal("15000.00"), base, tiers, SalaryTierMode.WHOLE);

        // 15000 × 40% = 6000，加精英档底薪 2000，应发 8000
        assertEquals(new BigDecimal("6000.00"), bd.commission());
        assertEquals(new BigDecimal("8000.00"), bd.payable());
    }
}
