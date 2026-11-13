package com.studio.erp.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求。
 *
 * <p>【为什么用 record】
 * 入参是纯数据载体，不该有 setter 也就不该被中途改写。
 * 另外 record 天然不可变，能避免"某处把密码字段清空后继续用"这类怪事。
 *
 * <p>【为什么限制长度上限】
 * BCrypt 对超长输入不做截断，但攻击者可以塞几百 KB 的字符串让服务端反复做
 * 昂贵的哈希运算 —— 这也是一种拒绝服务。64 位足够任何正常密码。
 */
public record LoginRequest(
        @NotBlank(message = "请输入用户名")
        @Size(max = 50, message = "用户名过长")
        String username,

        @NotBlank(message = "请输入密码")
        @Size(max = 64, message = "密码过长")
        String password) {
}
