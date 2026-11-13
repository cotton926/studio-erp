package com.studio.erp.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.studio.erp.auth.service.AuthService;
import com.studio.erp.common.exception.BizException;
import com.studio.erp.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * 登录拦截器 —— 整个权限体系的入口。
 *
 * <p>【为什么用拦截器而不是过滤器】
 * 拦截器能拿到 HandlerMethod（将来要按方法上的注解做细粒度判断），
 * 且抛出的异常会被 @RestControllerAdvice 接住，转成统一的 JSON。
 * 过滤器在 Servlet 层，异常不会被 Spring MVC 的异常处理器接管，得自己写响应体。
 *
 * <p>【为什么拦截 /api/** 而不是拦截全部再放行静态资源】
 * 白名单策略（默认全拦，显式放行）比黑名单安全：新增接口忘了配也不会漏，
 * 最坏是多拦了一个本该公开的路径，表现为 401 而不是数据泄露。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    /**
     * 强制改密期间仍允许访问的接口。
     * 如果这里不放行，用户会被锁死：改密码接口本身也需要登录，
     * 而登录后又立刻被拦，形成死循环。
     */
    private static final Set<String> ALLOWED_WHEN_MUST_CHANGE_PWD = Set.of(
            "/api/auth/password",
            "/api/auth/logout",
            "/api/auth/me");

    private final AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 防御性清理：正常流程在 afterCompletion 里清，但若上一个请求的线程被
        // 异步任务占用、或某处提前抛异常绕过了 afterCompletion，这里兜住。
        // 不清就是"设计师看到主管数据"这类无法复现的事故源头。
        CurrentUserHolder.clear();

        StpUtil.checkLogin();
        long userId = StpUtil.getLoginIdAsLong();

        AuthService.LoginContext context = authService.loadContext(userId);

        // 强制改密判断放在 set 之前：这里抛异常时拦截器不会走 afterCompletion，
        // 若已经 set 过，ThreadLocal 就会残留到下一个请求。
        if (context.mustChangePwd() && !ALLOWED_WHEN_MUST_CHANGE_PWD.contains(request.getRequestURI())) {
            throw new BizException(ErrorCode.PASSWORD_EXPIRED);
        }

        CurrentUserHolder.set(context.user());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // Tomcat 线程复用，必须清。用 remove 而非 set(null)，否则 Entry 不释放会内存泄漏。
        CurrentUserHolder.clear();
    }
}
