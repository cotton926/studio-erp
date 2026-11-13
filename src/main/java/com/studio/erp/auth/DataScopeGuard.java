package com.studio.erp.auth;

import com.studio.erp.common.exception.BizException;
import com.studio.erp.common.exception.ErrorCode;

/**
 * 数据范围守卫 —— "把东西锁在抽屉里"的那一层。
 *
 * <p>【为什么注解还不够】
 * {@link DataScope} 只拦"能不能进这个接口"。如果接口是
 * {@code GET /performance/my/items?employeeId=123}，设计师把 123 改成别人的工号，
 * 注解照样放行。真正的隔离必须在拿到参数的这一刻就把它按身份改写掉。
 *
 * <p>【为什么"改写"比"报错"更安全】
 * 报错意味着要先把参数用起来才能判断，中间任何一次疏忽都可能已经把数据查出来了。
 * 改写则是"不管你传什么，低权限用户永远只能查自己" ——
 * 前端参数写错、被手工拼 URL、被抓包改包，结果都一样：查到的还是自己的数据。
 *
 * <p>【为什么这个类全是静态方法】
 * 它不持有状态，只读 ThreadLocal。做成 Bean 反而要在每个 Service 里注入一遍。
 */
public final class DataScopeGuard {

    private DataScopeGuard() {
    }

    /**
     * 把"请求想查的工号"改写成"当前用户有权查的工号"。
     *
     * @param requestedEmployeeId 前端传上来的工号，可为 null（表示查自己）
     * @return 实际允许查询的工号
     */
    public static Long resolveEmployeeId(Long requestedEmployeeId) {
        CurrentUser user = CurrentUserHolder.require();

        // 高权限：传了就用传的，没传就看自己（也允许看自己，避免空指针）
        if (user.scope().covers(ScopeType.ALL)) {
            return requestedEmployeeId == null ? user.employeeId() : requestedEmployeeId;
        }

        // 低权限：无条件改写为自己。
        // 这里故意不抛异常也不打日志告警 —— 用户正常操作不会传别人的 ID，
        // 频繁告警只会让人麻木，真正的越权探测应该记在网关层的访问日志里。
        return user.employeeId();
    }

    /** 需要明确报错的场景（比如详情页按 ID 直接取），用这个 */
    public static void requireSelfOrAll(Long targetEmployeeId) {
        CurrentUser user = CurrentUserHolder.require();
        if (user.scope().covers(ScopeType.ALL)) {
            return;
        }
        if (targetEmployeeId != null && targetEmployeeId.equals(user.employeeId())) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN, "只能查看自己的数据");
    }

    /** 管理类操作（团队报表、全员工资）用这个，明确要求高权限 */
    public static void requireTeamOrAll() {
        CurrentUser user = CurrentUserHolder.require();
        if (!user.scope().covers(ScopeType.TEAM)) {
            throw new BizException(ErrorCode.FORBIDDEN, "需要主管及以上权限");
        }
    }
}
