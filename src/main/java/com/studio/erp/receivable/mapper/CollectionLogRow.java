package com.studio.erp.receivable.mapper;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 催收记录行（含催收人姓名）。
 *
 * <p>用普通类 + setter 映射，理由见 {@link CollectionRow}。
 */
@Data
public class CollectionLogRow {

    private Long id;
    private Long orderId;
    private Long planId;
    private Integer action;
    private Integer result;
    private LocalDate promiseDate;
    private BigDecimal promiseAmount;
    private String remark;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime createdAt;
}
