package com.studio.erp.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 登记一笔回款并核销到本单。
 *
 * <p>【为什么"登记回款"和"核销到订单"合成一个动作】
 * 因为在小公司里它们本来就是同一件事：财务看到银行进账，就知道是哪张单的哪笔钱。
 * 拆成两步（先建回款单，再去核销台核销）只会多一个"忘了核销"的中间态，
 * 而那个状态下的钱既不算业绩也不显示在订单上，是最容易丢账的地方。
 * 月结大客户一笔款清多单的场景，走后续的"回款核销台"单独处理，不在这个接口里。
 */
public record OrderPaymentRequest(

        @NotNull(message = "回款金额不能为空")
        @DecimalMin(value = "0.01", message = "回款金额必须大于 0")
        BigDecimal amount,

        /** 到账日期，决定业绩算到哪个月；不传默认今天 */
        LocalDate payDate,

        /** 1=银行转账 2=现金 3=承兑 4=其他，不传默认 1 */
        Integer payMethod,

        /** 银行流水号，用于和财务流水核对 */
        String bankSerial,

        String remark) {
}
