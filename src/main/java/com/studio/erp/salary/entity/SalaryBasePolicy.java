package com.studio.erp.salary.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分级底薪政策：一个员工级别对应一个默认底薪。
 *
 * <p>【这张表解决的问题】
 * 没有它的时候，底薪是一人一列手填：招人忘填就是 0 元，调薪就是挨个改几十行。
 * 有了它，人事只需要给员工挂一个级别，钱自动带出；真有个别特批的，
 * 再用 employee.base_salary 覆盖。
 *
 * <p>【为什么不跟 salary_tier 合并】
 * 提成阶梯是"业绩区间 → 比例"，按月动态命中；底薪是"人 → 固定金额"，与业绩无关。
 * 主键、维度、变更频率都不同，合并后每个查询都要先判断"这行属于哪类规则"。
 */
@Data
@TableName("salary_base_policy")
public class SalaryBasePolicy {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** NORMAL / ELITE / SUPERVISOR */
    private String levelCode;

    /** 展示名：普通员工 / 精英员工 / 主管 */
    private String levelName;

    /** 该级别的默认月底薪 */
    private BigDecimal baseSalary;

    private Integer sortNo;

    /** 给主管看的说明，例如"试用期按 80%" */
    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
