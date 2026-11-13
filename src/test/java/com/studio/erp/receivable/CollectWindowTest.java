package com.studio.erp.receivable;

import com.studio.erp.receivable.domain.CollectWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 月末催收窗口的规则测试。
 *
 * <p>【为什么这几条必须测】
 * "每月最后三天"听起来简单，但有三个坑：2 月的天数、闰年、窗口跨年（12 月）。
 * 这类规则一旦写错，表现是"某个月的尾款没人催"，而没人会主动发现 ——
 * 只有等到月底没收到钱才追查，那时已经迟了。
 */
@DisplayName("月末催收窗口")
class CollectWindowTest {

    @Test
    @DisplayName("普通月份：最后三天是 28-30 号")
    void normalMonth() {
        CollectWindow win = CollectWindow.of(YearMonth.of(2026, 11), 3);
        assertEquals(LocalDate.of(2026, 11, 28), win.start());
        assertEquals(LocalDate.of(2026, 11, 30), win.end());
    }

    @Test
    @DisplayName("2 月：按月末反推，不能写死 28-30")
    void february() {
        CollectWindow normal = CollectWindow.of(YearMonth.of(2026, 2), 3);
        assertEquals(LocalDate.of(2026, 2, 26), normal.start());
        assertEquals(LocalDate.of(2026, 2, 28), normal.end());

        // 2028 是闰年，2 月有 29 日
        CollectWindow leap = CollectWindow.of(YearMonth.of(2028, 2), 3);
        assertEquals(LocalDate.of(2028, 2, 27), leap.start());
        assertEquals(LocalDate.of(2028, 2, 29), leap.end());
    }

    @Test
    @DisplayName("12 月：窗口收在年末，不跨到次年 1 月")
    void december() {
        CollectWindow win = CollectWindow.of(YearMonth.of(2026, 12), 3);
        assertEquals(LocalDate.of(2026, 12, 29), win.start());
        assertEquals(LocalDate.of(2026, 12, 31), win.end());
        assertFalse(win.contains(LocalDate.of(2027, 1, 1)));
    }

    @Test
    @DisplayName("窗口天数是配置项：改成 5 天就是最后五天")
    void configurableDays() {
        CollectWindow win = CollectWindow.of(YearMonth.of(2026, 11), 5);
        assertEquals(LocalDate.of(2026, 11, 26), win.start());
    }

    @Test
    @DisplayName("天数配错（0 或 30）时收敛到合法区间，不会把整月都算成催收期")
    void clampDays() {
        assertEquals(1, CollectWindow.of(YearMonth.of(2026, 11), 0).days());
        // 上限 15 天：避免有人配 30 之后，页面天天提示"该催款了"
        assertEquals(15, CollectWindow.of(YearMonth.of(2026, 11), 30).days());
    }

    @Test
    @DisplayName("contains / activeOn / daysUntil 的口径一致")
    void boundaries() {
        CollectWindow win = CollectWindow.of(LocalDate.of(2026, 11, 13), 3);
        assertTrue(win.activeOn(LocalDate.of(2026, 11, 28)));
        assertTrue(win.activeOn(LocalDate.of(2026, 11, 30)));
        assertFalse(win.activeOn(LocalDate.of(2026, 11, 27)));
        assertFalse(win.activeOn(LocalDate.of(2026, 12, 1)));

        assertEquals(15, win.daysUntil(LocalDate.of(2026, 11, 13)));
        // 已进入窗口返回 0，而不是负数 —— 前端用它决定"今天就要动手"
        assertEquals(0, win.daysUntil(LocalDate.of(2026, 11, 29)));
        // 窗口已过：距下一个窗口开始是负数，这里正好是"离 11-28 已经过去 3 天"
        assertEquals(-3, win.daysUntil(LocalDate.of(2026, 12, 1)));
    }

    @Test
    @DisplayName("逾期天数只算已过期，未到期一律为 0")
    void overdue() {
        LocalDate today = LocalDate.of(2026, 11, 13);
        assertEquals(13, CollectWindow.overdueDays(LocalDate.of(2026, 10, 31), today));
        assertEquals(0, CollectWindow.overdueDays(LocalDate.of(2026, 11, 13), today));
        assertEquals(0, CollectWindow.overdueDays(LocalDate.of(2026, 11, 30), today));
        assertEquals(0, CollectWindow.overdueDays(null, today));
    }
}
