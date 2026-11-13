package com.studio.erp.performance.domain;

import java.math.BigDecimal;

/**
 * 一条分摊结果：某员工在某明细行上应得的业绩金额。
 *
 * <p>【为什么不是直接返回 Map<员工ID, 金额>】
 * 因为一条业绩流水必须能追溯到"哪张单的哪一行"。若只返回员工合计，
 * 员工在明细里点开想问"这 4200 是怎么来的"，系统答不上来。
 * 这个对象最终会一对一映射成 performance_ledger 的一行。
 *
 * @param employeeId 员工ID
 * @param itemId     明细行ID（写入流水的 order_item_id）
 * @param itemName   品项名（仅日志与错误提示用）
 * @param amount     该员工在该行分得的业绩金额
 * @param ratioBp    分成比例快照，随流水一起落库，日后改比例不影响历史
 */
public record PerfAllocation(
        Long employeeId,
        Long itemId,
        String itemName,
        BigDecimal amount,
        int ratioBp) {
}
