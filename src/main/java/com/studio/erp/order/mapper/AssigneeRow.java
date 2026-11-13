package com.studio.erp.order.mapper;

import java.math.BigDecimal;

/**
 * 明细行分成人 + 姓名的查询结果。
 *
 * <p>【为什么带姓名】
 * 详情页要显示"张三 70% / 李四 30%"，如果只返回 employeeId，
 * 前端还得再查一次人员列表并在内存里匹配 —— 多一次请求、多一处可能对不上的逻辑。
 */
public record AssigneeRow(
        Long orderItemId,
        Long employeeId,
        String employeeName,
        Integer assigneeRole,
        Integer ratioBp) {
}
