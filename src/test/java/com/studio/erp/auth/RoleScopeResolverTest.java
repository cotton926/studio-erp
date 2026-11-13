package com.studio.erp.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 角色 → 数据范围映射的单元测试。
 *
 * <p>【为什么要单独测这个纯函数】
 * 它决定了"谁能看到全公司的业绩"。一旦这里算错，隔离就是纸糊的；
 * 而它的逻辑又极其简单，简单到评审时容易被一眼扫过去 —— 恰恰最该用测试钉住。
 * 这里不需要数据库、不启动 Spring，跑一次不到 10ms，没有理由不测。
 */
class RoleScopeResolverTest {

    @Test
    @DisplayName("设计师 → 仅本人")
    void designerIsSelf() {
        assertEquals(ScopeType.SELF, RoleScopeResolver.resolve(Set.of("DESIGNER")));
    }

    @Test
    @DisplayName("主管 → 本团队")
    void supervisorIsTeam() {
        assertEquals(ScopeType.TEAM, RoleScopeResolver.resolve(Set.of("SUPERVISOR")));
    }

    @Test
    @DisplayName("财务 → 全部（要对账，必须能看所有人的单）")
    void financeIsAll() {
        assertEquals(ScopeType.ALL, RoleScopeResolver.resolve(Set.of("FINANCE")));
    }

    @Test
    @DisplayName("一人多角色时取最大范围：设计师兼主管 → 团队")
    void multiRoleTakesMax() {
        assertEquals(ScopeType.TEAM,
                RoleScopeResolver.resolve(Set.of("DESIGNER", "SUPERVISOR")));
    }

    @Test
    @DisplayName("顺序不影响结果：集合无序，结果必须稳定")
    void orderDoesNotMatter() {
        ScopeType a = RoleScopeResolver.resolve(Set.of("ADMIN", "DESIGNER", "CS"));
        ScopeType b = RoleScopeResolver.resolve(Set.of("CS", "DESIGNER", "ADMIN"));
        assertEquals(a, b);
        assertEquals(ScopeType.ALL, a);
    }

    @Test
    @DisplayName("未知角色必须退回最小权限，不能因为漏配映射就放权")
    void unknownRoleFallsBackToSelf() {
        assertEquals(ScopeType.SELF, RoleScopeResolver.resolve(Set.of("PROJECT_DIRECTOR")));
    }

    @Test
    @DisplayName("空角色集 → 最小权限（空账号不能看任何人的数据）")
    void emptyRolesIsSelf() {
        assertEquals(ScopeType.SELF, RoleScopeResolver.resolve(Set.of()));
        assertEquals(ScopeType.SELF, RoleScopeResolver.resolve(null));
    }
}
