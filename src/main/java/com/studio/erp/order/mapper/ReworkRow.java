package com.studio.erp.order.mapper;

import java.time.LocalDateTime;

/**
 * 返工历史行（含返工人姓名）。
 *
 * <p>⚠️ 构造参数顺序必须与查询 SQL 的列顺序逐一对齐 —— MyBatis 对 record 是按位置映射的。
 */
public record ReworkRow(
        Long id,
        Long orderId,
        Long deliverableId,
        Integer fromVersion,
        String reason,
        Long reworkBy,
        String reworkByName,
        LocalDateTime createdAt) {
}
