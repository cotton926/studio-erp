package com.studio.erp.auth.dto;

import com.studio.erp.auth.ScopeType;

import java.util.Set;

/**
 * 当前登录人视图（返回给前端）。
 *
 * <p>【刻意不包含的字段】
 * passwordHash、员工手机号、薪资类字段一律不出现。前端只需要"我是谁、我是什么角色、
 * 我能看多大范围"来决定菜单，不需要也不应该拿到凭据。
 * 用独立 VO 而不是直接返回实体，是为了让"多返回一个字段"变成一次显式代码改动，
 * 而不是某天实体加了字段就悄悄泄露出去。
 */
public record CurrentUserVO(
        Long userId,
        String username,
        Long employeeId,
        String employeeName,
        Set<String> roleCodes,
        ScopeType scope,
        boolean mustChangePwd) {
}
