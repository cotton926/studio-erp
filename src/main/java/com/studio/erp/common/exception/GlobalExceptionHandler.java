package com.studio.erp.common.exception;

import com.studio.erp.common.api.R;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。
 *
 * <p>【为什么必须有这一层】
 * 没有它，出了异常 Spring 会返回一段 HTML 错误页或裸露的堆栈，
 * 前端解析不了，用户看到白屏，运维还得去翻 catalina.out。
 * 有了它，任何异常都收敛成同一种 JSON，前端一套逻辑处理到底。
 *
 * <p>【分级策略】
 * · 业务异常（BizException）：可预期，记 warn，不打堆栈 —— 一天几百条堆栈会把日志淹掉
 * · 参数校验失败：可预期，记 warn，把字段级错误拼成一句人话返回
 * · 兜底 Exception：不可预期，记 error 并打全堆栈，但对外只返回"系统开小差"，
 *   绝不把堆栈或 SQL 暴露给前端（那是给攻击者送情报）
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：提示语直接给用户看 */
    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleBiz(BizException e) {
        log.warn("业务异常 code={} msg={}", e.getErrorCode().getCode(), e.getMessage());
        return R.fail(e.getErrorCode(), e.getMessage());
    }

    /** 参数校验失败：把「字段名: 提示」拼起来，方便前端直接弹 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("；"));
        log.warn("参数校验失败: {}", detail);
        return R.fail(ErrorCode.BAD_REQUEST, detail);
    }

    /** 唯一索引冲突：并发重复提交时落到这里，告诉用户刷新即可，不是系统故障 */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一约束冲突: {}", e.getMostSpecificCause().getMessage());
        return R.fail(ErrorCode.CONFLICT, "该记录已存在，请刷新后查看");
    }

    /**
     * 【路由不存在 = 404，绝不能被兜底处理器吃掉】
     *
     * 为什么单独处理：Spring 6.1 访问未映射的 URL 不会抛 404 异常，
     * 而是抛 NoResourceFoundException（"No static resource"）。
     * 一旦被下面的 @ExceptionHandler(Exception.class) 接走，就会变成
     * HTTP 200 + 「系统开小差了」——后果是：
     *   · 前端把这个响应当成正常响应，路由写错了却看不到 404，排查靠猜；
     *   · 网关/探活/监控只认状态码，全 200 意味着「一切正常」，故障被掩盖；
     *   · 每次拼错一个路径就在日志里留一条带堆栈的 ERROR，真错误被淹。
     * 实测确认过：GET / 曾返回 200 + code 50000。
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<Void> handleNotFound(Exception e) {
        log.warn("接口不存在: {}", e.getMessage());
        return R.fail(ErrorCode.NOT_FOUND, "接口不存在，请检查请求路径");
    }

    /** 方法不支持（如把 POST 接口用 GET 调）：返回 405，同样不能落到兜底 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMessage());
        return R.fail(ErrorCode.BAD_REQUEST, "请求方法不支持");
    }

    /**
     * 未登录 / 令牌无效 / 令牌过期。
     *
     * <p>【为什么这里返回 401 而不是 200】
     * 前端拦截器统一按 HTTP 状态判断"要不要跳登录页"，按业务码判断"提示什么话"。
     * 如果连登录态失效都返回 200，前端每个页面都得自己判断 code == 40100，
     * 漏一个就会出现"接口静默失败、页面一片空白"。
     *
     * <p>【注意不要把异常原文返回给前端】
     * NotLoginException 的 message 会带上"令牌无效/已过期/未提供"等内部措辞，
     * 对用户没有意义，只说"登录已过期，请重新登录"。
     */
    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<Void> handleNotLogin(NotLoginException e) {
        log.warn("未登录访问 type={}", e.getType());
        return R.fail(ErrorCode.UNAUTHORIZED, "登录已过期，请重新登录");
    }

    /** 已登录但权限不足 */
    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleNotPermission(Exception e) {
        log.warn("权限不足: {}", e.getMessage());
        return R.fail(ErrorCode.FORBIDDEN);
    }

    /**
     * 请求参数缺失 / 类型不对（缺 ?status=、把 id 传成字母等）。
     *
     * <p>【为什么必须单独处理】这类异常属于"调用方写错了"，不是系统故障。
     * 落到兜底处理器会变成 200 + 「系统开小差了」，前端只会提示"稍后重试"，
     * 而真正该做的是改成 400 并告诉用户少了哪个参数。
     * 实测踩到：PUT /orders/2/status 不带 status 参数，返回的是 50000。
     */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBadParam(Exception e) {
        String detail = "请求参数有误";
        if (e instanceof MissingServletRequestParameterException missing) {
            detail = "缺少必填参数：" + missing.getParameterName();
        } else if (e instanceof MethodArgumentTypeMismatchException mismatch) {
            detail = "参数 " + mismatch.getName() + " 格式不正确";
        } else if (e instanceof HttpMessageNotReadableException) {
            detail = "请求体格式不正确，请检查 JSON";
        }
        log.warn("请求参数错误: {}", detail);
        return R.fail(ErrorCode.BAD_REQUEST, detail);
    }

    /**
     * 兜底：不暴露任何内部细节。
     *
     * 注意这里刻意返回 HTTP 200 而不是 500：业务异常在语义上是「请求被正确处理，
     * 只是业务规则不允许」，HTTP 层没有出错，前端统一按 code 分支即可。
     * 但「路由不存在 / 方法不对」属于传输层错误，必须用真实状态码，
     * 所以上面两个 handler 单独处理并覆盖了状态码。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleUnexpected(Exception e) {
        log.error("未预期的异常", e);
        return R.fail(ErrorCode.INTERNAL_ERROR);
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
