package com.studio.erp.auth.controller;

import com.studio.erp.auth.RequireRole;
import com.studio.erp.auth.dto.AccountCreateRequest;
import com.studio.erp.auth.dto.AccountCreateResult;
import com.studio.erp.auth.dto.AccountQuery;
import com.studio.erp.auth.dto.AccountRolesRequest;
import com.studio.erp.auth.dto.AccountVO;
import com.studio.erp.auth.dto.PasswordResetResult;
import com.studio.erp.auth.dto.ResetPasswordRequest;
import com.studio.erp.auth.entity.SysRole;
import com.studio.erp.auth.service.AccountService;
import com.studio.erp.common.api.PageResult;
import com.studio.erp.common.api.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 账号管理接口。
 *
 * <p>【整类都只给 ADMIN】
 * 账号管理能重置别人的密码 —— 这条权限等价于"可以冒充任何人"。
 * 所以它不能和"看员工列表"共用权限：主管要看人名，但绝不能改别人的密码。
 *
 * <p>【返回的初始密码只出现一次】
 * 响应里带明文密码是必要的（管理员得把它转达给员工），
 * 但接口不提供"再查一次密码"的入口，数据库里也只有 BCrypt 哈希。
 * 忘了就只能再重置一次，这是设计上刻意接受的成本。
 */
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    @RequireRole("ADMIN")
    public R<PageResult<AccountVO>> page(AccountQuery query) {
        return R.ok(accountService.page(query));
    }

    /** 角色字典，供前端渲染角色复选框 */
    @GetMapping("/roles")
    @RequireRole("ADMIN")
    public R<List<SysRole>> roles() {
        return R.ok(accountService.listRoles());
    }

    /** 给员工开账号 */
    @PostMapping
    @RequireRole("ADMIN")
    public R<AccountCreateResult> create(@Valid @RequestBody AccountCreateRequest request) {
        return R.ok(accountService.create(request));
    }

    /**
     * 重置密码。
     *
     * <p>请求体可以不传（{@code required = false}）：管理员最常见的诉求是
     * "把密码废掉重发一个"，此时让系统生成比自己想一个更省事、也更安全。
     */
    @PostMapping("/{id}/reset-password")
    @RequireRole("ADMIN")
    public R<PasswordResetResult> resetPassword(@PathVariable Long id,
                                                @RequestBody(required = false) ResetPasswordRequest request) {
        return R.ok(accountService.resetPassword(id, request == null ? null : request.getNewPassword()));
    }

    /** 启用 / 停用 */
    @PutMapping("/{id}/status")
    @RequireRole("ADMIN")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        accountService.updateStatus(id, status);
        return R.ok();
    }

    /** 调整角色（整体覆盖） */
    @PutMapping("/{id}/roles")
    @RequireRole("ADMIN")
    public R<Void> updateRoles(@PathVariable Long id, @Valid @RequestBody AccountRolesRequest request) {
        accountService.updateRoles(id, request.getRoleCodes());
        return R.ok();
    }
}
