package com.studio.erp.employee.dto;

/**
 * 部门下拉项。
 *
 * <p>用 record 而不是普通类：它只由 Java 组装、不经过 MyBatis 映射，
 * 不存在 OGNL 读不到属性的问题，record 更省样板且天然不可变。
 *
 * @param id       部门ID
 * @param name     部门名
 * @param parentId 上级部门，0 为顶级
 * @param sortNo   排序号
 */
public record DeptVO(Long id, String name, Long parentId, Integer sortNo) {
}
