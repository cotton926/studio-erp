package com.studio.erp.salary;

import com.studio.erp.salary.domain.SalaryBreakdown;
import com.studio.erp.salary.domain.SalaryCalculator;
import com.studio.erp.salary.domain.SalaryTierDef;
import com.studio.erp.salary.domain.SalaryTierMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工资阶梯算法的边界测试。
 *
 * <p>【为什么这些用例值得单独立一个类】
 * 工资算错是这个系统里唯一"必须人工道歉 + 补钱"的故障。
 * 边界点（19999.99 / 20000.00 / 20000.01）是错得最隐蔽的地方：
 * 差一分钱就换档，界面上完全看不出来，只有员工自己按计算器才会发现。
 * 这里把每个边界都钉死，日后任何改动只要碰坏了边界，构建就会红。
 */
class SalaryCalculatorTest {

    /** 与 V5 迁移脚本里 seed 的三档完全一致：0~2万 40%，2万~2.5万 45%，2.5万以上 50% */
    private static final List<SalaryTierDef> TIERS = List.of(
            new SalaryTierDef(1, new BigDecimal("0.00"), new BigDecimal("20000.00"), 4000),
            new SalaryTierDef(2, new BigDecimal("20000.00"), new BigDecimal("25000.00"), 4500),
            new SalaryTierDef(3, new BigDecimal("25000.00"), null, 5000));

    private static final BigDecimal NO_BASE = BigDecimal.ZERO;

    private static SalaryBreakdown whole(String performance) {
        return SalaryCalculator.compute(new BigDecimal(performance), NO_BASE, TIERS, SalaryTierMode.WHOLE);
    }

    private static SalaryBreakdown marginal(String performance) {
        return SalaryCalculator.compute(new BigDecimal(performance), NO_BASE, TIERS, SalaryTierMode.MARGINAL);
    }

    @Test
    @DisplayName("跳点：命中哪档，整笔按该档比例")
    void wholeTier() {
        // 15000 明确落在第一档（0 ~ 20000）
        assertAmount("6000.00", whole("15000").commission());
        assertEquals(4000, whole("15000").rateBp());

        // 20000 是第一次跳点：整笔按 45%
        assertAmount("9000.00", whole("20000").commission());
        assertEquals(4500, whole("20000").rateBp());

        // 25000 是第二次跳点
        assertAmount("12500.00", whole("25000").commission());
        assertEquals(5000, whole("25000").rateBp());

        // 超过最高档仍是 50%
        assertAmount("13000.00", whole("26000").commission());
    }

    @Test
    @DisplayName("跳点：档位边界左闭右开，19999.99 还在 40%")
    void wholeBoundary() {
        SalaryBreakdown bd = whole("19999.99");
        assertEquals(4000, bd.rateBp());
        // 19999.99 × 40% = 7999.996 → 四舍五入到分
        assertAmount("8000.00", bd.commission());

        // 20000.01 已经进第二档
        assertEquals(4500, whole("20000.01").rateBp());
        assertAmount("9000.00", whole("20000.01").commission());
    }

    @Test
    @DisplayName("累进：分段累加，和跳点结果不同")
    void marginalTier() {
        // 20000 以内全部 40%
        assertAmount("6000.00", marginal("15000").commission());

        // 26000 = 20000×40% + 5000×45% + 1000×50% = 8000 + 2250 + 500
        assertAmount("10750.00", marginal("26000").commission());

        // 与跳点的差距就是这类系统最容易被投诉的地方，用例里直接钉住
        assertEquals(0, new BigDecimal("13000.00").compareTo(whole("26000").commission()));
    }

    @Test
    @DisplayName("整数分不丢精度：12345.67 × 40% = 4938.268 → 4938.27")
    void rounding() {
        assertAmount("4938.27", whole("12345.67").commission());
        // 0.01 × 40% = 0.004 → 0.00，不能进到 0.01（否则每笔都往上滚一分）
        assertAmount("0.00", whole("0.01").commission());
    }

    @Test
    @DisplayName("底薪计入应发，但不受比例影响")
    void baseSalary() {
        SalaryBreakdown bd = SalaryCalculator.compute(
                new BigDecimal("15000"), new BigDecimal("3000"), TIERS, SalaryTierMode.WHOLE);
        assertAmount("6000.00", bd.commission());
        assertAmount("9000.00", bd.payable());
        assertAmount("3000.00", bd.baseSalary());
    }

