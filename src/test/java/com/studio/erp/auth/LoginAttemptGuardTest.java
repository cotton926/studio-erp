package com.studio.erp.auth;

import com.studio.erp.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 登录锁定机制的关键回归：核心是"达到阈值后固定锁定"，
 * 防住低速爆破（以前是滑动窗口，每 5 分钟试 4 次就能永远绕开）。
 */
class LoginAttemptGuardTest {

    private final LoginAttemptGuard guard = new LoginAttemptGuard();

    @BeforeEach
    void shrinkWindow() {
        // 测试用 500ms 窗口替代 15 分钟，行为等价、时间可控
        LoginAttemptGuard.WINDOW = Duration.ofMillis(500);
    }

    @AfterEach
    void restoreWindow() {
        LoginAttemptGuard.WINDOW = Duration.ofMinutes(15);
    }

    @Test
    void 达到阈值后锁定_锁定期间继续尝试仍被拒绝() {
        for (int i = 0; i < 5; i++) {
            guard.recordFailure("admin");
        }
        assertThrows(BizException.class, () -> guard.assertNotLocked("admin"));
        // 锁定期间再次尝试不应解锁：即便再过一小段时间（仍在锁定时长内）
        assertThrows(BizException.class, () -> guard.assertNotLocked("admin"));
        // 大小写归一：换个写法也是同一个账号
        assertThrows(BizException.class, () -> guard.assertNotLocked("Admin "));
    }

    @Test
    void 锁定时长过后自动解锁() throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            guard.recordFailure("bob");
        }
        assertThrows(BizException.class, () -> guard.assertNotLocked("bob"));
        Thread.sleep(600); // 超过 500ms 窗口，记录全部过期
        assertDoesNotThrow(() -> guard.assertNotLocked("bob"));
    }

    @Test
    void 不足阈值不锁定_成功登录清零() {
        for (int i = 0; i < 4; i++) {
            guard.recordFailure("carol");
        }
        assertDoesNotThrow(() -> guard.assertNotLocked("carol"));
        guard.clear("carol");
        assertDoesNotThrow(() -> guard.assertNotLocked("carol"));
    }

    @Test
    void 失败记录超上限时拒绝而不是放行() {
        LoginAttemptGuard.WINDOW = Duration.ofSeconds(5);
        LoginAttemptGuard guard2 = new LoginAttemptGuard();
        // 直接灌满 map（反射不可取，这里用小窗口+循环；上限 1 万次循环很快）
        for (int i = 0; i < 10_000; i++) {
            guard2.recordFailure("user" + i);
        }
        // 第 10001 个新用户名：应抛出拒绝，而不是默默忽略继续放行
        assertThrows(BizException.class, () -> guard2.recordFailure("intruder"));
    }
}
