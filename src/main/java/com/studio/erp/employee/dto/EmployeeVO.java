package com.studio.erp.employee.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.studio.erp.employee.domain.EmpLevel;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 员工列表 / 详情视图。
 *
 * <p>【为什么把账号信息揉进员工视图】
 * 管理员真正关心的问题是"谁还没有登录账号"、"谁的账号被停用了"。
 * 如果拆成员工页和账号页两张表，他要靠肉眼比对两个列表，
 * 而这件事每周都在发生（新人入职、有人离职）。合在一起，
 * 一个筛选条件（hasAccount=false）就能给出待办清单。
 *
 * <p>【为什么 roleCodes 不直接返回字符串】
 * SQL 里用 GROUP_CONCAT 把多个角色拼成一列，返回给前端时再拆成数组，
 * 前端可以直接做 v-for，不用自己 split。拼接字符串直接暴露给前端，
 * 会出现"前端按逗号分割，结果角色名里正好有逗号"这种事故。
 */
@Data
public class EmployeeVO {

    private Long id;

    private String empNo;

    private String name;

    private Long deptId;

    private String deptName;

    private String position;

    /** 员工级别码：NORMAL / ELITE / SUPERVISOR */
    private String empLevel;

    private Integer isDesigner;

    private String phone;

    private LocalDate hireDate;

    /** 1=在职 0=离职 */
    private Integer status;

    private LocalDateTime createdAt;

    /* ---------- 账号信息（未开账号时以下字段为 null） ---------- */

    private Long userId;

    private String username;

    /** 1=启用 0=停用 */
    private Integer accountStatus;

    private LocalDateTime lastLoginAt;

    /** GROUP_CONCAT 出来的原始串，如 "DESIGNER,SUPERVISOR"。仅内部使用，不返回前端 */
    @JsonIgnore
    private String roleCodes;

    /** 角色码数组，由 roleCodes 拆分而来 */
    public List<String> getRoles() {
        if (roleCodes == null || roleCodes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(roleCodes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * 级别中文名。
     *
     * <p>【为什么在服务端翻译而不是让前端查表】
     * 前端各自维护一份 "NORMAL→普通员工" 的映射，加一档就要改多处；
     * 而且一旦有人漏改，页面会直接显示英文字段码。
     * 库里存了脏值（手工改过数据）时退回原始码，至少不会 500。
     */
    public String getEmpLevelLabel() {
        try {
            return EmpLevel.parse(empLevel).getLabel();
        } catch (IllegalArgumentException e) {
            return empLevel;
        }
    }
}
