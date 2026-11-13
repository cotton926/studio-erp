package com.studio.erp.auth;

import com.studio.erp.common.exception.BizException;
import com.studio.erp.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * {@link DataScope} 切面：进接口前先校验身份与数据范围。
 *
 * <p>【为什么在切面里做，而不是每个 Controller 开头写一行】
 * 写在方法里的校验，只要有人新加接口时忘了抄，就是一条越权通道。
 * 切面由注解驱动，忘了标注解会表现为"没有校验"，但代码评审时
 * 一眼就能看到 Controller 上缺了 {@code @DataScope} —— 这比藏在方法体里好发现得多。
 *
 * <p>【顺序问题】
 * 这个切面依赖 CurrentUserHolder 已经被填充。也就是说登录拦截器必须先执行。
 * 若 Spring 的切面顺序导致这里拿不到用户，会抛 401 而不是静默放行 ——
 * 宁可报错，也不能在身份未知时放行任何数据。
 */
@Slf4j
@Aspect
@Component
public class DataScopeAspect {

    @Around("@annotation(dataScope)")
    public Object checkScope(ProceedingJoinPoint joinPoint, DataScope dataScope) throws Throwable {
        CurrentUser user = CurrentUserHolder.require();

        if (!user.scope().covers(dataScope.value())) {
            log.warn("数据范围不足 user={} 需要={} 实际={} 方法={}",
                    user.username(), dataScope.value(), user.scope(),
                    joinPoint.getSignature().toShortString());
            throw new BizException(ErrorCode.FORBIDDEN, "没有权限访问该数据范围");
        }

        return joinPoint.proceed();
    }
}
