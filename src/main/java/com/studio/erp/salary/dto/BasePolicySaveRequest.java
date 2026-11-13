package com.studio.erp.salary.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 整表替换底薪政策。
 *
 * <p>【为什么是整表替换】
 * 与工资阶梯同理：级别必须齐全（三个级别各一条），缺一个就会有人算不出底薪。
 * 允许逐条改的话，"删掉精英档但还没加回来"的中间状态是合法的，
 * 这时候查工资就会报错。整表提交 = 校验通过才生效，不存在中间态。
 *
 * <p>【为什么不校验"必须正好三条"】
 * 校验的是"必须覆盖所有已有级别"，由服务层拿 EmpLevel 枚举逐个比对。
 * 写死 3 条的话，日后加了第四档员工级别，这里会变成一个莫名奇妙的失败点。
 */
public record BasePolicySaveRequest(
        @NotEmpty(message = "底薪政策不能为空") List<@Valid PolicyItem> policies) {

    /**
     * 单个级别的底薪。
     *
     * @param levelCode  NORMAL / ELITE / SUPERVISOR
     * @param baseSalary 该级别默认底薪
     * @param remark     说明，例如"试用期按 80%"
     */
    public record PolicyItem(
            @NotBlank(message = "级别不能为空") String levelCode,
            @NotNull(message = "底薪不能为空")
            @DecimalMin(value = "0", message = "底薪不能为负数") BigDecimal baseSalary,
            @Size(max = 200, message = "说明最长 200 字") String remark) {
    }
}
