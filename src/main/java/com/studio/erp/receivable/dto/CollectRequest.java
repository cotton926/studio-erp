package com.studio.erp.receivable.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 登记一次催收。
 *
 * <p>【为什么备注不是必填】催收员在客户电话里被挂断，最不愿意做的就是再填一堆表单。
 * 强制写备注的结果是所有人都填"已联系"，反而污染数据。方式+结果两个下拉已经足够统计，
 * 备注只在有话说时才填。
 *
 * @param action        催收方式，见 CollectAction
 * @param result        催收结果，见 CollectResult
 * @param promiseDate   客户承诺回款日（选"承诺付款"时应填）
 * @param promiseAmount 客户承诺金额
 */
public record CollectRequest(
        Integer action,
        Integer result,
        LocalDate promiseDate,
        BigDecimal promiseAmount,
        String remark) {
}
