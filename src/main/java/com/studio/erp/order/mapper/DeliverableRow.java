package com.studio.erp.order.mapper;

import java.time.LocalDateTime;

/**
 * 稿件版本列表行（含上传人/审核人姓名）。
 *
 * <p>⚠️ 构造参数顺序必须与查询 SQL 的列顺序逐一对齐 —— MyBatis 对 record 是按位置映射的。
 */
public record DeliverableRow(
        Long id,
        Long orderId,
        Long orderItemId,
        Integer versionNo,
        String fileName,
        String fileUrl,
        Long fileSize,
        String fileType,
        Integer reviewResult,
        String feedback,
        Long uploadedBy,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        String uploadedByName,
        String reviewedByName) {
}
