package com.studio.erp.auth.dto;

/**
 * 重置密码结果。
 *
 * @param username  账号登录名
 * @param password  新的初始密码，只在这一刻可见
 */
public record PasswordResetResult(String username, String password) {
}
