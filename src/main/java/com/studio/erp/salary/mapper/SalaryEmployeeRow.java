package com.studio.erp.salary.mapper;

import java.math.BigDecimal;

/**
 * 工资表的一行人员信息（不含计算结果）。
 *
 * <p>【为什么要单独一个类，而不用 Employee 实体】
 * 工资页只需要"工号、姓名、岗位、级别、部门、个人底薪"这几项，用实体会把手机号、
 * 入职日期、账号状态一并带出来。工资单是最敏感的页面，能少返回一个字段就少一个泄露面。
 *
 * <p>【baseSalary 为什么可能是 null】
 * 它是"个人指定底薪"，为空表示跟随级别的政策金额。
 * 实际发放的底薪由 {@code BaseSalaryResolver} 结合 empLevel 算出来，不在这里。
 */
public record SalaryEmployeeRow(
        Long employeeId,
        String empNo,
        String name,
        String position,
        String empLevel,
        String deptName,
        BigDecimal baseSalary,
        Integer isDesigner) {
}
