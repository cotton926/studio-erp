package com.studio.erp.performance.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 参与业绩分摊的一条订单明细行（只读视图，不是数据库实体）。
 *
 * <p>【为什么用独立的领域对象而不是直接传 Entity】
 * 分摊算法是纯函数：给同样的输入必然得出同样的结果。若直接传 MyBatis 实体，
 * 算法就被数据库结构绑死，将来加字段、改类型都会连带改算法，也没法脱离数据库做单元测试。
 *
 * <p>【作用】
 * 只携带分摊所需的最小信息：金额、类型、以及这行上的人与比例。
 *
 * @param itemId    明细行ID
 * @param itemName  品项名（仅用于错误提示与日志定位）
 * @param amount    该行金额（已含税、已确认）
 * @param itemType  行类型，决定是否参与分摊
 * @param assignees 分成人列表；单人单场景就是一条 ratioBp=10000 的记录
 */
public record ItemShare(
        Long itemId,
        String itemName,
        BigDecimal amount,
        ItemType itemType,
        List<AssigneeShare> assignees) {

    public ItemShare {
        if (itemId == null) {
            throw new IllegalArgumentException("明细行ID不能为空");
        }
        if (amount == null) {
            throw new IllegalArgumentException("明细行金额不能为空，行: " + itemId);
        }
        if (itemType == null) {
            throw new IllegalArgumentException("明细行类型不能为空，行: " + itemId);
        }
        // 防御性拷贝：record 虽然是不可变的，但 List 本身可变，
        // 外部拿到引用后还能 add/clear，会导致"计算完比例又变了"的诡异问题。
        assignees = assignees == null ? List.of() : List.copyOf(assignees);
    }

    /** 该行是否参与业绩分摊：类型允许 且 金额大于 0 */
    public boolean perfEligible() {
        return itemType.isPerfEligible() && amount.signum() > 0;
    }

    /** 该行分成比例合计（万分比） */
    public int totalRatioBp() {
        return assignees.stream().mapToInt(AssigneeShare::ratioBp).sum();
    }

    /**
     * 校验该行分成比例是否正好 100%。
     *
     * <p>【为什么必须校验】
     * 比例不足 100% 会导致订单业绩发不完（剩余部分凭空消失），
     * 超过 100% 会导致个人业绩之和大于订单金额（公司记账对不上）。
     *
     * <p>【什么时候校验】
     * 在分摊前统一校验，而不是边算边校验 —— 提前失败，避免写了一半流水才发现数据有问题。
     */
    public void assertRatioFull() {
        if (!perfEligible()) {
            return;
        }
        if (assignees.isEmpty()) {
            throw new IllegalStateException(
                    "明细行【" + itemName + "】没有指定设计师，无法产生业绩，请先在派单页补充");
        }
        int total = totalRatioBp();
        if (!RatioUtil.isFull(total)) {
            throw new IllegalStateException(String.format(
                    "明细行【%s】分成比例合计为 %s，必须等于 100%%，请调整后再确认订单",
                    itemName, RatioUtil.bpToPercentText(total)));
        }
    }
}
