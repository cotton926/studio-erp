package com.studio.erp.performance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 业绩流水实体。
 *
 * <p>【为什么这张表只追加、不修改、不删除】
 * 员工对系统最基本的信任来自"数字能查到底"。改一条流水，历史就对不上了；
 * 正确的做法永远是再追加一条反向流水（冲销／调整），让两条记录互相印证。
 */
@Data
@TableName("performance_ledger")
public class PerformanceLedger {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long employeeId;

    private Long orderId;

    private Long orderItemId;

    /** 1=签单额 2=业绩确认 3=冲销 4=人工调整 */
    private Integer ledgerType;

    /** 业绩金额，可为负（冲销用） */
    private BigDecimal amount;

    /** 分成比例快照，万分比 */
    private Integer ratioBp;

    /** 归属期间 yyyy-MM；与 occurDate 分开存，因为存在跨月调整归属 */
    private String period;

    private LocalDate occurDate;

    /** 1=订单 2=回款核销 3=人工 */
    private Integer sourceType;

    private Long sourceId;

    /** 幂等键，数据库唯一索引，重复插入直接失败 —— 这是防重复计业绩的最后一道防线 */
    private String idemKey;

    private String remark;

    private Long createdBy;

    private LocalDateTime createdAt;
}
