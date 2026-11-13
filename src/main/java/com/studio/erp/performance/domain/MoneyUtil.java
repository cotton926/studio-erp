package com.studio.erp.performance.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额计算工具。
 *
 * <p>【为什么需要这个类】
 * 项目里所有金额计算都必须经过这里，不允许业务代码直接调 {@code a.multiply(b)}，
 * 因为直接调用会保留满精度（如 33.3333333），一旦写库或比较就会出错。
 *
 * <p>【作用】
 * 统一把金额收敛到 2 位小数（分），舍入规则固定为 HALF_UP（四舍五入）。
 *
 * <p>【不这么做会出什么问题】
 * 1) 不同地方用不同 RoundingMode，A 处 HALF_UP、B 处 HALF_DOWN，汇总时对不上；
 * 2) 忘记 setScale 直接把 3 位小数的值写进 DECIMAL(12,2)，MySQL 会静默截断（不是四舍五入），
 *    导致个人业绩之和比订单少几分钱，且极难定位。
 *
 * <p>【怎么解决】
 * 收敛入口只有这一个类，代码评审时看到裸的 BigDecimal.multiply 就打回。
 */
public final class MoneyUtil {

    /** 金额保留位数，与数据库 DECIMAL(12,2) 严格对应 */
    public static final int SCALE = 2;

    private MoneyUtil() {
    }

    /** 把任意精度收敛到分；null 视为 0，避免调用方到处写判空 */
    public static BigDecimal yuan(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** 乘法并收敛到分，用于「金额 × 比例」和「数量 × 单价」 */
    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        BigDecimal left = a == null ? BigDecimal.ZERO : a;
        BigDecimal right = b == null ? BigDecimal.ZERO : b;
        return yuan(left.multiply(right));
    }

    /**
     * 除法并收敛到分，用于按占比分摊。
     *
     * <p>【为什么一步就到 2 位，而不是先保留 6 位再取 2 位】
     * 两步取整会产生"双重舍入"误差：真实值 0.0049995，先舍到 6 位得 0.005000，
     * 再舍到 2 位得 0.01；而直接舍到 2 位应该是 0.00。差 1 分钱看起来不多，
     * 但如果每笔回款都往一个方向偏，一年下来账就对不上，且极难复现。
     * 直接按目标精度一次取整，是唯一不会有歧义的做法。
     */
    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        if (divisor == null || divisor.signum() == 0) {
            throw new ArithmeticException("分摊除数为 0，说明订单业绩基数为空，属于脏数据");
        }
        return dividend.divide(divisor, SCALE, RoundingMode.HALF_UP);
    }
}
