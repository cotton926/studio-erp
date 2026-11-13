package com.studio.erp.performance.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 业绩分摊算法：把一笔回款金额，按「明细行金额占比 × 分成比例」拆到每个设计师头上。
 *
 * <p>【这是整个系统的核心，为什么值得单独抽成一个纯函数类】
 * 全公司的工资争议都集中在这里：谁多算了 5 块钱、为什么这个月少了 300。
 * 把它写成无状态的纯函数（不碰数据库、不碰 Spring），意味着：
 * 1) 可以脱离环境跑几千个用例，把边界全部打穿；
 * 2) 同一份输入永远得到同一份输出，任何时候都能复现某个月的算法结果；
 * 3) 日后要改规则，只有这一个文件需要动。
 *
 * <p>【为什么不用"逐份四舍五入 + 最后一人吸收尾差"】
 * 那个做法在人多、金额碎的场景下会让最后一个人拿到负数：
 * 例如 0.01 元分给 3 个人，各自四舍五入都是 0.00，最后一人得 0.01 —— 看起来还行；
 * 但 0.01 元分给 3 个各 33.33% 的人时，前两个算出 0.00，最后一个得 0.01，
 * 若比例是 50/50，第一个算出 0.01（0.005 四舍五入），最后一个就是 0.00，勉强能过；
 * 一旦行数多起来，尾差可能变成 -0.01，出现"负业绩"，员工直接炸锅。
 *
 * <p>【本类的做法：累计取整法（Cumulative Rounding）】
 * 第 i 个人应得 = round(总额 × 前 i 人比例累计) − round(总额 × 前 i−1 人比例累计)
 * 由于累计值单调不减，每一步的差额必然 ≥ 0，天然不会出现负业绩；
 * 且最后一步的累计恰好是 100%，总和必然精确等于总额，一分不多一分不少。
 *
 * <p>【注意】这是纯计算，不做任何持久化。写库、幂等、状态更新在 PerformanceService 里。
 */
public final class PerformanceAllocator {

    private PerformanceAllocator() {
    }

    /**
     * 把一笔业绩金额分摊到所有明细行、所有分成人。
     *
     * @param confirmAmount 本次要确认的业绩金额（通常等于本次回款核销额，必须 &gt; 0）
     * @param items         订单的全部明细行（含赠品/返工行，内部会自动过滤）
     * @return 分摊明细，每个元素对应一条待写入的业绩流水；合计必然精确等于 confirmAmount
     * @throws IllegalArgumentException 入参非法（金额为负、无明细、基数为 0）
     * @throws IllegalStateException    某行分成比例不足或超过 100%
     */
    public static List<PerfAllocation> allocate(BigDecimal confirmAmount, List<ItemShare> items) {
        Objects.requireNonNull(items, "明细行列表不能为 null");
        if (confirmAmount == null) {
            throw new IllegalArgumentException("确认金额不能为 null");
        }
        if (confirmAmount.signum() < 0) {
            // 负数不在这里处理：退款/冲销必须走 REVERSE 流水单独入账，
            // 否则会把当月业绩算成负数，报表口径就乱了。
            throw new IllegalArgumentException(
                    "确认金额不能为负数，冲销请走独立的反向流水，实际传入 " + confirmAmount);
        }

        // 1) 只保留参与业绩的行（赠品、返工、金额为 0 的行自动排除）
        List<ItemShare> eligible = items.stream().filter(ItemShare::perfEligible).toList();
        if (eligible.isEmpty()) {
            throw new IllegalArgumentException("订单没有可计价的有效明细行，无法确认业绩");
        }

        // 2) 先把所有行的比例校验完再开始算 —— 提前失败，避免写了半截流水才发现数据有问题
        eligible.forEach(ItemShare::assertRatioFull);

        // 3) 计算分摊基数：只累计有效行的金额
        BigDecimal base = eligible.stream()
                .map(ItemShare::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (base.signum() <= 0) {
            throw new IllegalArgumentException("业绩分摊基数为 0，订单明细金额异常");
        }

        // 4) 金额为 0 时无需分摊，直接返回空列表（调用方据此跳过写库）
        if (confirmAmount.signum() == 0) {
            return List.of();
        }

        List<PerfAllocation> result = new ArrayList<>();
        BigDecimal cumulativeBase = BigDecimal.ZERO;   // 已累计的明细金额
        BigDecimal cumulativeAlloc = BigDecimal.ZERO;  // 已累计分配出去的金额

        for (ItemShare item : eligible) {
            cumulativeBase = cumulativeBase.add(item.amount());
            // 目标累计值 = 总额 × (累计基数 / 总基数)
            BigDecimal targetCumulative = MoneyUtil.divide(
                    confirmAmount.multiply(cumulativeBase), base);
            // 本行应得 = 目标累计 − 上一行的累计，必然 ≥ 0
            BigDecimal lineAmount = targetCumulative.subtract(cumulativeAlloc);
            cumulativeAlloc = targetCumulative;

            if (lineAmount.signum() == 0) {
                // 金额太小、按比例分下来不足 1 分，这一行本次不产生业绩。
                // 不写 0 元流水，避免业绩明细里出现一堆 0.00 的噪声行。
                continue;
            }
            result.addAll(splitWithinItem(item, lineAmount));
        }

        return result;
    }

    /**
     * 把某一行的金额按分成比例拆到人。
     *
     * <p>同样使用累计取整法，理由与 {@link #allocate} 一致：保证行内各人合计精确等于行金额。
     */
    private static List<PerfAllocation> splitWithinItem(ItemShare item, BigDecimal lineAmount) {
        List<AssigneeShare> assignees = item.assignees();
        List<PerfAllocation> result = new ArrayList<>(assignees.size());

        int cumulativeBp = 0;
        BigDecimal cumulativeAlloc = BigDecimal.ZERO;

        for (AssigneeShare assignee : assignees) {
            cumulativeBp += assignee.ratioBp();
            // 用累进比例算目标值：lineAmount × 累计比例 / 10000
            BigDecimal targetCumulative = MoneyUtil.divide(
                    lineAmount.multiply(BigDecimal.valueOf(cumulativeBp)),
                    BigDecimal.valueOf(RatioUtil.FULL_BP));
            BigDecimal share = targetCumulative.subtract(cumulativeAlloc);
            cumulativeAlloc = targetCumulative;

            if (share.signum() == 0) {
                // 该员工分到的金额不足 1 分，跳过；
                // 金额会在其他人的累计里自动补回，总额不受影响。
                continue;
            }
            result.add(new PerfAllocation(
                    assignee.employeeId(), item.itemId(), item.itemName(), share, assignee.ratioBp()));
        }
        return result;
    }

    /**
     * 汇总每个人的合计业绩（用于写月汇总表与页面顶部数字卡片）。
     *
     * <p>直接用 BigDecimal::add 累加，不做中间舍入 —— 明细已经是分，加完还是分。
     */
    public static BigDecimal total(List<PerfAllocation> allocations) {
        return allocations.stream()
                .map(PerfAllocation::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MoneyUtil.SCALE, RoundingMode.HALF_UP);
    }
}
