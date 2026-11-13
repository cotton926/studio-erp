package com.studio.erp.receivable.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studio.erp.receivable.entity.CollectionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 催收记录 Mapper。
 */
@Mapper
public interface CollectionLogMapper extends BaseMapper<CollectionLog> {

    /**
     * 某张订单的全部催收历史，按时间倒序。
     *
     * <p>倒序是因为业务上几乎总是看"最近一次怎么说的"，
     * 最老的那条永远在最下面 —— 顺排时用户每次都要滚到底。
     */
    @Select("""
            SELECT c.id,
                   c.order_id       AS orderId,
                   c.plan_id        AS planId,
                   c.action         AS action,
                   c.result         AS result,
                   c.promise_date   AS promiseDate,
                   c.promise_amount AS promiseAmount,
                   c.remark         AS remark,
                   c.operator_id    AS operatorId,
                   e.name           AS operatorName,
                   c.created_at     AS createdAt
              FROM collection_log c
              LEFT JOIN employee e ON e.id = c.operator_id
             WHERE c.order_id = #{orderId}
             ORDER BY c.id DESC
            """)
    List<CollectionLogRow> selectByOrder(@Param("orderId") Long orderId);
}
