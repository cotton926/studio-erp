package com.studio.erp.common.exception;

/**
 * 业务错误码。
 *
 * <p>【为什么不用 HTTP 状态码包打天下】
 * "分成比例不是 100%"和"你没有权限看这条业绩"都是 400/403，
 * 但前端需要给出完全不同的引导（前者跳到改比例，后者提示找主管）。
 * 业务码分段后，前端按码做分支，提示语也不会串。
 *
 * <p>编码规则：前三位对应 HTTP 语义，后两位是细分序号。
 */
public enum ErrorCode {

    /* ---------- 通用 ---------- */
    BAD_REQUEST(40000, "请求参数有误"),
    PASSWORD_TOO_WEAK(40001, "密码至少 8 位，且必须同时包含字母和数字"),
    PASSWORD_SAME(40002, "新密码不能与当前密码相同"),

    /* ---------- 员工与账号 ---------- */
    EMP_NO_DUPLICATED(40010, "工号已存在，请换一个"),
    POSITION_INVALID(40011, "岗位不在允许的范围内"),
    EMPLOYEE_RESIGNED(40012, "该员工已离职，请先办理复职"),
    USERNAME_DUPLICATED(40013, "登录名已被占用"),
    ACCOUNT_ALREADY_EXISTS(40014, "该员工已有登录账号，请直接重置密码"),
    CANNOT_OPERATE_SELF(40015, "不能对自己执行此操作"),
    LAST_ADMIN_PROTECTED(40016, "系统必须保留至少一个启用的管理员账号"),
    ROLE_INVALID(40017, "角色不存在或已被删除"),
    EMP_LEVEL_INVALID(40018, "员工级别只能是 NORMAL（普通）/ ELITE（精英）/ SUPERVISOR（主管）"),
    PERIOD_INVALID(40019, "期间格式应为 yyyy-MM，例如 2026-11"),
    UNAUTHORIZED(40100, "请先登录"),
    /**
     * 【为什么登录失败只有一句话】
     * 不区分「用户不存在」和「密码错误」。否则攻击者可以先批量试用户名，
     * 有回应的就是真实账号（用户名枚举），再集中爆破这些小目标。
     * 内部系统也一样 —— 员工名单往往能从别处拿到。
     */
    LOGIN_FAILED(40101, "用户名或密码错误"),
    ACCOUNT_DISABLED(40102, "账号已停用，请联系管理员"),
    LOGIN_LOCKED(40103, "登录失败次数过多，请 15 分钟后再试"),
    PASSWORD_EXPIRED(40104, "请先修改初始密码"),
    FORBIDDEN(40300, "没有操作权限"),
    NOT_FOUND(40400, "数据不存在"),
    CONFLICT(40900, "数据状态已变化，请刷新后重试"),
    INTERNAL_ERROR(50000, "系统开小差了，请稍后重试"),

    /* ---------- 订单 ---------- */
    ORDER_STATUS_INVALID(41001, "当前订单状态不允许此操作"),
    ORDER_ITEM_EMPTY(41002, "订单没有有效明细行"),
    RATIO_NOT_FULL(41003, "分成比例合计必须等于 100%"),
    ORDER_ITEM_INVALID(41004, "订单明细不完整：名称、数量、单价都要填"),
    ORDER_ASSIGNEE_EMPTY(41005, "收费明细行必须指定设计师"),
    /** 已产生回款或已交付的单子重新定价，会让已生成的业绩对不上账 */
    ORDER_PRICING_LOCKED(41006, "订单已有回款或已交付，不能重新定价"),
    ORDER_NOT_PRICED(41007, "订单还没定价，不能做此操作"),

    /* ---------- 收款 ---------- */
    PAYMENT_OVER_ALLOCATED(42001, "核销金额超过回款金额"),
    ORDER_OVER_PAID(42002, "核销后将超出订单总额"),
    PAYMENT_ALREADY_VOID(42003, "回款单已作废，不能核销"),
    PAYMENT_AMOUNT_INVALID(42004, "回款金额必须大于 0 且不超过未收金额"),

    /* ---------- 业绩 ---------- */
    PERF_ALREADY_CONFIRMED(43001, "该笔业绩已确认，请勿重复提交"),
    PERF_AMOUNT_NEGATIVE(43002, "确认金额不能为负数，冲销请走反向流水"),
    PERF_BASE_ZERO(43003, "业绩分摊基数为 0，请检查订单明细金额"),

    /* ---------- 工资 ---------- */
    SALARY_TIER_EMPTY(44001, "工资阶梯未配置，请先维护提成档位"),
    SALARY_TIER_INVALID(44002, "工资阶梯配置有误，请检查档位是否首尾相接"),
    BASE_SALARY_INVALID(44003, "底薪不能为负数"),
    SALARY_PERIOD_INVALID(44004, "工资期间格式应为 yyyy-MM，例如 2026-11"),
    SALARY_BASE_POLICY_EMPTY(44005, "底薪政策未配置，请先在「工资设置」里维护各级别底薪"),
    SALARY_BASE_POLICY_INVALID(44006, "底薪政策配置有误，请检查是否覆盖全部员工级别"),

    /* ---------- 稿件与送审 ---------- */
    DELIVERABLE_NOT_FOUND(45001, "稿件版本不存在"),
    DELIVERABLE_FILE_EMPTY(45002, "上传的文件是空的，请重新选择"),
    DELIVERABLE_TYPE_INVALID(45003, "不支持这种文件类型，请上传图片、设计源文件、文档或压缩包"),
    DELIVERABLE_ALREADY_REVIEWED(45004, "该版本已经审核过，如需再次送审请上传新版本"),
    DELIVERABLE_FEEDBACK_REQUIRED(45005, "退回必须填写原因，否则统计不出返工的真实原因"),
    DELIVERABLE_FILE_MISSING(45006, "文件已丢失（可能被清理或迁移），请联系管理员从备份恢复"),
    DELIVERABLE_VERSION_CONFLICT(45007, "已有其他人上传了同一版本，请刷新后重试"),

    /* ---------- 收款计划与催收 ---------- */
    COLLECTION_PLAN_NOT_FOUND(46001, "收款计划不存在，请刷新后重试"),
    COLLECTION_PLAN_PAID(46002, "该期款项已收讫，无需再催收"),
    COLLECTION_ACTION_INVALID(46003, "催收方式或结果不在允许的范围内"),

    /* ---------- 开票 ---------- */
    ORDER_NOT_INVOICEABLE(47001, "订单未定价或已取消，不能开票"),
    INVOICE_AMOUNT_INVALID(47002, "开票金额必须大于 0"),
    INVOICE_OVER_AMOUNT(47003, "开票金额超过订单可开余额"),
    INVOICE_STATUS_INVALID(47004, "当前开票状态不允许此操作"),
    INVOICE_NO_REQUIRED(47005, "开票时必须填写发票号"),
    INVOICE_NO_DUPLICATED(47006, "该发票号已存在，请核对后重试"),
    INVOICE_VOID_REASON_REQUIRED(47007, "作废必须填写原因"),

    /* ---------- 报表导出 ---------- */
    EXPORT_ROW_LIMIT(48001, "导出数据量超过上限，请缩小筛选范围（如缩短日期区间）"),
    EXPORT_FAILED(48002, "生成表格文件失败，请稍后重试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
