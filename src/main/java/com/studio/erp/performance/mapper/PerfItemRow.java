package com.studio.erp.performance.mapper;

import java.math.BigDecimal;

/**
 * 明细行 + 分成人 的扁平查询结果。
 *
 * <p>【为什么用扁平结构而不是带集合的嵌套对象】
 * MyBatis 做嵌套集合需要 resultMap + collection 标签，配置冗长且容易写错；
 * 而"一次查出扁平行、在 Java 里分组"逻辑更直白，人也看得懂，出问题好排查。
 * 单量最多几十行，内存分组的开销可以忽略。
 *
 * @param itemId     明细行ID
 * @param itemName   品项名
 * @param amount     行金额
 * @param itemType   1正常 2赠品 3返工
 * @param employeeId 分成人ID；该行没有分成人时为 null
 * @param ratioBp    分成比例，万分比
 */
public record PerfItemRow(
        Long itemId,
        String itemName,
        BigDecimal amount,
        Integer itemType,
        Long employeeId,
        Integer ratioBp) {
}
