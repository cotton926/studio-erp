package com.studio.erp.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studio.erp.order.entity.ReworkLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReworkLogMapper extends BaseMapper<ReworkLog> {

    /**
     * 某单的返工历史（新的在前），详情页直接展示。
     *
     * <p>【列顺序必须与 ReworkRow 的构造参数顺序完全一致】
     * MyBatis 给 record 做自动映射时按"列位置 → 构造参数位置"对应。
     * 把 rework_by_name 写在 created_at 后面，就会拿中文姓名去构造 LocalDateTime，
     * 报 "Cannot convert string '系统管理员' to java.time.LocalDateTime" —— 而且只有存在返工记录时才报，
     * 空单一切正常，很难联想。新增列时请直接加在末尾，并同步 record。
     */
    @Select("""
            SELECT r.id, r.order_id, r.deliverable_id, r.from_version, r.reason, r.rework_by,
                   e.name AS rework_by_name, r.created_at
              FROM rework_log r
              LEFT JOIN employee e ON e.id = r.rework_by
             WHERE r.order_id = #{orderId}
             ORDER BY r.id DESC
            """)
    List<ReworkRow> selectByOrder(@Param("orderId") Long orderId);

    /**
     * 按人统计版本数与退回数。
     *
     * <p>用 uploaded_by 归人：返工是"这版没通过"，责任落在原稿人身上；
     * 谁去改的记在 rework_log.rework_by，用于另一种口径（谁在擦屁股）。
     */
    @Select("""
            SELECT d.uploaded_by AS employeeId, e.name AS employeeName,
                   COUNT(*) AS versions,
                   SUM(CASE WHEN d.review_result = 3 THEN 1 ELSE 0 END) AS rejected
              FROM deliverable d
              LEFT JOIN employee e ON e.id = d.uploaded_by
             WHERE d.created_at >= #{from} AND d.created_at < #{to}
             GROUP BY d.uploaded_by, e.name
             ORDER BY versions DESC
            """)
    List<ReworkStatRow> statByEmployee(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * 按人统计返工次数。
     *
     * <p>【为什么单独一条 SQL 而不是在聚合里套子查询】
     * MySQL 的 ONLY_FULL_GROUP_BY 下，聚合函数内部引用未分组列（d.id）会直接报错。
     * 拆成两条查询在 Java 里合并，逻辑一眼看得懂，也不会踩不同版本的严格模式差异。
     */
    @Select("""
            SELECT d.uploaded_by AS employeeId, e.name AS employeeName,
                   COUNT(*) AS reworks
              FROM rework_log r
              JOIN deliverable d ON d.id = r.deliverable_id
              LEFT JOIN employee e ON e.id = d.uploaded_by
             WHERE d.created_at >= #{from} AND d.created_at < #{to}
             GROUP BY d.uploaded_by, e.name
            """)
    List<ReworkCountRow> reworkCountByEmployee(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 全公司口径的返工次数（版本数与退回数由 statByEmployee 汇总而来，不重复查） */
    @Select("""
            SELECT COUNT(*) FROM rework_log r
              JOIN deliverable d ON d.id = r.deliverable_id
             WHERE d.created_at >= #{from} AND d.created_at < #{to}
            """)
    int countReworks(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
