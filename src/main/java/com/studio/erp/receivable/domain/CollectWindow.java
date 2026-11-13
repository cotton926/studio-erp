package com.studio.erp.receivable.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * 月末催收窗口 —— 「每月最后 N 天集中催尾款」这条规则的唯一实现。
 *
 * <p>【为什么单独抽成一个值对象】
 * 这条规则会在三处被用到：催收页的"本期窗口"筛选、页面顶部的提醒条、定时任务的判断。
 * 如果每处各自写 `month.atEndOfMonth().minusDays(2)`，只要有一处写成 `minusDays(3)`，
 * 就会出现"页面说今天在窗口内、列表却是空的"——这种 bug 很难被发现，
 * 而且每次都要翻三个文件对照。收敛成一个对象，规则只有一份。
 *
 * <p>【窗口为什么是"月末往前数 N 天"而不是"每月 28-30 号"】
 * 2 月没有 30 号。写死日期在 2 月会永久漏催一批尾款，且没人会记得这个例外。
 * 用月末反推，任何月份都成立（闰年 2 月自动是 27-29）。
 */
public record CollectWindow(LocalDate start, LocalDate end, int days) {

    /** 窗口最多占半个月：配置写错（比如填 30）时不至于把整月都算成"催收期"，否则提醒失去意义 */
    private static final int MAX_DAYS = 15;

    public static CollectWindow of(YearMonth month, int windowDays) {
        int d = Math.max(1, Math.min(windowDays, MAX_DAYS));
        LocalDate end = month.atEndOfMonth();
        return new CollectWindow(end.minusDays(d - 1L), end, d);
    }

    public static CollectWindow of(LocalDate day, int windowDays) {
        return of(YearMonth.from(day), windowDays);
    }

    public boolean contains(LocalDate day) {
        return day != null && !day.isBefore(start) && !day.isAfter(end);
    }

    /** 今天是否处于窗口内（页面顶部是否要弹"该催尾款了"） */
    public boolean activeOn(LocalDate today) {
        return contains(today);
    }

    /**
     * 距离窗口开始还有几天；已进入窗口返回 0，窗口已过返回负数。
     *
     * <p>页面上用来显示"还有 3 天进入催收期"，比只给一个日期更直接。
     */
    public long daysUntil(LocalDate today) {
        if (contains(today)) {
            return 0;
        }
        return ChronoUnit.DAYS.between(today, start);
    }

    public String text() {
        return start + " ~ " + end;
    }

    /**
     * 逾期天数：应收日已过而未收讫的天数，未到期返回 0。
     *
     * <p>用"今天 - 应收日"而不是"窗口结束 - 应收日"：客户拖了 40 天这件事，
     * 和有没有进入催收窗口无关，逾期就是逾期。
     */
    public static long overdueDays(LocalDate planDate, LocalDate today) {
        if (planDate == null || today == null) {
            return 0;
        }
        long d = ChronoUnit.DAYS.between(planDate, today);
        return Math.max(d, 0);
    }
}
