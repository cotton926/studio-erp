package com.studio.erp.order.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 稿件版本（含该版本产生的返工记录） */
@Data
public class DeliverableVO {

    private Long id;

    private Long orderItemId;

    private Integer versionNo;

    /** V1 / V2，前端直接显示 */
    private String versionText;

    private String fileName;

    private String fileType;

    private Long fileSize;

    /** 1.2 MB，避免前端各自实现一套换算 */
    private String fileSizeText;

    private Integer reviewResult;

    private String reviewResultText;

    private String feedback;

    private Long uploadedBy;

    private String uploadedByName;

    private String reviewedByName;

    private LocalDateTime reviewedAt;

    private LocalDateTime createdAt;

    /** 这一版被退回后产生的返工（通常 0 或 1 条） */
    private List<ReworkVO> reworks;

    @Data
    public static class ReworkVO {
        private Long id;
        private String reason;
        private String reworkByName;
        private LocalDateTime createdAt;
    }
}
