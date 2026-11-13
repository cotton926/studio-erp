package com.studio.erp.common.exception;

/**
 * 业务异常。
 *
 * <p>【为什么不直接 throw new RuntimeException】
 * 全局异常处理器只能靠类型判断该返回什么码。全用 RuntimeException，
 * 所有业务错误都会掉进"系统错误 50000"那一支，用户看到"系统开小差了"
 * 却不知道自己少填了一个字段 —— 这类提示会直接把客服电话打爆。
 *
 * <p>【为什么继承 RuntimeException 而不是 Exception】
 * 业务校验失败属于"可预期的正常分支"，不该强迫每一层都写 try-catch 或 throws，
 * 那样会把业务代码淹在样板里。检查型异常只适合"调用方必须处理才能继续"的场景。
 *
 * <p>【为什么不打印堆栈】
 * 参数校验失败一天可能几百次，全打堆栈会把真正的错误淹掉。这里只记录，
 * 由全局处理器按 warn 级别输出一行。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 带具体上下文的提示，例如"明细行【包装设计】分成比例合计为 90%" */
    public BizException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
