package com.studio.erp.performance.domain;

/**
 * 比例工具。
 *
 * <p>【为什么单独抽出来】
 * "一万等于百分之百"这个约定必须只有一处定义，否则有人写 100、有人写 10000，
 * 校验就会形同虚设。
 */
public final class RatioUtil {

    /** 100% 对应的万分比数值 */
    public static final int FULL_BP = 10000;

    private RatioUtil() {
    }

    /** 百分比整数 → 万分比。70 → 7000 */
    public static int percentToBp(int percent) {
        return percent * 100;
    }

    /** 万分比 → 百分比展示字符串。7000 → "70%"，7050 → "70.5%" */
    public static String bpToPercentText(int bp) {
        String text = String.format("%.2f", bp / 100.0);
        // 去掉无意义的尾零，70.00% 显示成 70%，70.50% 显示成 70.5%
        text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
        return text + "%";
    }

    /**
     * 校验一组比例合计是否正好 100%。
     *
     * <p>【为什么不在这里抛异常而返回 boolean】
     * 调用方需要给出更具体的错误提示（"第 2 行分成比例合计为 90%，请调整"），
     * 异常信息由调用方拼装，这里只做判定。
     *
     * <p>【会出现什么问题】
     * 若某行没有分成人（list 为空），合计为 0，此方法返回 false —— 这是对的，
     * 因为"没派单"的订单不该产生业绩，更不该通过校验被确认。
     */
    public static boolean isFull(int totalBp) {
        return totalBp == FULL_BP;
    }
}
