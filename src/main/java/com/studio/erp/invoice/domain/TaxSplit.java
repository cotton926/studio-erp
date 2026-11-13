package com.studio.erp.invoice.domain;

import com.studio.erp.performance.domain.MoneyUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 含税金额的拆分（价内税）。
 *
 * <p>【为什么不能直接 金额 × 税率】
 * 发票上的"金额"是价税合计。客户开 10,000 的票、税率 6%，
 * 税额是 10,000 - 10,000/1.06 = 566.04，而不是 600。
 * 按 600 算，申报时不含税收入少了 33.96，税额多了 33.96 —— 一张票差几十块，
 * 一个月几十张，年度汇算就会被税务系统比对出来。
 *
 * <p>【为什么单独抽成值对象】这是纯计算，不依赖数据库，
 * 可以脱离 Spring 直接单测。钱的计算只有被反复测过才敢相信。
 */
public record TaxSplit(BigDecimal gross, BigDecimal net, BigDecimal tax) {

    public static TaxSplit of(BigDecimal grossAmount, BigDecimal rate) {
        BigDecimal gross = MoneyUtil.yuan(grossAmount);
        if (rate == null || rate.signum() <= 0) {
            // 免税或不填税率：全额视为不含税收入，税额 0
            return new TaxSplit(gross, gross, BigDecimal.ZERO.setScale(MoneyUtil.SCALE, RoundingMode.HALF_UP));
        }
        BigDecimal net = gross.divide(BigDecimal.ONE.add(rate), MoneyUtil.SCALE, RoundingMode.HALF_UP);
        // 税额用减法得出，保证 net + tax == gross 恒成立。
        // 若分别四舍五入 net 和 tax，可能出现差 1 分的情况，报表两端对不上
        return new TaxSplit(gross, net, gross.subtract(net));
    }
}
