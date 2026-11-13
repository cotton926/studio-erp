package com.studio.erp.employee.dto;

import lombok.Data;

/**
 * 员工列表查询条件。
 *
 * <p>【为什么用普通类而不是 record】
 * 这些字段要在 MyBatis 的动态 SQL 里以 {@code #{q.keyword}} 的形式访问，
 * 而 MyBatis 底层用的 OGNL 走的是 getXxx() 约定；record 的访问器是
 * {@code keyword()}，OGNL 读不到，条件会静默失效 —— 页面看起来正常，
 * 只是筛选条件永远不生效，排查起来非常费劲。
 *
 * <p>分页参数不设上限，由 MyBatis-Plus 的 maxLimit 统一截断。
 * 在 DTO 里再限制一次会出现"前端传 500，实际按 200 返回"的困惑。
 */
@Data
public class EmployeeQuery {

    private long pageNum = 1;

    private long pageSize = 20;

    /** 姓名 / 工号 / 手机号 模糊匹配 */
    private String keyword;

    private Long deptId;

    /** SUPERVISOR / CS / DESIGNER / FINANCE / ADMIN */
    private String position;

    /** 1=在职 0=离职；不传表示全部 */
    private Integer status;

    /** 1=参与业绩分成 0=不参与；不传表示全部 */
    private Integer isDesigner;

    /** true=只看已开账号的，false=只看没有账号的，null=全部 */
    private Boolean hasAccount;
}
