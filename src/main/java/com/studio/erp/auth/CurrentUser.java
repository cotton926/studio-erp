package com.studio.erp.auth;

import java.util.Set;

/**
 * 当前登录人（一次请求内的不可变快照）。
 *
 * <p>【为什么是 record 而不是可变对象】
 * 一旦构造就不能改，杜绝"某个 Service 顺手改了当前用户的 scope"这种灵异问题。
 *
 * <p>【为什么角色用 String code 而不是枚举】
 * 角色是会变的（今年加个"项目总监"，明年合并"客服/商务"），
 * 用枚举意味着每加一个角色都要改代码重新发版；用 code 字符串，加角色只需加数据。
 *
 * @param employeeId 员工ID，数据隔离的唯一依据
 * @param username   登录名
 * @param roleCodes  角色码集合，如 SUPERVISOR / DESIGNER
 * @param scope      数据可见范围
 */
public record CurrentUser(
        Long employeeId,
        String username,
        Set<String> roleCodes,
        ScopeType scope) {

    public CurrentUser {
        roleCodes = roleCodes == null ? Set.of() : Set.copyOf(roleCodes);
    }

    public boolean hasRole(String roleCode) {
        return roleCodes.contains(roleCode);
    }

    /** 是否管理层（主管或老板），用于少数"是否显示带人数据入口"的判断，不能用于权限决策 */
    public boolean isManager() {
        return hasRole("ADMIN") || hasRole("SUPERVISOR");
    }
}
