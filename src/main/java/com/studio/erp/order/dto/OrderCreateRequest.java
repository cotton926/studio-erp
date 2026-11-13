package com.studio.erp.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 客服提单请求。
 *
 * <p>【注意这里没有金额字段，这是刻意的】
 * 客服和客户谈好内容，但价格由主管录入 —— 需求里明确"单价通过客服跟客户沟通好之后由部门主管录入"。
 * 如果这里留一个可填的金额，客服就会顺手填一个，而那个数字往往是口头承诺的、
 * 没有算过成本的，最后变成业绩的基数。字段不存在，就没有填错的可能。
 *
 * <p>订单在提单后处于「待定价」状态，此时不产生任何业绩。
 */
public record OrderCreateRequest(

        @NotBlank(message = "客户名称不能为空")
        @Size(max = 60, message = "客户名称不能超过 60 个字")
        String clientName,

        @NotBlank(message = "项目名称不能为空")
        @Size(max = 120, message = "项目名称不能超过 120 个字")
        String title,

        @Size(max = 40, message = "品类不能超过 40 个字")
        String category,

        LocalDate expectDeliverDate,

        @Size(max = 500, message = "备注不能超过 500 个字")
        String remark) {
}
