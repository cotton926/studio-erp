package com.studio.erp.auth;

import com.studio.erp.common.exception.BizException;
import com.studio.erp.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * {@link RequireRole} 切面。
 *
 * <p>【为什么用注解而不是在 Controller 里写 if】
 * 与 DataScope 同理：权限判断散落在各处，漏写一处就是一条越权通道，
 * 而且漏写不会报错，只会"悄悄放行"。注解写在方法签名上，
 * 代码评审时一眼就能扫出哪个接口没做限制。
 *
 * <p>【为什么用角色码而不是角色 ID】
 * ID 是自增的，换个环境重新初始化就可能错位；写死 id==1 相当于埋雷。
 *
 * <p>【失败一律 403，不区分"角色不存在"和"角色不够"】
 * 对调用方来说结果都是"不能做这件事"，区分只会多一个信息泄漏面。
 */
@Slf4j
@Aspect
@Component
public class RequireRoleAspect {

    @Around("@annotation(requireRole)")
    public Object checkRole(ProceedingJoinPoint joinPoint, RequireRole requireRole) throws Throwable {
        CurrentUser user = CurrentUserHolder.require();
        Set<String> owned = user.roleCodes();
        String[] required = requireRole.value();

        boolean pass = requireRole.requireAll()
                ? owned.containsAll(Arrays.asList(required))
                : Arrays.stream(required).anyMatch(owned::contains);

        if (!pass) {
            log.warn("角色不足 user={} 需要={} 实际={} 方法={}",
                    user.username(), Arrays.toString(required), owned,
                    joinPoint.getSignature().toShortString());
            throw new BizException(ErrorCode.FORBIDDEN, "没有权限执行此操作");
        }

        return joinPoint.proceed();
    }
}
