package com.studio.erp.employee.domain;

import java.util.Locale;

/**
 * 员工级别（薪酬档）。
 *
 * <p>【为什么要有这个枚举，而不是直接存字符串】
 * 级别决定底薪，底薪决定工资。如果库里存的是自由文本，某天有人录成 "elite"（小写）
 * 或 "精英"，底薪政策就匹配不上，这个人会静默掉到兜底值 —— 而页面不会报任何错。
 * 枚举 + 白名单校验把这类错误挡在写入之前。
 *
 * <p>【为什么级别与岗位分开】
 * 岗位（position）是"干什么活"，会参与派单过滤与数据权限；
 * 级别（emp_level）是"哪一档薪酬"，只影响钱。两者正交：
 * 设计师可以是普通也可以是精英，主管岗位的人也可能只拿精英档。
 * 混成一列的话，日后加一档"资深"就要改动所有按岗位判断的逻辑。
 */
public enum EmpLevel {

    /** 普通员工：默认级别，新入职未特别说明的都属于这一档 */
    NORMAL("普通员工"),

    /** 精英员工：骨干 / 资深，出图质量与产能稳定的人 */
    ELITE("精英员工"),

    /** 主管：部门负责人 */
    SUPERVISOR("主管");

    private final String label;

    EmpLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 解析级别码。
     *
     * <p>【为什么空值给 NORMAL，非法值直接抛】
     * 空值来自"老数据 / 前端没传"，回退到普通员工不会算错得太离谱；
     * 而 "SENIOR" 这种拼错的值如果也回退，就会变成"这个人明明该是精英，
     * 却一直按普通员工发底薪"—— 少发的钱没人会主动发现。宁可当次保存失败。
     */
    public static EmpLevel parse(String code) {
        if (code == null || code.isBlank()) {
            return NORMAL;
        }
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("员工级别只能是 NORMAL / ELITE / SUPERVISOR，实际为：" + code);
        }
    }

    /**
     * 按岗位推导默认级别。
     *
     * <p>只在"新增员工时没选级别"的情况下用。主管与管理员归主管档，
     * 其他人一律普通员工 —— 精英是稀缺档，必须人工指定，不能靠岗位自动给。
     * 反过来（新人默认精英）会让底薪悄悄多发，这是更贵的一种错。
     */
    public static EmpLevel fromPosition(String position) {
        if (position == null) {
            return NORMAL;
        }
        String p = position.trim().toUpperCase(Locale.ROOT);
        return "SUPERVISOR".equals(p) || "ADMIN".equals(p) ? SUPERVISOR : NORMAL;
    }
}
