package com.studio.erp.auth.dto;

/**
 * 登录成功响应。
 *
 * <p>【为什么把 tokenName 也返回】
 * 前端要在请求头里带上 `studio-token: xxx`。把名字由后端下发，
 * 将来改配置（比如换成 Authorization）时前端不用跟着改代码。
 *
 * @param token         令牌值，前端存 sessionStorage 或内存
 * @param tokenName     请求头名称
 * @param mustChangePwd 是否必须先改密码，前端据此强制弹窗
 * @param user          当前登录人信息
 */
public record LoginResponse(
        String token,
        String tokenName,
        boolean mustChangePwd,
        CurrentUserVO user) {
}
