package com.studio.erp.order.mapper;

import lombok.Data;

/** 返工次数（按人），理由同 {@link ReworkStatRow}：统计行用 setter 映射 */
@Data
public class ReworkCountRow {

    private Long employeeId;

    private String employeeName;

    private int reworks;
}
