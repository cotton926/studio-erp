package com.studio.erp.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 稿件版本。
 *
 * <p>【为什么 file_name 和 file_url 分开存】
 * file_name 是设计师看到的原始名字（"画册初稿-第三版.psd"），file_url 是磁盘上实际路径。
 * 磁盘文件名必须用 UUID：中文名在 Windows/Linux 之间搬来搬去会乱码，
 * 同名文件互相覆盖更是直接丢稿。原始名只作展示，两者互不影响。
 */
@Data
@TableName("deliverable")
public class Deliverable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    /** 可空：整单稿件不挂具体明细行 */
    private Long orderItemId;

    /** V1/V2/V3...，同一单内唯一（uk_order_version 兜底） */
    private Integer versionNo;

    private String fileName;

    /** 相对 upload-dir 的路径，如 "12/9f3a....psd" */
    private String fileUrl;

    private Long fileSize;

    private String fileType;

    /** 1=待审 2=客户通过 3=客户退回 */
    private Integer reviewResult;

    private String feedback;

    private Long uploadedBy;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

    private LocalDateTime createdAt;
}
