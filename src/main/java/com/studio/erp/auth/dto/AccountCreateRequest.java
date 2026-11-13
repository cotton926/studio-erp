package com.studio.erp.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 给员工开登录账号。
 *
 * <p>【为什么不复用员工新增接口】
 * 员工档案和登录账号是两件事：先录人事信息（可能人还没到岗），
 * 等人来上班再开号；也有人只有档案不需要账号（比如只做外发的兼职）。
 * 强行合成一个接口的后果是"想开个号，得重填一遍员工信息"。
 */
@Data
public class AccountCreateRequest {

    @NotNull(message = "请选择员工")
    private Long employeeId;

    /**
     * 登录名。为空时默认取工号 —— 国内公司最常见的做法就是工号即账号，
     * 员工不用记两套东西。这里允许自定义，是为了兼容"英文名登录"的习惯。
     */
    @Size(max = 50, message = "登录名最长 50 位")
    @Pattern(regexp = "^$|^[A-Za-z][A-Za-z0-9_.-]{2,29}$",
            message = "登录名须以字母开头，3~30 位，只能含字母、数字、下划线、点和短横线")
    private String username;

    /** 角色码。为空时按员工岗位自动推导，管理员可在界面上改 */
    private List<String> roleCodes;

    /**
     * 初始密码。为空则由系统生成随机密码并只返回一次。
     * 允许管理员指定，是因为有些公司要求"统一初始密码"以便批量告知；
     * 但无论哪种方式，建号后都强制首次登录修改。
     */
    private String initialPassword;
}
