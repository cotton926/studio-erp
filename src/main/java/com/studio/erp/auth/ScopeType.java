package com.studio.erp.auth;

/**
 * 数据可见范围，带等级用于"是否覆盖"判断。
 *
 * <p>【为什么要有等级而不是简单 equals】
 * 主管（TEAM）应该能看员工的（SELF）数据，反之不行。
 * 用等级比较一行代码解决，不用为每种组合写 if。
 *
 * <p>【本项目的实际映射】
 * · 老板 / 财务 → ALL
 * · 部门主管 → TEAM（看全团队，含所有人业绩）
 * · 客服 → TEAM（看自己名下客户，范围由 owner_emp_id 约束，不用这个等级控制）
 * · 设计师 → SELF（只能看自己）
 */
public enum ScopeType {

    SELF(1, "仅本人"),
    TEAM(2, "本团队"),
    ALL(3, "全部");

    private final int level;
    private final String label;

    ScopeType(int level, String label) {
        this.level = level;
        this.label = label;
    }

    public int getLevel() {
        return level;
    }

    public String getLabel() {
        return label;
    }

    /** 当前范围是否覆盖目标范围 */
    public boolean covers(ScopeType required) {
        return this.level >= required.level;
    }
}
