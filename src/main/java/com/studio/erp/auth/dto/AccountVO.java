package com.studio.erp.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 账号视图（账号管理页）。
 *
 * <p>【为什么这里也要带员工信息】
 * 账号列表如果只显示"admin / designer1 / designer2"，管理员根本不知道
 * 哪个账号对应哪个人，只能去员工表里反查。账号管理页的核心用途是
 * "给新人开号、给离职的人停号"，必须以人为中心展示。
 */
@Data
public class AccountVO {

    private Long id;

    private String username;

    private Long employeeId;

    private String employeeName;

    private String empNo;

    private String position;

    /** 1=启用 0=停用 */
    private Integer status;

    /** 1=还在用初始密码，尚未修改 */
    private Integer mustChangePwd;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;

    /** GROUP_CONCAT 原始串，不返回前端 */
    @JsonIgnore
    private String roleCodes;

    public List<String> getRoles() {
        if (roleCodes == null || roleCodes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(roleCodes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
