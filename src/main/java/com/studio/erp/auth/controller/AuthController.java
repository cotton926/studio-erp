package com.studio.erp.auth.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.studio.erp.auth.dto.ChangePasswordRequest;
import com.studio.erp.auth.dto.CurrentUserVO;
import com.studio.erp.auth.dto.LoginRequest;
import com.studio.erp.auth.dto.LoginResponse;
import com.studio.erp.auth.service.AuthService;
import com.studio.erp.common.api.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 *
 * <p>【为什么登录失败也返回 HTTP 200】
 * 沿用全局约定：业务性失败（密码错、账号停用）统一 200 + code，
 * 前端一套拦截器处理。但"未登录/令牌失效"是传输层问题，返回 401，
 * 方便前端拦截器直接跳登录页而不用解析 code。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 登录 */
    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }

    /** 退出登录 */
    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }

    /**
     * 当前登录人。
     * 前端刷新页面后靠它重建菜单与按钮权限，不必把用户信息长期存在本地。
     */
    @GetMapping("/me")
    public R<CurrentUserVO> me() {
        return R.ok(authService.currentUserInfo(StpUtil.getLoginIdAsLong()));
    }

    /** 修改密码（首次登录强制走这里） */
    @PostMapping("/password")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(StpUtil.getLoginIdAsLong(), request);
        return R.ok();
    }
}
