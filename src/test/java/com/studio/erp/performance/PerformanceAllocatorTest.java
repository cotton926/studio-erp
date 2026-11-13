package com.studio.erp.performance;

import com.studio.erp.performance.domain.AssigneeShare;
import com.studio.erp.performance.domain.ItemShare;
import com.studio.erp.performance.domain.ItemType;
import com.studio.erp.performance.domain.PerfAllocation;
import com.studio.erp.performance.domain.PerformanceAllocator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业绩分摊算法测试。
 *
 * <p>【为什么这些用例值得写】
 * 这几条覆盖的是"上了线以后一定会被人拿计算器验算"的场景：
 * 单人多人的拆分、小数除不尽、金额小到 1 分钱、赠品与返工不该占业绩。
 * 任何一条挂了，都意味着某个设计师看到自己的钱不对。
 *
 * <p>【断言的核心不变量】
 * Σ(所有人业绩) == 本次核销金额，且无负数。这条比任何具体数字都重要。
 */
class PerformanceAllocatorTest {

    private static ItemShare single(Long itemId, String name, String amount, AssigneeShare... assignees) {
        return new ItemShare(itemId, name, new BigDecimal(amount), ItemType.NORMAL, List.of(assignees));
    }

    @Test
    @DisplayName("单人 100%：订金 6000 全额归该设计师")
    void singlePersonFullRatio() {
        List<PerfAllocation> result = PerformanceAllocator.allocate(
                new BigDecimal("6000.00"),
                List.of(single(1L, "主视觉海报", "20000.00", new AssigneeShare(101L, 10000))));

        assertEquals(1, result.size());
        assertEquals(new BigDecimal("6000.00"), result.get(0).amount());
        assertEquals(101L, result.get(0).employeeId());
    }

    @Test
    @DisplayName("两人 70/30：6000 拆成 4200 与 1800")
    void twoPersonsSplit() {
        List<PerfAllocation> result = PerformanceAllocator.allocate(
                new BigDecimal("6000.00"),
                List.of(single(1L, "画册", "10000.00",
                        new AssigneeShare(101L, 7000),
                        new AssigneeShare(102L, 3000))));

        assertEquals(2, result.size());
        assertEquals(new BigDecimal("4200.00"), result.get(0).amount());
        assertEquals(new BigDecimal("1800.00"), result.get(1).amount());
        assertEquals(new BigDecimal("6000.00"), PerformanceAllocator.total(result));
    }

    @Test
    @DisplayName("三行除不尽：3333.33×3 分摊 10000，合计必须精确等于 10000")
    void nonDivisibleAmounts() {
        List<ItemShare> items = List.of(
                single(1L, "内页A", "3333.33", new AssigneeShare(101L, 5000), new AssigneeShare(102L, 5000)),
                single(2L, "内页B", "3333.33", new AssigneeShare(101L, 5000), new AssigneeShare(102L, 5000)),
                single(3L, "内页C", "3333.33", new AssigneeShare(101L, 5000), new AssigneeShare(102L, 5000)));

        List<PerfAllocation> result = PerformanceAllocator.allocate(new BigDecimal("10000.00"), items);

        // 关键断言：总和不能多一分也不能少一分
        assertEquals(new BigDecimal("10000.00"), PerformanceAllocator.total(result));
        result.forEach(a -> assertTrue(a.amount().signum() > 0, "不应出现 0 或负数业绩"));
    }

    @Test
    @DisplayName("极小额：1 分钱分给三人，合计仍是 1 分，且无人得负数")
    void tinyAmountNoNegative() {
        List<PerfAllocation> result = PerformanceAllocator.allocate(
                new BigDecimal("0.01"),
                List.of(single(1L, "修改一处", "100.00",
                        new AssigneeShare(101L, 3300),
                        new AssigneeShare(102L, 3300),
                        new AssigneeShare(103L, 3400))));

        assertEquals(new BigDecimal("0.01"), PerformanceAllocator.total(result));
        result.forEach(a -> assertTrue(a.amount().signum() > 0, "极端情况下也不能出现负数业绩"));
    }

    @Test
    @DisplayName("赠品与返工行不参与分摊，不稀释正常业绩")
    void giftAndReworkExcluded() {
        ItemShare normal = single(1L, "海报", "10000.00", new AssigneeShare(101L, 10000));
        ItemShare gift = new ItemShare(2L, "赠品-名片", new BigDecimal("0.00"), ItemType.GIFT,
                List.of(new AssigneeShare(101L, 10000)));
        ItemShare rework = new ItemShare(3L, "返工-换文案", new BigDecimal("0.00"), ItemType.REWORK,
                List.of());

        List<PerfAllocation> result = PerformanceAllocator.allocate(
                new BigDecimal("10000.00"), List.of(normal, gift, rework));

        assertEquals(1, result.size(), "赠品与返工行不应产生业绩流水");
        assertEquals(new BigDecimal("10000.00"), result.get(0).amount());
    }

    @Test
    @DisplayName("分成比例不足 100% 时拒绝确认，并给出可读的报错")
    void rejectIncompleteRatio() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                PerformanceAllocator.allocate(new BigDecimal("1000.00"),
                        List.of(single(1L, "包装", "5000.00",
                                new AssigneeShare(101L, 7000),
                                new AssigneeShare(102L, 2000)))));

        assertTrue(ex.getMessage().contains("90%"), "报错信息里要直接告诉主管差多少");
    }

    @Test
    @DisplayName("没有有效明细行时拒绝确认，避免生成 0 元业绩")
    void rejectNoEligibleItem() {
        ItemShare gift = new ItemShare(1L, "赠品", new BigDecimal("0.00"), ItemType.GIFT,
                List.of(new AssigneeShare(101L, 10000)));

        assertThrows(IllegalArgumentException.class, () ->
                PerformanceAllocator.allocate(new BigDecimal("500.00"), List.of(gift)));
    }

    @Test
    @DisplayName("负数金额必须被拒绝，冲销要走独立反向流水")
    void rejectNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () ->
                PerformanceAllocator.allocate(new BigDecimal("-100.00"),
                        List.of(single(1L, "海报", "1000.00", new AssigneeShare(101L, 10000)))));
    }
}
