package com.studio.erp.receivable.dto;

import java.math.BigDecimal;

/**
 * 催收页顶部概览。
 *
 * <p>【为什么把"催收窗口"放进返回值，而不是让前端自己算月末】
 * 前端算月末要处理时区、闰年、以及"窗口天数"这个配置项；
 * 一旦配置改成 5 天而前端还按 3 天算，页面提示就会和列表对不上。
 * 规则只有后端一份，前端只负责显示。
 *
 * @param windowText      窗口区间文案，如 "2026-11-28 ~ 2026-11-30"
 * @param windowActive    今天是否已在窗口内（决定顶部是否高亮提醒）
 * @param daysToWindow    距窗口开始还有几天，0=已进入，负数=本月窗口已过
 * @param totalUnpaid     全部未收讫计划的金额合计
 * @param windowUnpaid    本期窗口内到期的未收金额
 * @param windowCount     本期窗口内到期的期数
 * @param overdueUnpaid   已逾期未收金额
 * @param overdueCount    已逾期期数
 * @param monthCollected  本月实际到账（含订金与尾款）
 * @param promisedCount   有客户承诺且承诺日未到的期数（预计会回款）
 * @param remindedThisMonth 本月已发起的催收次数
 */
public record CollectionSummaryVO(
        String windowText,
        boolean windowActive,
        long daysToWindow,
        BigDecimal totalUnpaid,
        BigDecimal windowUnpaid,
        int windowCount,
        BigDecimal overdueUnpaid,
        int overdueCount,
        BigDecimal monthCollected,
        int promisedCount,
        int remindedThisMonth) {
}
