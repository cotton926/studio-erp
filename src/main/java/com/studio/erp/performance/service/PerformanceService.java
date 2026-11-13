package com.studio.erp.performance.service;

import com.studio.erp.common.enums.LedgerType;
import com.studio.erp.common.enums.SourceType;
import com.studio.erp.common.exception.BizException;
import com.studio.erp.common.exception.ErrorCode;
import com.studio.erp.performance.domain.AssigneeShare;
import com.studio.erp.performance.domain.ItemShare;
import com.studio.erp.performance.domain.ItemType;
import com.studio.erp.performance.domain.PerfAllocation;
import com.studio.erp.performance.domain.PerformanceAllocator;
import com.studio.erp.performance.entity.PerformanceLedger;
import com.studio.erp.performance.mapper.PerfItemRow;
import com.studio.erp.performance.mapper.PerfQueryMapper;
import com.studio.erp.performance.mapper.PerformanceLedgerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业绩确认服务：把「一笔回款核销」翻译成「若干条业绩流水」。
 *
 * <p>【为什么这个类必须短】
 * 它承担的是一条业务规则：钱到了 → 业绩才算数。规则只有一个入口、一个事务、
 * 一份日志。任何额外的分支（发通知、改状态、刷缓存）都放在调用方或事件里，
 * 否则一个方法越写越长，最后没人敢改，改一次坏一次。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceService {

    private final PerfQueryMapper queryMapper;
    private final PerformanceLedgerMapper ledgerMapper;

    /**
     * 按核销单确认业绩。
     *
     * <p>【事务边界为什么卡在这里】
     * 业绩流水、收款计划已收金额、订单收款状态必须是同生共死的三件事。
     * 只提交一半的后果：钱记了、业绩没出，员工月底发现少钱；
     * 或者业绩出了、订单状态没变，看板显示"未收"但实际已收，报表全错。
     *
     * @param allocationId 核销记录ID（幂等键的组成部分）
     * @param orderId      订单ID
     * @param allocAmount  本次核销金额
     * @param payDate      回款到账日期 —— 决定业绩归属月
     * @param operatorId   操作人
     * @return 实际新增的流水条数（重复提交时返回 0）
     */
    @Transactional(rollbackFor = Exception.class)
    public int confirmByAllocation(Long allocationId,
                                   Long orderId,
                                   BigDecimal allocAmount,
                                   LocalDate payDate,
                                   Long operatorId) {

        // ---------- 1. 前置校验：核销总额不能超过订单总额 ----------
        // 这里先做一次快速判断给出友好提示；真正的并发防线是数据库行锁/乐观锁，
        // 两者都不能省 —— 只靠这一句 check，两个财务同时核销同一单仍会超收。
        //
        // 【注意 excludeId】调用方是"先插核销记录、再调这里"，所以本次核销已经在表里了。
        // 不排掉自己的话，本次金额会被算两遍（3 万的单收第一笔 2 万会被判成超收），
        // 所有正常回款的第一笔都会被拒。
        BigDecimal orderTotal = queryMapper.selectOrderTotal(orderId);
        BigDecimal allocated = queryMapper.selectAllocatedAmount(orderId, allocationId);
        BigDecimal afterThis = allocated.add(allocAmount);
        if (afterThis.compareTo(orderTotal) > 0) {
            throw new BizException(ErrorCode.ORDER_OVER_PAID, String.format(
                    "核销后将超收：订单总额 %s，已核销 %s，本次 %s。请先核对订单金额或核销明细。",
                    orderTotal.toPlainString(), allocated.toPlainString(), allocAmount.toPlainString()));
        }

        // ---------- 2. 组装领域对象 ----------
        List<ItemShare> items = assembleItems(queryMapper.selectItemRows(orderId));

        // ---------- 3. 纯函数分摊 ----------
        // 领域层抛的是 IllegalArgumentException/IllegalStateException（它不依赖框架，也不认识业务码），
        // 这里统一翻译成 BizException，否则会掉进全局兜底分支变成"系统开小差了"，
        // 而实际上这是用户能自己修的数据问题。
        List<PerfAllocation> allocations;
        try {
            allocations = PerformanceAllocator.allocate(allocAmount, items);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BizException(ErrorCode.RATIO_NOT_FULL, e.getMessage());
        }

        // ---------- 4. 落库（唯一索引兜底幂等） ----------
        String period = YearMonth.from(payDate).toString(); // 2026-11
        int inserted = 0;
        for (PerfAllocation a : allocations) {
            PerformanceLedger ledger = new PerformanceLedger();
            ledger.setEmployeeId(a.employeeId());
            ledger.setOrderId(orderId);
            ledger.setOrderItemId(a.itemId());
            ledger.setLedgerType(LedgerType.CONFIRM.getCode());
            ledger.setAmount(a.amount());
            ledger.setRatioBp(a.ratioBp());
            ledger.setPeriod(period);          // 业绩归属月 = 回款到账月，这是业务确认过的规则
            ledger.setOccurDate(payDate);
            ledger.setSourceType(SourceType.PAYMENT_ALLOCATION.getCode());
            ledger.setSourceId(allocationId);
            ledger.setIdemKey(buildIdemKey(allocationId, a.employeeId(), a.itemId()));
            ledger.setCreatedBy(operatorId);

            try {
                ledgerMapper.insert(ledger);
                inserted++;
            } catch (DuplicateKeyException e) {
                // 走到这里说明这条业绩已经生成过了（网络重试、用户连点、任务重跑）。
                // 为什么捕获而不是先 SELECT 判断：先查再插在并发下一定会漏，
                // 唯一索引是唯一可靠的判据。捕获后安静跳过，用户看到的结果依然正确。
                log.info("业绩流水已存在，跳过。idemKey={}", ledger.getIdemKey());
            }
        }

        log.info("业绩确认完成 orderId={} allocationId={} period={} 金额={} 新增流水={} 条",
                orderId, allocationId, period, allocAmount.toPlainString(), inserted);
        return inserted;
    }

    /**
     * 把扁平查询结果按明细行分组，还原成领域对象。
     *
     * <p>【为什么用 LinkedHashMap】
     * 明细行要按录入顺序（line_no）呈现给主管核对，HashMap 会打乱顺序。
     */
    private List<ItemShare> assembleItems(List<PerfItemRow> rows) {
        Map<Long, List<PerfItemRow>> grouped = new LinkedHashMap<>();
        for (PerfItemRow row : rows) {
            grouped.computeIfAbsent(row.itemId(), k -> new ArrayList<>()).add(row);
        }

        List<ItemShare> items = new ArrayList<>(grouped.size());
        for (Map.Entry<Long, List<PerfItemRow>> entry : grouped.entrySet()) {
            List<PerfItemRow> group = entry.getValue();
            PerfItemRow head = group.get(0);

            List<AssigneeShare> assignees = new ArrayList<>(group.size());
            for (PerfItemRow row : group) {
                // LEFT JOIN 会产生 employeeId 为 null 的行（该行还没派单），必须跳过，
                // 否则会构造出 employeeId=null 的非法对象，异常信息也对用户没有帮助。
                if (row.employeeId() == null) {
                    continue;
                }
                assignees.add(new AssigneeShare(row.employeeId(), row.ratioBp()));
            }

            items.add(new ItemShare(
                    head.itemId(),
                    head.itemName(),
                    head.amount(),
                    ItemType.of(head.itemType()),
                    assignees));
        }
        return items;
    }

    /**
     * 幂等键：类型 + 核销ID + 员工 + 明细行。
     *
     * <p>【为什么四段都要有】
     * 只有核销ID → 一个核销拆给 5 个人时，后 4 个人会被当成重复而丢失；
     * 只有员工ID → 同一个人在不同订单的业绩会互相顶掉。
     * 四段组合才能唯一标识"这一次、这一行、这个人"的一条业绩。
     */
    private String buildIdemKey(Long allocationId, Long employeeId, Long itemId) {
        return "CONFIRM:" + allocationId + ":" + employeeId + ":" + itemId;
    }
}
