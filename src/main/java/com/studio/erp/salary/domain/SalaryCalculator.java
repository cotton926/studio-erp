package com.studio.erp.salary.domain;

import com.studio.erp.performance.domain.MoneyUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 工资核算算法：业绩 → 阶梯比例 → 提成 → 应发。
 *
 * <p>【为什么又是一个纯函数类】
 * 和 {@link com.studio.erp.performance.domain.PerformanceAllocator} 同理：
 * 工资是最不能出错、也最需要穷举边界的计算。纯函数意味着可以对着
 * 19999.99 / 20000.00 / 20000.01 这种"差一分钱换档"的点各跑一遍，
 * 不需要数据库、不需要 Spring 上下文，跑一次几十毫秒。
 *
 * <p>【取整为什么只做一次】
 * 提成 = 业绩 × 比例，结果是无限小数（例如 14999.99 × 40% = 5999.996）。
 * 如果先算到 4 位再取 2 位，5999.996 → 6000.00 → 6000.00；
 * 直接取 2 位也是 6000.00，看起来一样。但累进模式下要把三段加起来，
 * 每段各自取整再相加，和"先加总再一次取整"会差 1~2 分。
 * 工资单上出现 1 分钱的差额，员工一定会问，而且很难解释。
 * 所以本类统一：**中间全程保留精确值，只在最后一步取整到分**。
 *
 * <p>【负数业绩怎么处理（退款、废单冲销）】
 * 业绩为负时无法套用任何档位（所有档位下限都是 0）。这里的做法是：
 * 按最低档比例对负数本身计算，得到负的提成（即"扣回"），并打上 clawback 标记。
 * 不用"取绝对值再算"或"直接按 0 处理"两种省事的写法 ——
 * 前者等于公司替员工承担退款，后者等于退款不计成本，两种都会让账长期对不平。
 */
public final class SalaryCalculator {

    /** 100% 对应万分比，与 order_item_assignee.ratio_bp 口径一致 */
    private static final BigDecimal FULL_BP = BigDecimal.valueOf(10000);

    private SalaryCalculator() {
    }

    /**
     * 核算一个月工资。
     *
     * @param performance 本月业绩（可为负）
     * @param baseSalary  底薪（null 视为 0）
     * @param tiers       阶梯，必须已按 minAmount 升序且互不重叠
     * @param mode        算法（跳点 / 累进）
     */
    public static SalaryBreakdown compute(BigDecimal performance,
                                         BigDecimal baseSalary,
                                         List<SalaryTierDef> tiers,
                                         SalaryTierMode mode) {
        Objects.requireNonNull(mode, "阶梯算法不能为空");
        List<SalaryTierDef> sorted = normalize(tiers);

        BigDecimal perf = MoneyUtil.yuan(performance);
        BigDecimal base = MoneyUtil.yuan(baseSalary);

        // ---------- 负数业绩：按最低档扣回 ----------
        if (perf.signum() < 0) {
            int rate = sorted.get(0).rateBp();
            BigDecimal commission = rateOf(perf, rate);
            return new SalaryBreakdown(
                    perf, base, commission, base.add(commission), rate, mode,
                    true, null, null, null, null);
        }

        // ---------- 正数业绩：找命中档 ----------
        SalaryTierDef matched = match(perf, sorted);
        BigDecimal commission = mode == SalaryTierMode.WHOLE
                ? rateOf(perf, matched.rateBp())
                : marginal(perf, sorted);

        // ---------- 下一档：只找"起点大于当前业绩"的最小一档 ----------
        SalaryTierDef next = sorted.stream()
                .filter(t -> t.minAmount().compareTo(perf) > 0)
                .min(Comparator.comparing(SalaryTierDef::minAmount))
                .orElse(null);

        BigDecimal nextAt = null;
        BigDecimal gap = null;
        BigDecimal gain = null;
        Integer nextRate = null;
        if (next != null) {
            nextAt = next.minAmount();
            gap = nextAt.subtract(perf);
            BigDecimal commissionAtNext = mode == SalaryTierMode.WHOLE
                    ? rateOf(nextAt, next.rateBp())
                    : marginal(nextAt, sorted);
            // 差额可能为 0（例如累进模式下多做的部分很少，取整后没变化），
            // 这时不下调成 null，如实返回 0.00，界面显示"还差 300 元业绩"即可。
            gain = commissionAtNext.subtract(commission);
            nextRate = next.rateBp();
        }

        return new SalaryBreakdown(
                perf, base, commission, base.add(commission), matched.rateBp(), mode,
                false, nextAt, gap, gain, nextRate);
    }

