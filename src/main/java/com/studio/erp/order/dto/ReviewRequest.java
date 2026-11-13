package com.studio.erp.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 审核稿件（录客户反馈）。
 *
 * <p>【为什么退回必须填原因】
 * 没有原因的退回，月底只能得出"这单改了三版"，得不出"为什么改"。
 * 而后者才是能拿来改流程的东西（是文案没定？还是客户审美反复？）。
 * 校验放在这里而不是前端：前端必填拦不住直接调接口的情况。
 *
 * @param result   2=客户通过 3=客户退回
 * @param feedback 退回原因 / 通过时的备注
 * @param reworkBy 实际返工人，不传则默认原稿人
 */
public record ReviewRequest(
        @NotNull(message = "审核结果不能为空")
        @Min(value = 2, message = "审核结果只能是 2（通过）或 3（退回）")
        @Max(value = 3, message = "审核结果只能是 2（通过）或 3（退回）")
        Integer result,

        @Size(max = 500, message = "反馈不能超过 500 字")
        String feedback,

        Long reworkBy) {
}
