package com.studio.erp.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studio.erp.order.entity.Deliverable;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DeliverableMapper extends BaseMapper<Deliverable> {

    /**
     * 版本列表（新的在前）。
     *
     * <p>带上传人姓名：设计稿是谁做的必须一眼看出来，否则改稿时找不到人问。
     */
    @Select("""
            SELECT d.id, d.order_id, d.order_item_id, d.version_no, d.file_name, d.file_url,
                   d.file_size, d.file_type, d.review_result, d.feedback,
                   d.uploaded_by, d.reviewed_by, d.reviewed_at, d.created_at,
                   e.name AS uploaded_by_name, r.name AS reviewed_by_name
              FROM deliverable d
              LEFT JOIN employee e ON e.id = d.uploaded_by
              LEFT JOIN employee r ON r.id = d.reviewed_by
             WHERE d.order_id = #{orderId}
             ORDER BY d.version_no DESC
            """)
    List<DeliverableRow> selectByOrder(@Param("orderId") Long orderId);

    /**
     * 取当前最大版本号。
     *
     * <p>不能 count(*)+1：删过一版之后会算出重复版本号，撞唯一键直接上传失败。
     */
    @Select("SELECT COALESCE(MAX(version_no), 0) FROM deliverable WHERE order_id = #{orderId}")
    int maxVersion(@Param("orderId") Long orderId);
}
