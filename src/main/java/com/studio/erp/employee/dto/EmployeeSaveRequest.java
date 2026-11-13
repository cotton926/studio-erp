package com.studio.erp.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 新增 / 修改员工。
 *
 * <p>【为什么新增和修改共用一个请求对象】
 * 两边要校验的字段完全一样。分成两个类之后，只要有人给"新增"加了字段校验
 * 却忘了改"修改"，就会出现"新建时手机号必填、修改时能清空"这类不一致，
 * 而这会导致数据质量慢慢劣化，报表上却看不出原因。
 *
 * <p>【这里为什么没有 status】
 * 在职/离职必须走独立接口（{@code PUT /employees/{id}/status}），
 * 因为它有副作用 —— 离职要同步停用登录账号。混在普通编辑里，
 * 前端很容易在保存表单时不带这个字段，或被误传成 0，
 * 造成"改了个手机号，人变成离职了"。
 */
@Data
public class EmployeeSaveRequest {

    @NotBlank(message = "工号不能为空")
    @Size(max = 20, message = "工号最长 20 位")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "工号只能包含字母、数字、下划线和短横线")
    private String empNo;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名最长 50 个字")
    private String name;

    private Long deptId;

    @NotBlank(message = "岗位不能为空")
    private String position;

    /**
     * 员工级别：NORMAL / ELITE / SUPERVISOR。
     *
     * <p>允许为 null —— 表示"按岗位自动推断"：主管/管理员归主管档，其余为普通员工。
     * 精英档必须人工指定，不自动给：默认给高一级的错，比默认给低一级贵得多。
     */
    private String empLevel;

    /**
     * 是否参与业绩分成。
     * 允许为 null —— 表示"按岗位自动推断"：设计师默认参与，其他岗位默认不参与。
     * 之所以保留手填，是因为主管自己出图、客服兼做设计的情况真实存在。
     */
    private Integer isDesigner;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    private LocalDate hireDate;
}
