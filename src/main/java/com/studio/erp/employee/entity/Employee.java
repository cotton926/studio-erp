package com.studio.erp.employee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工。
 *
 * <p>【is_designer 为什么独立于 position】
 * 主管自己出图、客服转岗做设计都很常见。业绩分摊与"我的业绩"查询处处要用这个条件，
 * 靠 position 字符串匹配，一旦有人把岗位录成"设计师 "（带空格）或"高级设计师"，
 * 业绩就不声不响地漏算了 —— 而漏算通常要到发工资时才发现。
 */
@Data
@TableName("employee")
public class Employee {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String empNo;

    private String name;

    private Long deptId;

    /** SUPERVISOR / CS / DESIGNER / FINANCE / ADMIN */
    private String position;

    /**
     * 员工级别（薪酬档）：NORMAL / ELITE / SUPERVISOR。
     *
     * <p>【为什么与 position 分开存】
     * 岗位决定"干什么活"（参与派单过滤、数据权限），级别决定"底薪哪一档"。
     * 两者正交：设计师可以普通也可以精英，主管岗位也可能只拿精英档。
     * 合成一列的话，日后加一档就要改动所有按岗位判断的逻辑。
     */
    private String empLevel;

    /**
     * 个人指定底薪；null = 按 emp_level 从底薪政策取。
     *
     * <p>【为什么不是 NOT NULL DEFAULT 0】
     * 0 无法区分"没填"和"故意填 0"。有了级别兜底后，默认值必须是 null
     * 才能表达"跟随政策"；0 留给真正的特例（如纯提成实习生）。
     * 应发工资 = 实际底薪 + 业绩×阶梯比例。
     */
    private java.math.BigDecimal baseSalary;

    /** 1=参与业绩分成 0=不参与 */
    private Integer isDesigner;

    private String phone;

    private LocalDate hireDate;

    /** 1=在职 0=离职；离职后其历史业绩必须保留，所以不能删行 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
