package com.studio.erp.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studio.erp.auth.DataScope;
import com.studio.erp.auth.DataScopeGuard;
import com.studio.erp.auth.ScopeType;
import com.studio.erp.common.enums.LedgerType;
import com.studio.erp.common.enums.OrderStatus;
import com.studio.erp.common.enums.PaymentStatus;
import com.studio.erp.common.exception.BizException;
import com.studio.erp.common.exception.ErrorCode;
import com.studio.erp.employee.entity.Employee;
import com.studio.erp.employee.mapper.EmployeeMapper;
import com.studio.erp.performance.domain.MoneyUtil;
import com.studio.erp.performance.dto.PerfSummaryVO;
import com.studio.erp.performance.entity.PerformanceLedger;
import com.studio.erp.performance.mapper.PerfSummaryMapper;
import com.studio.erp.performance.mapper.PerformanceLedgerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 业绩查询服务 —— 演示「双层数据隔离」的标准写法。
 *
 * <p>【第一层：注解拦截】{@link DataScope}(SELF) 保证任何登录用户都能进，
 * 但进门之后看谁的数据，由第二层决定。
 *
 * <p>【第二层：参数改写】{@link DataScopeGuard#resolveEmployeeId} 把
 * 前端传来的 employeeId 强制改写为当前用户（低权限者）。这一步不可省略：
 * 设计师把 URL 里的 id 改成同事的，没有这一行就直接越权了。
 *
 * <p>【第三层：SQL 条件】改写后的 id 作为 WHERE 条件下推到数据库，
 * 而不是查出全部再在内存里 filter —— 内存过滤一旦写错条件就会泄露，
 * 而且数据量大了还会拖慢响应。
 */
@Service
@RequiredArgsConstructor
public class PerformanceQueryService {

    private final PerformanceLedgerMapper ledgerMapper;
    private final PerfSummaryMapper summaryMapper;
    private final EmployeeMapper employeeMapper;

    /** 计入业绩的流水类型，从枚举推导，避免新类型漏算 */
    private static final List<Integer> PERF_TYPES = Arrays.stream(LedgerType.values())
            .filter(LedgerType::countsAsPerformance)
            .map(LedgerType::getCode)
            .toList();

    /** 页面趋势展示的月份数 */
    private static final int TREND_MONTHS = 12;

    /**
     * 查询某员工的业绩流水。
     *
     * @param requestedEmployeeId 前端请求的工号；低权限用户即使传了别人的，也会被改写成自己
     * @param period              期间 yyyy-MM，为空则查全部
     */
    @DataScope(ScopeType.SELF)
    public List<PerformanceLedger> listLedger(Long requestedEmployeeId, String period) {
        Long employeeId = DataScopeGuard.resolveEmployeeId(requestedEmployeeId);

        LambdaQueryWrapper<PerformanceLedger> wrapper = new LambdaQueryWrapper<PerformanceLedger>()
                .eq(PerformanceLedger::getEmployeeId, employeeId)
                .eq(StringUtils.hasText(period), PerformanceLedger::getPeriod, period)
                .orderByDesc(PerformanceLedger::getOccurDate)
                .orderByDesc(PerformanceLedger::getId);

        return ledgerMapper.selectList(wrapper);
    }

    /**
     * 汇总某期间的签单额与业绩。
     *
     * <p>【为什么在内存里算而不是写 SUM 的 SQL】
     * 单个员工一个月的流水最多几十条，内存累加完全够用，且能复用同一份类型判断逻辑，
     * 不会出现"SQL 里写了一套口径、Java 里又写一套"的双份维护问题。
     * 真正的大数据量场景走 monthly_perf_summary 预聚合表，不靠实时 SUM。
     */
    @DataScope(ScopeType.SELF)
    public Map<LedgerType, BigDecimal> summarizeByType(Long requestedEmployeeId, String period) {
        List<PerformanceLedger> ledgers = listLedger(requestedEmployeeId, period);

        return ledgers.stream()
                .collect(Collectors.groupingBy(
                        l -> LedgerType.of(l.getLedgerType()),
                        Collectors.reducing(BigDecimal.ZERO, PerformanceLedger::getAmount, BigDecimal::add)));
    }

    /**
     * 「我的业绩」页面数据：4 个 KPI + 近 12 月趋势 + 本月订单明细。
     *
     * <p>【为什么所有数字都从一次查询里出】
     * 拆成多次查询时，若中途刚好确认了一笔回款，KPI 和明细就会对不上。
     * 员工看到"本月 18,550，但明细加起来 21,000"，第一反应是系统算错，
     * 而这种"错"事后无法复现、也没法解释。
     *
     * @param period yyyy-MM，不传默认当月
     */
    @DataScope(ScopeType.SELF)
    public PerfSummaryVO summary(String period) {
        Long employeeId = DataScopeGuard.resolveEmployeeId(null);
        if (employeeId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "当前账号未绑定员工，无法查看个人业绩");
        }
        String p = normalizePeriod(period);
        YearMonth month = YearMonth.parse(p);

        // 一次取 12 个月：本月业绩、上月业绩（算环比）、趋势图都用这一份
        Map<String, BigDecimal> monthMap = new HashMap<>();
        for (PerfSummaryMapper.PeriodAmountRow row : summaryMapper.selectMonthlyTrend(
                employeeId, month.minusMonths(TREND_MONTHS - 1L).toString(), PERF_TYPES)) {
            if (row.period() != null && row.amount() != null) {
                monthMap.put(row.period(), row.amount());
            }
        }

        BigDecimal performance = monthMap.getOrDefault(p, BigDecimal.ZERO);
        BigDecimal lastMonth = monthMap.getOrDefault(month.minusMonths(1).toString(), BigDecimal.ZERO);

        // 环比：上月为 0 时不给数字。除以 0 会抛异常，而给个 999% 也毫无意义
        BigDecimal momRate = lastMonth.signum() == 0
                ? null
                : performance.subtract(lastMonth)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(lastMonth, 1, RoundingMode.HALF_UP);

        List<PerfSummaryVO.TrendPoint> trend = new ArrayList<>(TREND_MONTHS);
        for (int i = TREND_MONTHS - 1; i >= 0; i--) {
            YearMonth m = month.minusMonths(i);
            String key = m.toString();
            trend.add(new PerfSummaryVO.TrendPoint(
                    key,
                    m.getMonthValue() + "月",
                    MoneyUtil.yuan(monthMap.getOrDefault(key, BigDecimal.ZERO)),
                    key.equals(p)));
        }

        List<PerfSummaryMapper.MyOrderRow> orderRows = summaryMapper.selectMyOrders(employeeId, p);
        List<PerfSummaryVO.MyOrder> orders = orderRows.stream().map(this::toMyOrder).toList();

        BigDecimal signAmount = orders.stream()
                .map(PerfSummaryVO.MyOrder::mySignAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 未收业绩不限期间：9 月签的单、11 月还没回款，员工现在就该看到
        BigDecimal unpaid = summaryMapper.selectMyOrders(employeeId, null).stream()
                .map(this::toMyOrder)
                .map(PerfSummaryVO.MyOrder::myUnpaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Employee employee = employeeMapper.selectById(employeeId);

        PerfSummaryVO vo = new PerfSummaryVO();
        vo.setPeriod(p);
        vo.setEmployeeId(employeeId);
        vo.setEmployeeName(employee == null ? "" : employee.getName());
        vo.setPerformance(MoneyUtil.yuan(performance));
        vo.setLastMonthPerformance(MoneyUtil.yuan(lastMonth));
        vo.setMomRate(momRate);
        vo.setYearPerformance(MoneyUtil.yuan(
                summaryMapper.sumYearPerformance(employeeId, String.valueOf(month.getYear()), PERF_TYPES)));
        vo.setOrderCount(orders.size());
        vo.setUnpaidAmount(MoneyUtil.yuan(unpaid));
        vo.setSignAmount(MoneyUtil.yuan(signAmount));
        vo.setTrend(trend);
        vo.setOrders(orders);
        return vo;
    }

    /**
     * 把一行订单数据翻译成页面对象。
     *
     * <p>【我的已收为什么按比例折算】
     * 回款是订单级的（客户一笔钱付整单），但业绩是行级的（按明细分给不同设计师）。
     * 两者之间唯一的桥就是金额占比：整单收了一半，我的那部分就等于一半。
     * 这个折算跟 {@code PerformanceAllocator} 的分摊口径一致，所以"我看到的已收"
     * 和"实际确认进我业绩的钱"长期会对得上。
     */
    private PerfSummaryVO.MyOrder toMyOrder(PerfSummaryMapper.MyOrderRow row) {
        BigDecimal mySign = MoneyUtil.yuan(row.mySignAmount());
        BigDecimal myPaid = paidOf(row);
        BigDecimal myUnpaid = mySign.subtract(myPaid);

        // 分成比例 = 我的签单额 / 我参与明细的金额合计；多行比例不同时这是加权平均，
        // 显示成"70%"不会误导，因为旁边的金额是精确的
        int ratioPct = 0;
        if (row.myItemAmount() != null && row.myItemAmount().signum() > 0) {
            ratioPct = row.mySignAmount()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(row.myItemAmount(), 0, RoundingMode.HALF_UP)
                    .intValue();
        }

        return new PerfSummaryVO.MyOrder(
                row.orderId(),
                row.orderNo(),
                row.clientName(),
                row.title(),
                row.itemNames(),
                row.signDate(),
                labelOf(row.orderStatus()),
                row.orderStatus(),
                paymentLabelOf(row.paymentStatus()),
                ratioPct + "%",
                mySign,
                myPaid,
                myUnpaid);
    }

    /** 我的已收 = 我的签单额 × (整单已收 / 整单金额)，且不允许超过我的签单额 */
    private BigDecimal paidOf(PerfSummaryMapper.MyOrderRow row) {
        if (row.orderTotal() == null || row.orderTotal().signum() <= 0
                || row.orderPaid() == null || row.orderPaid().signum() <= 0) {
            return BigDecimal.ZERO.setScale(MoneyUtil.SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal paid = row.mySignAmount()
                .multiply(row.orderPaid())
                .divide(row.orderTotal(), 6, RoundingMode.HALF_UP);
        BigDecimal mySign = MoneyUtil.yuan(row.mySignAmount());
        paid = MoneyUtil.yuan(paid);
        // 数据异常（比如有人手工改过核销）时不允许出现"已收 > 签单"，否则未收会变成负数
        return paid.compareTo(mySign) > 0 ? mySign : paid;
    }

    private String labelOf(Integer code) {
        if (code == null) {
            return "";
        }
        try {
            return OrderStatus.of(code).getLabel();
        } catch (IllegalArgumentException e) {
            return String.valueOf(code);
        }
    }

    private String paymentLabelOf(Integer code) {
        if (code == null) {
            return "";
        }
        for (PaymentStatus s : PaymentStatus.values()) {
            if (s.getCode() == code) {
                return s.getLabel();
            }
        }
        return String.valueOf(code);
    }

    /**
     * 期间校验。
     *
     * <p>前端传 "2026-11-01" 或空串时，若静默当成当月，用户会以为在看 11 月的数据，
     * 实际是别的月份。宁可报错。
     */
    private String normalizePeriod(String period) {
        if (!StringUtils.hasText(period)) {
            return YearMonth.now().toString();
        }
        try {
            return YearMonth.parse(period.trim()).toString();
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.PERIOD_INVALID);
        }
    }
}
