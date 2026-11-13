package com.studio.erp.auth;

import java.util.Set;

/**
 * 角色 → 数据范围的映射（唯一一处定义，别处不要再写 switch）。
 *
 * <p>【为什么取"最大值"而不是固定优先级表】
 * 一人可兼多角色。财务兼主管时，若按角色列表顺序取第一个，结果取决于
 * 数据库返回顺序 —— 同样的账号在两次查询里可能看到不同范围，这种不确定性最致命。
 * 取等级最大的那个，结果稳定且符合直觉：只要有一个角色能看全部，就能看全部。
 *
 * <p>【为什么 CS 是 TEAM 而不是 SELF】
 * 客服要看自己名下所有客户的订单与回款进度，这是"一团数据"而不是"一个人的数据"。
 * 真正的范围收窄由 client.owner_emp_id 之类的业务条件负责，
 * 数据范围等级只表达"这个人需不需要跨人看数据"。
 */
public final class RoleScopeResolver {

    private RoleScopeResolver() {
    }

    public static ScopeType resolve(Set<String> roleCodes) {
        ScopeType max = ScopeType.SELF;
        if (roleCodes == null || roleCodes.isEmpty()) {
            return max;
        }
        for (String code : roleCodes) {
            ScopeType scope = scopeOf(code);
            if (scope.getLevel() > max.getLevel()) {
                max = scope;
            }
        }
        return max;
    }

    /**
     * 未知角色一律退回 SELF（最小权限）。
     * 如果这里给 TEAM 或 ALL，那么"新增一个角色但忘了在映射里登记"就会导致
     * 一群人意外获得越权范围 —— 默认值必须是失败安全的。
     */
    private static ScopeType scopeOf(String code) {
        return switch (code) {
            case "ADMIN", "FINANCE" -> ScopeType.ALL;
            case "SUPERVISOR", "CS" -> ScopeType.TEAM;
            case "DESIGNER" -> ScopeType.SELF;
            default -> ScopeType.SELF;
        };
    }
}
