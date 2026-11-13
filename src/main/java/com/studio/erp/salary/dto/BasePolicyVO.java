package com.studio.erp.salary.dto;

import com.studio.erp.salary.entity.SalaryBasePolicy;

import java.math.BigDecimal;

/**
 * 底薪政策展示对象。
 *
 * <p>【为什么员工也能看这张表】
 * 员工看到工资单上"底薪 2000（精英员工）"，还需要知道这 2000 是公司统一标准
 * 而不是主管随手给的。把政策表开放给全员只读，能挡掉一大类"为什么他比我多"
 * 的猜测 —— 级别是公开的，钱是按规则算的。
 * 写权限仍然只有管理员，改一个数字全公司底薪都变。
 */
public record BasePolicyVO(
        Long id,
        String levelCode,
        String levelName,
        BigDecimal baseSalary,
        Integer sortNo,
        String remark) {

    public static BasePolicyVO of(SalaryBasePolicy p) {
        return new BasePolicyVO(
                p.getId(),
                p.getLevelCode(),
                p.getLevelName(),
                p.getBaseSalary(),
                p.getSortNo(),
                p.getRemark());
    }
}
