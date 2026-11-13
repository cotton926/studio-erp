package com.studio.erp.auth;

import com.studio.erp.common.exception.BizException;
import com.studio.erp.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败次数限制。
 *
 * <p>【为什么必须做】
 * 没有它，login 接口就是一个可以无限次尝试的密码预言机，常见口令字典几分钟就能撞开。
 *
 * <p>【为什么按用户名计数，不按 IP】
 * 设计公司全公司出口往往是同一个 IP，按 IP 锁定等于一个人输错三次、
 * 整个公司都登不上 —— 这是比不锁更糟的运维事故。
 * 按用户名计数则精确命中被攻击的账号。
 * 代价是攻击者可以拿大量不存在的用户名来撑爆内存，所以下面做了两件事：
 * 1) 定期清理过期记录；2) map 超过上限时直接拒绝（宁可拒绝服务，不可 OOM）。
 *
 * <p>【为什么是"固定锁定时长"而不是"滑动窗口"】
 * 纯滑动窗口有个致命弱点：攻击者只要控制节奏（每 5 分钟试 4 次），
 * 永远凑不满阈值，可以以 48 次/小时的速度无限爆破。
 * 现在的规则是：窗口内失败次数达到阈值后，从最后一次失败起固定锁 15 分钟；
 * 锁定期间直接拒绝、连密码都不校验，尝试也不会刷新计时 —— 节奏再慢也逃不掉。
 *
 * <p>【为什么用内存而不是 Redis】
 * 项目当前是单机部署，内存够用且零依赖。一旦扩到多实例，
 * 各实例的计数互不相通，攻击者可以轮着打 —— 那时必须换成 Redis（INCR + EXPIRE）。
 * 这个前提写在注释里，避免后人误以为它是分布式安全的。
 */
@Slf4j
@Component
public class LoginAttemptGuard {

    /** 15 分钟内连续失败 5 次即锁定 */
    private static final int MAX_ATTEMPTS = 5;
    /** 失败记录的保留窗口，同时是锁定时长：达到阈值后从最后一次失败起锁这么久。包级可见仅供测试缩短 */
    static Duration WINDOW = Duration.ofMinutes(15);

    /** 保护内存：正常系统不会有上万个不同用户名来登录，超过说明有人在刷 */
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    /** 登录前调用：已锁定则直接拒绝，连密码都不校验（省掉一次 BCrypt 计算） */
    public void assertNotLocked(String username) {
        Deque<Instant> deque = failures.get(key(username));
        if (deque == null) {
            return;
        }
        synchronized (deque) {
            evictExpired(deque);
            if (deque.size() >= MAX_ATTEMPTS) {
                // 从"最后一次失败"起算剩余锁定时间；锁定期间拒绝不计数，计时不会被刷新
                long remainingMs = deque.peekLast().plus(WINDOW).toEpochMilli()
                        - System.currentTimeMillis();
                long remainingMin = Math.max(1, remainingMs / 60_000);
                log.warn("账号 {} 登录失败次数超限，剩余锁定约 {} 分钟", username, remainingMin);
                throw new BizException(ErrorCode.LOGIN_LOCKED);
            }
        }
    }

    /** 登录失败时调用 */
    public void recordFailure(String username) {
        String key = key(username);
        if (failures.size() >= MAX_TRACKED_KEYS && !failures.containsKey(key)) {
            // 已经有人在用不存在的用户名刷接口：此时记录新 key 只会无限吃内存，
            // 但"默默忽略"等于对刷接口的人不设防 —— 直接拒绝，宁可误伤不放过
            log.warn("登录失败记录已达上限 {}，拒绝来自未跟踪用户名 {} 的尝试", MAX_TRACKED_KEYS, username);
            throw new BizException(ErrorCode.LOGIN_LOCKED);
        }
        Deque<Instant> deque = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            evictExpired(deque);
            deque.addLast(Instant.now());
        }
    }

    /** 登录成功时清空，避免"错两次后终于对了，但计数一直挂着" */
    public void clear(String username) {
        failures.remove(key(username));
    }

    /**
     * 每 10 分钟扫一遍，把窗口内没有任何失败记录的 key 删掉。
     * 不做这一步，map 会随"曾经登录失败过的人"永久增长。
     */
    @Scheduled(fixedDelay = 600_000L)
    public void purgeExpired() {
        failures.entrySet().removeIf(entry -> {
            Deque<Instant> deque = entry.getValue();
            synchronized (deque) {
                evictExpired(deque);
                return deque.isEmpty();
            }
        });
    }

    private void evictExpired(Deque<Instant> deque) {
        Instant deadline = Instant.now().minus(WINDOW);
        while (!deque.isEmpty() && deque.peekFirst().isBefore(deadline)) {
            deque.pollFirst();
        }
    }

    /** 统一小写：避免 Admin / admin 被当成两个账号分别计数 */
    private String key(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
