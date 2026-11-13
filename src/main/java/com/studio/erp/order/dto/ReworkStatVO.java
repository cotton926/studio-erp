package com.studio.erp.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 返工统计。
 *
 * <p>【为什么单列"一次通过率"而不只报返工次数】
 * 返工次数是绝对量，大单多的设计师天然次数高；通过率是比例，
 * 才能横向比较谁的问题更多。两个一起看才不会被绝对量带偏。
 */
@Data
public class ReworkStatVO {

    private String period;

    /** 期间内上传的版本总数 */
    private int totalVersions;

    /** 其中被客户退回的版本数 */
    private int rejectedVersions;

    private int reworkCount;

    /** 一次通过率（%），保留 1 位小数 */
    private BigDecimal passRate;

    private List<PersonStat> persons;

    @Data
    public static class PersonStat {
        private Long employeeId;
        private String employeeName;
        private int versions;
        private int rejected;
        private int reworks;
        private BigDecimal passRate;
    }
}
