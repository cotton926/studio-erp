package com.studio.erp.salary.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * 整表替换工资阶梯。
 *
 * <p>【为什么是"整表替换"而不是"逐档增删改"】
 * 阶梯是一个整体约束：档与档必须首尾相接、最后一档必须无上限。
 * 如果允许单条改，就会出现"删掉中间一档、留下断档"的中间状态，
 * 而这个状态在并发下可能被人当成正常配置用一阵子。
 * 整表提交 = 一次性校验 + 一次性生效，不存在中间态。
 *
 * <p>【代价】
 * 两个人同时改阶梯时，后提交的会覆盖前一个。因为这是"一个月才动一次"的配置，
 * 加乐观锁的复杂度不划算；实际做法是由主管一个人维护，接口另外限定了 ADMIN 角色。
 */
public record TierSaveRequest(@NotEmpty(message = "至少要有一个档位") List<@Valid TierItem> tiers) {

    /**
     * 单个档位。
     *
     * @param levelNo   档位序号，可不传，按提交顺序自动编号（1、2、3…）
     * @param minAmount 下限（含）
     * @param maxAmount 上限（不含），最高档传 null
     * @param rateBp    比例，万分比：4000 = 40%
     * @param remark    给员工看的说明
     */
    public record TierItem(
            Integer levelNo,
            @NotNull(message = "档位下限不能为空") BigDecimal minAmount,
            BigDecimal maxAmount,
            @NotNull(message = "档位比例不能为空") Integer rateBp,
            String remark) {
    }
}