    @Test
    @DisplayName("零业绩：提成为 0，底薪照发，并给出升档提示")
    void zeroPerformance() {
        SalaryBreakdown bd = whole("0");
        assertAmount("0.00", bd.commission());
        assertFalse(bd.clawback());
        assertAmount("20000.00", bd.nextTierAt());
        assertAmount("20000.00", bd.gapToNextTier());
        // 做到 20000 就能拿 9000，这是给员工看的"再努一把值多少"
        assertAmount("9000.00", bd.gainIfReachNext());
        assertEquals(4500, bd.nextTierRateBp());
    }

    @Test
    @DisplayName("升档提示：差多少业绩、多拿多少钱")
    void nextTierHint() {
        SalaryBreakdown bd = whole("15000");
        assertAmount("20000.00", bd.nextTierAt());
        assertAmount("5000.00", bd.gapToNextTier());
        // 跳点模式下 15000 → 20000 这一下多拿 3000，跳跃很明显，界面要如实展示
        assertAmount("3000.00", bd.gainIfReachNext());
    }

    @Test
    @DisplayName("已是最高档时，不再有升档提示")
    void topTier() {
        SalaryBreakdown bd = whole("30000");
        assertNull(bd.nextTierAt());
        assertNull(bd.gapToNextTier());
        assertNull(bd.gainIfReachNext());
        assertNull(bd.nextTierRateBp());
    }

    @Test
    @DisplayName("负业绩（退款冲销）：按最低档扣回，不能当成 0")
    void negativePerformance() {
        SalaryBreakdown bd = whole("-5000");
        assertTrue(bd.clawback());
        assertEquals(4000, bd.rateBp());
        // -5000 × 40% = -2000，即下月要扣回 2000
        assertAmount("-2000.00", bd.commission());
        assertAmount("-2000.00", bd.payable());
        assertNull(bd.nextTierAt());
    }

    @Test
    @DisplayName("阶梯配置错误必须当场失败，不能默默算出一个数")
    void invalidTiers() {
        // 第一档不从 0 开始：0~10000 这段业绩无档可套
        assertThrows(IllegalArgumentException.class, () -> SalaryCalculator.normalize(List.of(
                new SalaryTierDef(1, new BigDecimal("10000"), null, 4000))));

        // 断档：第一档到 20000，第二档却从 21000 开始
        assertThrows(IllegalArgumentException.class, () -> SalaryCalculator.normalize(List.of(
                new SalaryTierDef(1, new BigDecimal("0"), new BigDecimal("20000"), 4000),
                new SalaryTierDef(2, new BigDecimal("21000"), null, 4500))));

        // 空阶梯
        assertThrows(IllegalArgumentException.class, () -> SalaryCalculator.normalize(List.of()));

        // 上一档已声明无上限，还有更高档 —— 自相矛盾
        assertThrows(IllegalArgumentException.class, () -> SalaryCalculator.normalize(List.of(
                new SalaryTierDef(1, new BigDecimal("0"), null, 4000),
                new SalaryTierDef(2, new BigDecimal("20000"), null, 4500))));
    }

    @Test
    @DisplayName("乱序提交的档位会被自动排序，结果与有序一致")
    void unsortedTiers() {
        List<SalaryTierDef> shuffled = List.of(
                new SalaryTierDef(3, new BigDecimal("25000.00"), null, 5000),
                new SalaryTierDef(1, new BigDecimal("0.00"), new BigDecimal("20000.00"), 4000),
                new SalaryTierDef(2, new BigDecimal("20000.00"), new BigDecimal("25000.00"), 4500));

        SalaryBreakdown bd = SalaryCalculator.compute(
                new BigDecimal("22000"), NO_BASE, shuffled, SalaryTierMode.WHOLE);
        assertAmount("9900.00", bd.commission()); // 22000 × 45%
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        // 用 compareTo 而不是 equals：BigDecimal 的 equals 会比较 scale，
        // 6000 和 6000.00 在 equals 下不相等，这是最常见的一种假失败
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "期望 " + expected + "，实际 " + (actual == null ? "null" : actual.toPlainString()));
    }
}
