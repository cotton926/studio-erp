package com.studio.erp.common.api;

import com.studio.erp.common.exception.ErrorCode;
import org.slf4j.MDC;

/**
 * 统一响应体。
 *
 * <p>【为什么前端只认这一种结构】
 * 若有的接口返回裸对象、有的返回 {success:true}、有的直接抛异常文本，
 * 前端每个页面都要写不同的错误处理，出错时日志也对不上。统一之后，
 * axios 拦截器只写一次，任何接口的失败都能弹出一句人能看懂的话。
 *
 * <p>【traceId 为什么也返回给前端】
 * 用户截图报错时，客服只要拿到 traceId 就能在日志里定位到那一次请求的全部上下文，
 * 不用再去问"你几点操作的、点了哪个按钮"。
 *
 * @param code    业务码，0 表示成功
 * @param msg     提示语，直接展示给用户
 * @param data    业务数据
 * @param traceId 链路追踪号
 */
public record R<T>(int code, String msg, T data, String traceId) {

    public static final int SUCCESS = 0;

    public static <T> R<T> ok(T data) {
        return new R<>(SUCCESS, "ok", data, currentTraceId());
    }

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return new R<>(errorCode.getCode(), errorCode.getMessage(), null, currentTraceId());
    }

    public static <T> R<T> fail(ErrorCode errorCode, String msg) {
        return new R<>(errorCode.getCode(), msg, null, currentTraceId());
    }

    public boolean isSuccess() {
        return code == SUCCESS;
    }

    private static String currentTraceId() {
        String traceId = MDC.get("traceId");
        return traceId == null ? "" : traceId;
    }
}
