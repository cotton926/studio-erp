package com.studio.erp.salary.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studio.erp.salary.domain.SalaryTierDef;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工资提成档位。
 *
 * <p>【这张表是"可配置的钱规则"，不是业务流水】
 * 它被主管通过接口整体替换（先删后插），不像订单那样逐条维护。
 * 之所以敢整体替换，是因为档位只有几行、且没有历史外键引用于它 ——
 * 工资单落库时会把命中的比例快照进 salary_record（如已生成），
 * 不会因为改了档位而导致历史工资重算。
 */
@Data
@TableName("salary_tier")
public class SalaryTier {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer levelNo;

    /** 下限（含） */
    private BigDecimal minAmount;

    /** 上限（不含）；null = 无上限 */
    private BigDecimal maxAmount;

    /** 比例，万分比：4000 = 40% */
    private Integer rateBp;

    /** 档位说明，直接给员工看 */
    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * 转成计算用的领域对象。
     *
     * <p>作用是切断"算法依赖数据库实体"这条线：
     * SalaryCalculator 只认识 SalaryTierDef，单元测试里可以直接 new 出来跑边界。
     */
    public SalaryTierDef toDef() {
        return new SalaryTierDef(levelNo, minAmount, maxAmount, rateBp);
    }
}
