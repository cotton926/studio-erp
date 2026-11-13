package com.studio.erp.auth;

import com.studio.erp.common.exception.BizException;
import com.studio.erp.common.exception.ErrorCode;

/**
 * 当前登录人的线程上下文。
 *
 * <p>【为什么用 ThreadLocal 而不是方法参数一路传】
 * 如果每个 Service 方法都加一个 CurrentUser 参数，业务签名会被污染，
 * 且任何人只要传个假的进去就能越权 —— 那反而不安全。
 *
 * <p>【最大的坑：线程复用导致数据串号】
 * Tomcat 线程池会复用线程。如果请求结束时没有 clear()，
 * 下一个请求可能读到上一个用户的身份，出现"设计师看到了主管的业绩"这种事故，
 * 而且极难复现。所以：
 * 1) 必须在拦截器的 afterCompletion 里 finally clear()；
 * 2) 这里用 remove() 而不是 set(null)，remove 才会真正清掉 Entry 防止内存泄漏。
 */
public final class CurrentUserHolder {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private CurrentUserHolder() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    /** 取不到就抛 401：宁可让用户重新登录，也不能在"匿名"状态下继续跑业务逻辑 */
    public static CurrentUser require() {
        CurrentUser user = HOLDER.get();
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    public static Long currentEmployeeId() {
        return require().employeeId();
    }

    /** 必须在请求结束时调用，见类注释 */
    public static void clear() {
        HOLDER.remove();
    }
}
