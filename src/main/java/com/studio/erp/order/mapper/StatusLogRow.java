package com.studio.erp.order.mapper;

import java.time.LocalDateTime;

/**
 * 状态时间线的一行（含操作人姓名）。
 *
 * <p>用 record 而不是实体：这是"查询投影"，不需要写回，也不该被 MyBatis-Plus 当成表映射。
 *
 * <p>⚠️ 构造参数顺序必须与查询 SQL 的列顺序逐一对齐 —— MyBatis 对 record 是按位置映射的。
 */
public record StatusLogRow(
        Long id,
        Integer fromStatus,
        Integer toStatus,
        Long operatorId,
        String operatorName,
        String remark,
        LocalDateTime createdAt) {
}
