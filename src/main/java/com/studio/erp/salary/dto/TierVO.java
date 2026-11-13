package com.studio.erp.salary.dto;

import com.studio.erp.performance.domain.RatioUtil;
import com.studio.erp.salary.entity.SalaryTier;

import java.math.BigDecimal;

/**
 * 档位展示对象。
 *
 * <p>额外返回 {@code rateText}（"45%"）而不是让前端自己拿 4500 去除 100：
 * 万分比转百分比这件事只应该有一个实现，放在服务端就不会出现
 * 某个页面除以 100、另一个页面除以 1000 的问题。
 */
public record TierVO(
        Long id,
        Integer levelNo,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        Integer rateBp,
        String rateText,
        String remark) {

    public static TierVO of(SalaryTier t) {
        return new TierVO(
                t.getId(),
                t.getLevelNo(),
                t.getMinAmount(),
                t.getMaxAmount(),
                t.getRateBp(),
                RatioUtil.bpToPercentText(t.getRateBp()),
                t.getRemark());
    }
}
