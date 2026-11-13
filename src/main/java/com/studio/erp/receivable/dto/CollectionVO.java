package com.studio.erp.receivable.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 催收清单的一行。
 *
 * <p>【金额给了三组：本期、整单、未收】
 * 催收员打电话时最常被问"我们还欠你们多少"，答案是整单口径；
 * 但他今天要追的是"这一期"，所以两个数都得给，且必须在字段名上区分清楚
 * （planAmount vs orderTotal），否则前端随手拿错一个，沟通就出错。
 *
 * @param daysToDue    距应收日还有几天：正数=未到期，0=今天到期，负数=已逾期
 * @param overdueDays  已逾期天数（未逾期为 0）
 * @param inWindow     是否处于"月末最后三天"催收窗口内
 * @param promiseBroken 客户承诺过但没兑现（承诺日已过仍未收讫）
 */
public record CollectionVO(
        Long planId,
        Long orderId,
        String orderNo,
        String clientName,
        String title,
        Integer seq,
        String planName,
        BigDecimal planAmount,
        BigDecimal receivedAmount,
        BigDecimal unpaidAmount,
        LocalDate planDate,
        Integer planStatus,
        String planStatusText,
        long daysToDue,
        long overdueDays,
        boolean inWindow,
        int remindCount,
        LocalDateTime lastRemindAt,
        LocalDate promiseDate,
        BigDecimal promiseAmount,
        boolean promiseBroken,
        String csName,
        String csPhone,
        BigDecimal orderTotal,
        BigDecimal orderPaid) {
}
