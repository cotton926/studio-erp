package com.studio.erp.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改密码请求。
 *
 * <p>【为什么必须校验旧密码】
 * 令牌被盗时，攻击者若能直接改密码，就等于永久接管账号并把主人踢下线。
 * 要求旧密码可以把这个窗口压到"仅当前会话"。
 */
public record ChangePasswordRequest(
        @NotBlank(message = "请输入当前密码")
        String oldPassword,

        @NotBlank(message = "请输入新密码")
        @Size(min = 8, max = 64, message = "新密码长度需在 8-64 位之间")
        String newPassword) {
}
