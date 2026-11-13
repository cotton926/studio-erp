package com.studio.erp.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 主管定价请求（一次提交整单明细）。
 *
 * <p>【为什么整单一起提交，而不是逐行增删改】
 * 订单确认是一个原子动作：明细、分成、总额、状态必须一起生效。
 * 逐行保存会留下"明细改了一半就被确认"的中间态，而且期间任何人打开这个单看到的总额都不对。
 * 整单提交 = 一次校验、一次落库、一次确认。
 *
 * <p>【为什么金额不由前端传】
 * amount = qty × unitPrice 永远在服务端算。前端传金额就存在"传了个对不上的数"的可能，
 * 而这种错误在界面上完全看不出来，只有对账时才发现总额和明细加起来不等。
 */
public record OrderPricingRequest(

        /** 签单日期，不传默认今天；业绩的"签单月"按它算 */
        LocalDate signDate,

        @NotEmpty(message = "至少要有一条明细")
        @Size(max = 30, message = "一张订单最多 30 条明细")
        List<@Valid Item> items,

        @Size(max = 500, message = "备注不能超过 500 个字")
        String remark) {

    /**
     * 一条明细。
     *
     * @param itemName  品项名称（手填，如"画册设计 48P"）
     * @param spec      规格描述（可选）
     * @param unit      单位，默认"项"
     * @param qty       数量，支持小数
     * @param unitPrice 成交单价（主管手填）
     * @param itemType  1=正常 2=赠品 3=返工；不传按 1 处理
     * @param assignees 分成人；正常行必填且合计 100%，赠品/返工可不填
     */
    public record Item(
            @NotBlank(message = "品项名称不能为空")
            @Size(max = 80, message = "品项名称不能超过 80 个字")
            String itemName,

            @Size(max = 100, message = "规格不能超过 100 个字")
            String spec,

            @Size(max = 10, message = "单位不能超过 10 个字")
            String unit,

            @NotNull(message = "数量不能为空")
            @DecimalMin(value = "0.01", message = "数量必须大于 0")
            BigDecimal qty,

            @NotNull(message = "单价不能为空")
            @DecimalMin(value = "0", message = "单价不能为负数")
            BigDecimal unitPrice,

            Integer itemType,

            @Size(max = 500, message = "行备注不能超过 500 个字")
            String remark,

            @Valid
            List<Assignee> assignees) {
    }

    /**
     * 一个分成人。
     *
     * <p>ratioBp 是万分比：7000 = 70%。不用百分比小数，是因为"三人各 33.33%"这种事
     * 用小数永远凑不满 100%，而整数必须精确凑满，凑不满就当场报错，逼主管想清楚。
     */
    public record Assignee(
            @NotNull(message = "分成人不能为空")
            Long employeeId,

            @NotNull(message = "分成比例不能为空")
            @Min(value = 0, message = "分成比例不能为负")
            @Max(value = 10000, message = "分成比例不能超过 100%")
            Integer ratioBp) {
    }
}