    /**
     * 按某一档比例计算提成金额。
     *
     * <p>先乘后除，且只用一次 {@link MoneyUtil#divide} 取整 —— 见类注释的"取整只做一次"。
     */
    private static BigDecimal rateOf(BigDecimal amount, int rateBp) {
        return MoneyUtil.divide(amount.multiply(BigDecimal.valueOf(rateBp)), FULL_BP);
    }

    /**
     * 累进计算：把业绩按档位切成若干段，每段按各自比例加权，最后一次性取整。
     *
     * <p>【为什么用"加权金额求和 + 最后除 10000"而不是每段先算钱再相加】
     * 每段先算钱就要每段取整，三段各舍 0.004 就可能差 1 分；
     * 先把 (金额 × 万分比) 这个整数级别的量加总，只除一次，误差只有一次。
     */
    private static BigDecimal marginal(BigDecimal perf, List<SalaryTierDef> tiers) {
        BigDecimal weighted = BigDecimal.ZERO;
        for (SalaryTierDef tier : tiers) {
            if (perf.compareTo(tier.minAmount()) <= 0) {
                break;
            }
            // 该段终点 = min(业绩, 档位上限)；无上限时终点就是业绩本身
            BigDecimal top = tier.upperOr(perf).min(perf);
            BigDecimal piece = top.subtract(tier.minAmount());
            if (piece.signum() > 0) {
                weighted = weighted.add(piece.multiply(BigDecimal.valueOf(tier.rateBp())));
            }
        }
        return weighted.divide(FULL_BP, MoneyUtil.SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 找命中的档位。
     *
     * <p>业绩超过最高档时，所有 {@code covers} 都会返回 false（因为最后一档通常无上限，
     * 但配置成有上限也是合法数据），此时兜底取最后一档，避免抛异常把工资算崩。
     */
    private static SalaryTierDef match(BigDecimal perf, List<SalaryTierDef> tiers) {
        for (SalaryTierDef tier : tiers) {
            if (tier.covers(perf)) {
                return tier;
            }
        }
        return tiers.get(tiers.size() - 1);
    }

    /**
     * 排序并校验阶梯完整性。
     *
     * <p>【为什么在这里校验而不是信任数据库】
     * 阶梯是主管通过接口改的，改错的可能性很实在：两档下限填成一样、
     * 把 20000~25000 改成 20000~21000 留下 21000~25000 的空洞。
     * 出现空洞时，落在空洞里的业绩会命中最后一档，工资直接翻档 ——
     * 这种错误在页面上完全看不出来，只能靠这里拦。
     */
    public static List<SalaryTierDef> normalize(List<SalaryTierDef> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            throw new IllegalArgumentException("工资阶梯为空，无法核算。请先配置 salary_tier");
        }
        List<SalaryTierDef> sorted = tiers.stream()
                .sorted(Comparator.comparing(SalaryTierDef::minAmount))
                .toList();

        // 第一档必须从 0 开始：否则 0~下限之间的业绩无档可套
        if (sorted.get(0).minAmount().signum() != 0) {
            throw new IllegalArgumentException(
                    "第一档的下限必须为 0，实际为 " + sorted.get(0).minAmount().toPlainString());
        }

        // 相邻档必须首尾相接：上一档上限 == 下一档下限，且最后一档无上限（或有上限但覆盖到顶）
        for (int i = 0; i < sorted.size() - 1; i++) {
            SalaryTierDef cur = sorted.get(i);
            SalaryTierDef nxt = sorted.get(i + 1);
            if (cur.maxAmount() == null) {
                throw new IllegalArgumentException(
                        "第 " + cur.levelNo() + " 档已声明为无上限，不能再有更高的档位");
            }
            if (cur.maxAmount().compareTo(nxt.minAmount()) != 0) {
                throw new IllegalArgumentException(String.format(
                        "阶梯存在断档或重叠：第 %d 档上限 %s，第 %d 档下限 %s",
                        cur.levelNo(), cur.maxAmount().toPlainString(),
                        nxt.levelNo(), nxt.minAmount().toPlainString()));
            }
        }
        return sorted;
    }
}
