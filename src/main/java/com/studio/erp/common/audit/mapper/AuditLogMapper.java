package com.studio.erp.common.audit.mapper;

import com.studio.erp.common.audit.entity.AuditLog;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    /**
     * 查某个对象的变更轨迹（新的在前）。
     *
     * <p>【为什么限制 200 条】内部系统里单个订单/员工的变更次数是几十级，
     * 但一张被脚本刷过的表可能上万条。不设上限，前端一打开抽屉就卡住。
     */
    @Select("""
            SELECT id, biz_type, biz_id, field_name, old_value, new_value, operator_id, ip, created_at
              FROM audit_log
             WHERE biz_type = #{bizType} AND biz_id = #{bizId}
             ORDER BY id DESC
             LIMIT 200
            """)
    List<AuditLog> selectTrace(@Param("bizType") String bizType, @Param("bizId") Long bizId);
}
