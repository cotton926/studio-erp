package com.studio.erp.salary.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/**
 * 调整某个员工的底薪。
 *
 * <p>【为什么单独一个接口，而不是塞进员工编辑】
 * 底薪是敏感字段，改它需要留痕（谁在什么时候把谁的底薪从多少改成多少）。
 * 如果混在"编辑员工"接口里，一次普通的改手机号操作也会被记成"修改了员工信息"，
 * 真正需要审计的那次改动就淹在噪声里了。
 *
 * <p>【为什么用 set 而不是 add】
 * 加薪一般是"改成 8000"，不是"加 500"。用绝对值语义，重复提交结果一致；
 * 用增量语义，网络重试两次就多加了 500 —— 这类错误在工资上无法事后察觉。
 *
 * <p>【为什么 baseSalary 允许为 null】
 * 底薪现在有两层：级别政策（普通 1500 / 精英 2000 / 主管 3000）是默认值，
 * 这里传值表示"这个人是特例"，传 null 表示"撤销特例，回到级别政策"。
 * 若把 null 当成参数缺失直接拒绝，撤销特批就只能填一个假数字，
 * 以后再调政策，这个人就跟不上政策了。
 */
public record BaseSalaryRequest(
        @DecimalMin(value = "0", message = "底薪不能为负数") BigDecimal baseSalary) {
}
