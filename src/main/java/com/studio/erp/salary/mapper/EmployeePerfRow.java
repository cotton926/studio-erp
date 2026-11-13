package com.studio.erp.salary.mapper;

import java.math.BigDecimal;

/**
 * 某员工在某期间的业绩合计。
 *
 * <p>【为什么只返回合计，不返回明细】
 * 工资核算只需要"这个月这个人的业绩是多少"这一个数。
 * 明细在业绩页已经能查，工资页再查一遍是重复实现，
 * 而且一旦两处口径写得不完全一致，就会出现"业绩页 12000、工资页 11800"的信任危机。
 *
 * <p>注意：列的别名顺序必须与 record 组件顺序一致 —— MyBatis 对 record 走
 * 构造器映射，字段名对不上时不会报错，只会静默给 null，这类问题最难查。
 */
public record EmployeePerfRow(Long employeeId, BigDecimal amount) {
}
