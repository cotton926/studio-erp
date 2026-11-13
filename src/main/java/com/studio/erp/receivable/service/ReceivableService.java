package com.studio.erp.receivable.service;

import com.studio.erp.common.audit.AuditService;
import com.studio.erp.common.exception.BizException;
import com.studio.erp.common.exception.ErrorCode;
import com.studio.erp.order.entity.SalesOrder;
import com.studio.erp.order.mapper.SalesOrderMapper;
import com.studio.erp.performance.domain.MoneyUtil;
import com.studio.erp.performance.mapper.PerfQueryMapper;
import com.studio.erp.receivable.domain.CollectAction;
import com.studio.erp.receivable.domain.CollectResult;
import com.studio.erp.receivable.domain.CollectWindow;
import com.studio.erp.receivable.domain.PlanStatus;
import com.studio.erp.receivable.dto.CollectLogVO;
import com.studio.erp.receivable.dto.CollectRequest;
import com.studio.erp.receivable.dto.CollectionSummaryVO;
import com.studio.erp.receivable.dto.CollectionVO;
import com.studio.erp.receivable.dto.PlanBriefVO;
import com.studio.erp.receivable.entity.CollectionLog;
import com.studio.erp.receivable.entity.ReceivablePlan;
import com.studio.erp.receivable.mapper.CollectionLogMapper;
import com.studio.erp.receivable.mapper.CollectionLogRow;
import com.studio.erp.receivable.mapper.CollectionRow;
import com.studio.erp.receivable.mapper.ReceivablePlanMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 收款计划与月末催收。
 *
 * <p>【本类的核心业务规则】
 * <pre>
 * 订金 = 订单总额 × deposit-percent（默认 30%），应收日 = 签单日
 * 尾款 = 总额 - 订金（差额法，保证两期之和永远等于总额，不会因为四舍五入多出 1 分）
 * 尾款应收日 = 交付月的最后一天（未交付时为签单月的最后一天）
 * 催收窗口 = 每月最后 window-days 天（默认 3 天），到点集中催尾款
 * </pre>
 *
 * <p>【为什么尾款日按"交付月月末"而不是"签单日 + 30 天"】
 * 客户的心理预期是"东西交给我了，月底一起结"。按固定天数算，9 月 28 日签、
 * 10 月 20 日才交付的单，尾款应收日会早于交付日 —— 客户会觉得公司在提前要钱，
 * 沟通成本立刻上升。按交付月月末，逻辑上永远说得通。
 *
 * <p>【为什么 recalc 每次都全量重算，而不是"这次收了 5000 就加到某期上"】
 * 回款是订单级的，先冲哪一期是系统的约定（订金先收满）。累加在补录、冲销、
 * 并发登记三种场景下都会漂移，而重算永远收敛到同一个结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceivableService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MoneyUtil.SCALE, RoundingMode.HALF_UP);

    private static final int SEQ_DEPOSIT = 1;
    private static final int SEQ_TAIL = 2;

    private final ReceivablePlanMapper planMapper;
    private final CollectionLogMapper logMapper;
    private final SalesOrderMapper orderMapper;
    private final PerfQueryMapper perfQueryMapper;
    private final AuditService auditService;

    /** 订金占比（百分比）。可配，但只影响之后新定价的单 —— 已生成的计划行不会被动。 */
    @Value("${studio.receivable.deposit-percent:30}")
    private int depositPercent;

    /** 月末催收窗口天数，默认 3 天 */
    @Value("${studio.receivable.window-days:3}")
    private int windowDays;

    // =====================================================================
    // 一、计划生成与维护
    // =====================================================================

    /**
     * 为订单生成收款计划（订金 + 尾款）。
     *
     * <p>幂等：已有计划的订单直接返回。定价可能因为"退回重定"被调用两次，
     * 第二次若重复插入会撞 uk_order_seq，用户看到的是数据库报错 —— 这里挡住。
     */
    @Transactional(rollbackFor = Exception.class)
    public void ensurePlans(Long orderId) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null || order.getTotalAmount() == null || order.getTotalAmount().signum() <= 0) {
            return;
        }
        if (!planMapper.selectByOrder(orderId).isEmpty()) {
            return;
        }

        BigDecimal total = MoneyUtil.yuan(order.getTotalAmount());
        BigDecimal deposit = MoneyUtil.yuan(total.multiply(BigDecimal.valueOf(depositPercent))
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        // 差额法：尾款 = 总额 - 订金。用乘法算尾款在总额为奇数分时会出现 1 分钱缺口
        BigDecimal tail = total.subtract(deposit);

        LocalDate signDate = order.getSignDate() == null ? LocalDate.now() : order.getSignDate();
        LocalDate baseDate = order.getActualDeliverDate() == null ? signDate : order.getActualDeliverDate();
        LocalDate tailDate = YearMonth.from(baseDate).atEndOfMonth();

        try {
            insertPlan(orderId, SEQ_DEPOSIT, "订金", deposit, signDate, "按 " + depositPercent + "% 自动生成");
            if (tail.signum() > 0) {
                insertPlan(orderId, SEQ_TAIL, "尾款", tail, tailDate, "余款于交付月月末到期");
            }
        } catch (DuplicateKeyException e) {
            // 并发定价/重复提交：另一个事务已经插入了同一期，视为成功
            log.info("收款计划已存在，跳过生成 orderId={}", orderId);
            return;
        }

        // 存量已收（比如先收钱后定价的极端情况）也要立刻反映到计划行上
        recalc(orderId);
        auditService.event("RECEIVABLE", orderId, String.format("生成收款计划 订金%s@%s 尾款%s@%s",
                deposit.toPlainString(), signDate, tail.toPlainString(), tailDate));
        log.info("生成收款计划 orderId={} 订金={}@{} 尾款={}@{}",
                orderId, deposit.toPlainString(), signDate, tail.toPlainString(), tailDate);
    }

    private void insertPlan(Long orderId, int seq, String name, BigDecimal amount,
                            LocalDate planDate, String remark) {
        ReceivablePlan plan = new ReceivablePlan();
        plan.setOrderId(orderId);
        plan.setSeq(seq);
        plan.setPlanName(name);
        plan.setPlanAmount(amount);
        plan.setPlanDate(planDate);
        plan.setReceivedAmount(ZERO);
        plan.setStatus(PlanStatus.OPEN.getCode());
        plan.setRemindCount(0);
        plan.setRemark(remark);
        planMapper.insert(plan);
    }

    /**
     * 交付后把尾款应收日挪到交付月月末。
     *
     * <p>不挪会怎样：9 月签的单 11 月才交付，尾款应收日还停在 9-30，
     * 催收页从 10 月起就把它标成"逾期 60 天"，而客户其实连稿都没看到。
     */
    @Transactional(rollbackFor = Exception.class)
    public void onDelivered(Long orderId, LocalDate deliverDate) {
        if (deliverDate == null) {
            return;
        }
        LocalDate tailDate = YearMonth.from(deliverDate).atEndOfMonth();
        int moved = planMapper.moveTailDate(orderId, tailDate);
        if (moved > 0) {
            auditService.event("RECEIVABLE", orderId, "交付后尾款应收日调整为 " + tailDate);
        }
    }

    /**
     * 按订单实际回款重算各期已收金额与状态。
     *
     * <p>冲抵顺序固定为"按期次从前往后"：先收满订金，再冲尾款。
     * 客户打了一笔钱没说是哪一期，业务上默认先结最早的 —— 这与催收员的直觉一致。
     */
    @Transactional(rollbackFor = Exception.class)
    public void recalc(Long orderId) {
        BigDecimal paid = perfQueryMapper.selectAllocatedAmount(orderId, null);
        BigDecimal remaining = paid == null ? ZERO : MoneyUtil.yuan(paid);

        for (ReceivablePlan plan : planMapper.selectByOrder(orderId)) {
            BigDecimal planAmount = plan.getPlanAmount() == null ? ZERO : plan.getPlanAmount();
            BigDecimal received = remaining.min(planAmount).max(ZERO);
            remaining = remaining.subtract(received);
            PlanStatus status = PlanStatus.derive(received, planAmount);
            BigDecimal current = plan.getReceivedAmount() == null ? ZERO : plan.getReceivedAmount();
            // 只在真的变了才写：无谓的 UPDATE 会刷新 updated_at，让"最后变动时间"失去意义
            if (current.compareTo(received) != 0
                    || !java.util.Objects.equals(plan.getStatus(), status.getCode())) {
                planMapper.updateReceived(plan.getId(), received, status.getCode());
            }
        }
    }

    // =====================================================================
    // 二、催收清单和概览
    // =====================================================================

    /**
     * 催收清单。
     *
     * @param scope   WINDOW=本期窗口 / OVERDUE=已逾期 / ALL=全部未结清
     * @param keyword 订单号、客户、项目模糊匹配
     */
    public List<CollectionVO> collection(String scope, String keyword) {
        LocalDate today = LocalDate.now();
        CollectWindow win = CollectWindow.of(today, windowDays);
        List<CollectionRow> rows = planMapper.selectOpenPlans(normalizeScope(scope),
                StringUtils.hasText(keyword) ? keyword.trim() : null, today, win.start(), win.end());
        return rows.stream().map(r -> toVO(r, win, today)).toList();
    }

    private CollectionVO toVO(CollectionRow r, CollectWindow win, LocalDate today) {
        BigDecimal planAmount = r.getPlanAmount() == null ? ZERO : r.getPlanAmount();
        BigDecimal received = r.getReceivedAmount() == null ? ZERO : r.getReceivedAmount();
        long daysToDue = r.getPlanDate() == null ? 0 : ChronoUnit.DAYS.between(today, r.getPlanDate());
        boolean promiseBroken = r.getPromiseDate() != null && r.getPromiseDate().isBefore(today);

        return new CollectionVO(
                r.getPlanId(), r.getOrderId(), r.getOrderNo(), r.getClientName(), r.getTitle(),
                r.getSeq(), r.getPlanName(),
                planAmount, received, planAmount.subtract(received),
                r.getPlanDate(), r.getPlanStatus(), PlanStatus.of(r.getPlanStatus()).getLabel(),
                daysToDue, CollectWindow.overdueDays(r.getPlanDate(), today),
                win.contains(r.getPlanDate()),
                r.getRemindCount() == null ? 0 : r.getRemindCount(),
                r.getLastRemindAt(), r.getPromiseDate(), r.getPromiseAmount(), promiseBroken,
                r.getCsName(), r.getCsPhone(),
                r.getOrderTotal(), r.getOrderPaid());
    }

    /**
     * 顶部概览。
     *
     * <p>【为什么用全量 rows 在内存里算，而不是写四条 SUM SQL】
     * 未收讫的期次数量在几十到几百之间，内存遍历成本可以忽略；
     * 而四条 SQL 各写一遍"未收讫/未取消"的条件，只要有一条写漏，
     * 页面上的"待收 8 万"和列表加起来就不是 8 万，用户会立刻不信任这套数字。
     */
    public CollectionSummaryVO summary() {
        LocalDate today = LocalDate.now();
        CollectWindow win = CollectWindow.of(today, windowDays);
        List<CollectionRow> rows = planMapper.selectOpenPlans("ALL", null, today, win.start(), win.end());

        BigDecimal totalUnpaid = ZERO;
        BigDecimal windowUnpaid = ZERO;
        BigDecimal overdueUnpaid = ZERO;
        int windowCount = 0;
        int overdueCount = 0;
        int promised = 0;

        for (CollectionRow r : rows) {
            BigDecimal unpaid = (r.getPlanAmount() == null ? ZERO : r.getPlanAmount())
                    .subtract(r.getReceivedAmount() == null ? ZERO : r.getReceivedAmount());
            if (unpaid.signum() <= 0) {
                continue;
            }
            totalUnpaid = totalUnpaid.add(unpaid);
            if (win.contains(r.getPlanDate())) {
                windowUnpaid = windowUnpaid.add(unpaid);
                windowCount++;
            }
            if (r.getPlanDate() != null && r.getPlanDate().isBefore(today)) {
                overdueUnpaid = overdueUnpaid.add(unpaid);
                overdueCount++;
            }
            if (r.getPromiseDate() != null && !r.getPromiseDate().isBefore(today)) {
                promised++;
            }
        }

        LocalDate monthStart = today.withDayOfMonth(1);
        BigDecimal monthCollected = planMapper.sumPaidBetween(monthStart, today);
        LocalDateTime logFrom = monthStart.atStartOfDay();
        int reminded = planMapper.countCollectBetween(logFrom, logFrom.plusMonths(1));

        return new CollectionSummaryVO(
                win.text(), win.activeOn(today), win.daysUntil(today),
                totalUnpaid, windowUnpaid, windowCount,
                overdueUnpaid, overdueCount,
                MoneyUtil.yuan(monthCollected), promised, reminded);
    }

    // =====================================================================
    // 三、催收登记
    // =====================================================================

    /**
     * 登记一次催收。
     *
     * <p>【为什么"已付款"也允许登记一条催收】客户在电话里答应了当场转账，
     * 这次沟通本身是有效信息；钱到账后由财务登记回款，计划状态会自动变成已收讫。
     * 这里不直接改状态 —— 回款必须走核销，否则会出现"计划显示收了但钱没进账"。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long collect(Long planId, CollectRequest req) {
        ReceivablePlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BizException(ErrorCode.COLLECTION_PLAN_NOT_FOUND);
        }
        if (plan.getStatus() != null && plan.getStatus() == PlanStatus.PAID.getCode()) {
            throw new BizException(ErrorCode.COLLECTION_PLAN_PAID);
        }

        CollectAction action;
        CollectResult result;
        try {
            action = CollectAction.of(req.action());
            result = CollectResult.of(req.result());
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.COLLECTION_ACTION_INVALID);
        }

        CollectionLog row = new CollectionLog();
        row.setOrderId(plan.getOrderId());
        row.setPlanId(planId);
        row.setAction(action.getCode());
        row.setResult(result.getCode());
        row.setPromiseDate(req.promiseDate());
        row.setPromiseAmount(req.promiseAmount() == null ? null : MoneyUtil.yuan(req.promiseAmount()));
        row.setRemark(req.remark());
        row.setOperatorId(com.studio.erp.auth.CurrentUserHolder.require().employeeId());
        logMapper.insert(row);

        planMapper.markReminded(planId, row.getPromiseDate(), row.getPromiseAmount());

        auditService.event("RECEIVABLE", planId, String.format("催收 #%d %s/%s %s",
                (plan.getRemindCount() == null ? 0 : plan.getRemindCount()) + 1,
                action.getLabel(), result.getLabel(),
                row.getRemark() == null ? "" : row.getRemark()));
        log.info("催收登记 planId={} orderId={} 方式={} 结果={} 操作人={}",
                planId, plan.getOrderId(), action.getLabel(), result.getLabel(),
                row.getOperatorId());
        return row.getId();
    }

    /**
     * 某张订单的收款计划（订单详情抽屉用）。
     *
     * <p>只读，不触发补建：详情页是高频页面，不该因为"看一眼"就产生写操作。
     * 缺计划的历史单由管理员在催收页点"重建"补。
     */
    public List<PlanBriefVO> plans(Long orderId) {
        return planMapper.selectByOrder(orderId).stream()
                .map(p -> {
                    BigDecimal planAmount = p.getPlanAmount() == null ? ZERO : p.getPlanAmount();
                    BigDecimal received = p.getReceivedAmount() == null ? ZERO : p.getReceivedAmount();
                    return new PlanBriefVO(p.getId(), p.getSeq(), p.getPlanName(),
                            planAmount, received, planAmount.subtract(received),
                            p.getPlanDate(), p.getStatus(), PlanStatus.of(p.getStatus()).getLabel(),
                            p.getRemindCount() == null ? 0 : p.getRemindCount(),
                            p.getLastRemindAt(), p.getPromiseDate());
                })
                .toList();
    }

    /** 某张订单的催收历史 */
    public List<CollectLogVO> logs(Long orderId) {
        return logMapper.selectByOrder(orderId).stream()
                .map(r -> new CollectLogVO(
                        r.getId(), r.getOrderId(), r.getPlanId(),
                        r.getAction(), CollectAction.labelOf(r.getAction()),
                        r.getResult(), CollectResult.labelOf(r.getResult()),
                        r.getPromiseDate(), r.getPromiseAmount(), r.getRemark(),
                        r.getOperatorName() == null ? "系统" : r.getOperatorName(),
                        r.getCreatedAt()))
                .toList();
    }

    /**
     * 为历史订单一键补建计划。
     *
     * <p>【为什么需要这个入口】迁移已经回填过一次，但迁移之后新导入的历史数据、
     * 或者手工改过库的单，仍可能缺计划。给管理员一个幂等的重建按钮，
     * 比每次都要人肉写 SQL 可靠。
     */
    @Transactional(rollbackFor = Exception.class)
    public int rebuild() {
        List<Long> ids = planMapper.selectOrdersWithoutPlan();
        for (Long id : ids) {
            ensurePlans(id);
        }
        // 已有计划的行也要重算一次，修掉可能存在的历史偏差
        for (Long id : ids) {
            recalc(id);
        }
        log.info("重建收款计划：补建 {} 张订单", ids.size());
        return ids.size();
    }

    private String normalizeScope(String scope) {
        if (!StringUtils.hasText(scope)) {
            return "ALL";
        }
        String s = scope.trim().toUpperCase();
        return switch (s) {
            case "WINDOW", "OVERDUE", "ALL" -> s;
            // 传了不认识的值就当全部，而不是报错：催收页宁可多显示，不能白屏
            default -> "ALL";
        };
    }
}
