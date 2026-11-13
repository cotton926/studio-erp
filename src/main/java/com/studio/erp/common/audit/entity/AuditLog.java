package com.studio.erp.common.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计日志。
 *
 * <p>【为什么日志文件不够，必须落库】
 * logback 的日志会被轮转删掉（默认只留几十天），而且按行散落，想查"谁把乙的底薪从 1500 改成 3000"
 * 得靠 grep 撞运气。这张表按 biz_type + biz_id 建了索引，一条 SQL 就能拉出某个对象被谁动过几次。
 * 金额类系统里，这是发生争议时唯一拿得出手的证据。
 *
 * <p>【为什么不存密码、不存 token】
 * 审计表会被导出、被截图、被开发随手查。密码明文一旦进来就是事故，所以重置密码只记"已重置"。
 */
@Data
@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务类型：ORDER / EMPLOYEE / SYS_USER / SALARY_TIER ... */
    private String bizType;

    private Long bizId;

    /** 变更字段；整条记录型事件（如重置密码）留空 */
    private String fieldName;

    private String oldValue;

    private String newValue;

    /** 0 表示系统自动执行（如迁移回填），没有对应的登录人 */
    private Long operatorId;

    private String ip;

    private LocalDateTime createdAt;
}
