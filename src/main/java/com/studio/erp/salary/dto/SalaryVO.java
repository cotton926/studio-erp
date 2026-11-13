package com.studio.erp.salary.dto;

import com.studio.erp.employee.domain.EmpLevel;
import com.studio.erp.performance.domain.RatioUtil;
import com.studio.erp.salary.domain.BaseSalaryResolver;
import com.studio.erp.salary.domain.SalaryBreakdown;
import com.studio.erp.salary.mapper.SalaryEmployeeRow;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 工资单（员工端与主管端共用同一结构）。
 *
 * <p>【为什么员工和主管用同一个 VO，而不是各写一个】
 * 两边看到的"自己的工资"必须是同一套数字。各写一个 VO 意味着两套组装逻辑，
 * 迟早出现"我看到的和他看到的差 200"——这种问题一旦发生，系统就不再被信任。
 * 差异只在"能查几个人"上，由数据范围控制，而不是由返回结构控制。
 *
 * <p>【为什么把比例和档位说明一起返回】
 * 员工只看到一个"6000"是没法核对对错的，只会怀疑系统。
 * 返回"业绩 15000 × 40% = 6000"，他自己按计算器就能验算，
 * 主管的答疑工作量会下降一个数量级。
 */
@Data
public class SalaryVO {

    private Long employeeId;
    private String empNo;
    private String employeeName;
    private String deptName;
    private String position;

    /** 工资期间 yyyy-MM */
    private String period;

    /** 本月业绩 = 本月到账回款产生的业绩（含冲销/调整），不是签单额 */
    private BigDecimal performance;

    /** 本月签单额，仅用于对照展示，不参与提成计算 */
    private BigDecimal signAmount;

    /** 命中的提成比例，万分比 */
    private Integer rateBp;

    /** 比例的中文展示，如 "45%" */
    private String rateText;

    /** 提成 = 业绩 × 比例 */
    private BigDecimal commission;

    private BigDecimal baseSalary;

    /** 员工级别码：NORMAL / ELITE / SUPERVISOR */
    private String empLevel;

    /** 级别中文名：普通员工 / 精英员工 / 主管 */
    private String empLevelLabel;

    /** 底薪来源：LEVEL=按级别政策 PERSONAL=个人指定 */
    private String baseSalarySource;

    private String baseSalarySourceLabel;

    /** 一句人话说明，例如「精英员工 · 按级别 2000.00」 */
    private String baseSalaryNote;

    /** 应发 = 底薪 + 提成 */
    private BigDecimal payable;

    /** 本次使用的算法：WHOLE / MARGINAL */
    private String tierMode;

    private String tierModeLabel;

    /** true = 本月业绩为负，提成为负值，需在后续月份扣回 */
    private Boolean clawback;

    /** 下一档业绩起点；已是最高档为 null */
    private BigDecimal nextTierAt;

    /** 距下一档还差多少业绩；已是最高档为 null */
    private BigDecimal gapToNextTier;

    /** 到达下一档起点后，提成会比现在多多少；已是最高档为 null */
    private BigDecimal gainIfReachNext;

    private Integer nextTierRateBp;
    private String nextTierRateText;

    /** 命中档位的说明文案，如 "月业绩 20000 ~ 25000：45%" */
    private String tierRemark;

    /**
     * 组装工资单。
     *
     * <p>【为什么用静态工厂而不是 MapStruct 之类的映射器】
     * 映射关系只有这一处、字段不多，手写最直观；引入映射框架后，
     * 加字段忘了配映射会静默丢字段，反而更难发现。
     */
    public static SalaryVO of(String period,
                              SalaryEmployeeRow row,
                              BigDecimal signAmount,
                              SalaryBreakdown bd,
                              String tierRemark,
                              BaseSalaryResolver.Resolution base) {
        SalaryVO vo = new SalaryVO();
        vo.setEmployeeId(row.employeeId());
        vo.setEmpNo(row.empNo());
        vo.setEmployeeName(row.name());
        vo.setDeptName(row.deptName());
        vo.setPosition(row.position());
        vo.setPeriod(period);

        vo.setPerformance(bd.performance());
        vo.setSignAmount(signAmount == null ? BigDecimal.ZERO : signAmount);

        vo.setRateBp(bd.rateBp());
        vo.setRateText(RatioUtil.bpToPercentText(bd.rateBp()));
        vo.setCommission(bd.commission());
        vo.setBaseSalary(bd.baseSalary());
        vo.setPayable(bd.payable());

        // 底薪是怎么来的，必须一并返回：只给一个数字，员工没法核对，主管每次都要口头解释
        EmpLevel level = base == null ? EmpLevel.NORMAL : base.level();
        vo.setEmpLevel(level.name());
        vo.setEmpLevelLabel(level.getLabel());
        if (base != null) {
            vo.setBaseSalarySource(base.source().name());
            vo.setBaseSalarySourceLabel(base.source().getLabel());
            vo.setBaseSalaryNote(String.format("%s · %s %s",
                    level.getLabel(), base.source().getLabel(), bd.baseSalary().toPlainString()));
        }

        vo.setTierMode(bd.mode().name());
        vo.setTierModeLabel(bd.mode().getLabel());
        vo.setClawback(bd.clawback());

        vo.setNextTierAt(bd.nextTierAt());
        vo.setGapToNextTier(bd.gapToNextTier());
        vo.setGainIfReachNext(bd.gainIfReachNext());
        vo.setNextTierRateBp(bd.nextTierRateBp());
        vo.setNextTierRateText(bd.nextTierRateBp() == null
                ? null : RatioUtil.bpToPercentText(bd.nextTierRateBp()));
        vo.setTierRemark(tierRemark);
        return vo;
    }
}
