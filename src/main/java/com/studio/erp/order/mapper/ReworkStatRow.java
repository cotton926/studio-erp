package com.studio.erp.order.mapper;

import lombok.Data;

/**
 * 按人统计的稿件质量行。
 *
 * <p>versions = 期间内上传的版本数；rejected = 其中被客户退回的版本数。
 *
 * <p>【为什么这里用普通类而不是 record】
 * MyBatis 对 record 走"按位置构造"，一旦 record 里带了额外方法（比如算通过率），
 * 构造参数个数与结果集列数的判断就会错位，报 IndexOutOfBoundsException。
 * 统计行用 setter 按列名映射最稳，别为省几行代码在这里赌框架行为。
 */
@Data
public class ReworkStatRow {

    private Long employeeId;

    private String employeeName;

    private int versions;

    private int rejected;
}
