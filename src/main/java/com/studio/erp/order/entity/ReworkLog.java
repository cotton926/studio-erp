package com.studio.erp.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 返工记录（只增不改）。
 *
 * <p>【为什么不参与业绩计算】
 * 返工没有新收入，却真实消耗工时。把它算进业绩会虚增签单额，
 * 不算又看不见成本 —— 正确做法是让它成为独立指标（返工率），
 * 用来看趋势和分配，而不是直接折算成钱。
 */
@Data
@TableName("rework_log")
public class ReworkLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    /** 被退回的那个版本 */
    private Long deliverableId;

    /** 退回时的版本号，冗余存一份：日后稿件记录被清理，返工历史仍然可读 */
    private Integer fromVersion;

    private String reason;

    /** 实际返工的人（换人接手时与原稿人不同） */
    private Long reworkBy;

    private LocalDateTime createdAt;
}
