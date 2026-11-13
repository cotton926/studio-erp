package com.studio.erp.auth.dto;

/**
 * 建号结果。
 *
 * @param userId          新账号ID
 * @param username        最终使用的登录名（申请时留空则取工号，前端要按返回值显示）
 * @param employeeName    员工姓名，用于"已为 张三 创建账号"这类提示
 * @param initialPassword 初始密码，**只在这一次响应里出现**
 */
public record AccountCreateResult(
        Long userId,
        String username,
        String employeeName,
        String initialPassword) {
}
