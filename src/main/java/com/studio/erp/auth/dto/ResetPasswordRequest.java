package com.studio.erp.auth.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重置密码请求。
 *
 * <p>newPassword 可以完全不传（连请求体都不传也行），此时由系统生成随机密码。
 * 这是刻意设计：管理员大多数时候并不想"替员工想一个密码"，
 * 只想把对方的密码废掉重发一个。
 */
@Data
public class ResetPasswordRequest {

    @Size(max = 64, message = "密码最长 64 位")
    private String newPassword;
}
