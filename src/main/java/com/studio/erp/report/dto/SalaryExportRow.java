package com.studio.erp.report.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ContentStyle;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 工资表导出行。
 *
 * <p>【为什么导出不复用 SalaryVO】
 * VO 是给页面用的：字段多（下一档差多少、档位说明、底薪来源码值），
 * 有些是给前端做交互的（nextTierRateBp）。直接导出去，表格会有 20 多列，
 * 财务第一眼就找不到"应发"在哪。导出是给"看表格的人"用的，列要少而准。
 *
 * <p>【底薪来源为什么保留成文字】月底核对时最常见的争议是"为什么他 2000 我 1500"，
 * 表格里直接写"精英员工 · 按级别"，比只给数字少一轮解释。
 */
@Data
public class SalaryExportRow {

    @ExcelProperty("工号")
    private String empNo;

    @ExcelProperty("姓名")
    private String employeeName;

    @ExcelProperty("部门")
    private String deptName;

    @ExcelProperty("级别")
    private String empLevelLabel;

    @ExcelProperty("本月业绩")
    @ContentStyle(dataFormat = 4)
    private BigDecimal performance;

    @ExcelProperty("提成比例")
    private String rateText;

    @ExcelProperty("提成")
    @ContentStyle(dataFormat = 4)
    private BigDecimal commission;

    @ExcelProperty("底薪")
    @ContentStyle(dataFormat = 4)
    private BigDecimal baseSalary;

    @ExcelProperty("底薪来源")
    private String baseSalaryNote;

    @ExcelProperty("应发合计")
    @ContentStyle(dataFormat = 4)
    private BigDecimal payable;

    /** 算法说明：跳点/累进，以及本月业绩落在哪一档 —— 事后复核时能自证 */
    @ExcelProperty("计算口径")
    private String tierRemark;

    @ExcelProperty("工资期间")
    private String period;
}
