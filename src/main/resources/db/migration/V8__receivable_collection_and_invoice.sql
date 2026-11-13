-- =============================================================================
-- V8：收款计划催收留痕 + 开票流程字段
--
-- 【这次改动的业务背景】
-- 1) 尾款不是"到期自动到账"，是要有人去催的。原来 receivable_plan 只有计划金额和日期，
--    没有"催过几次、客户答应哪天付"。缺了这两项，催收就退化成在 Excel 里记流水，
--    而 Excel 没人愿意维护 —— 所以催收记录必须进系统，且和计划行绑在一起。
-- 2) 原来 invoice 表只有金额和状态，没有抬头、税号、申请人、作废原因。
--    这些字段缺一个，财务都得回微信里问一次，开票就成了纯手工活。
--
-- 【为什么加字段而不是新建表】
-- 收款计划的分期结构（订金/尾款）已经在 receivable_plan 里了，
-- 新建一张 collection_task 会让"这一期到底收了没"要跨两张表对账。
-- 就地加字段，计划行永远是这个分期的唯一事实来源。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 一、收款计划：催收留痕
-- -----------------------------------------------------------------------------
ALTER TABLE receivable_plan
    ADD COLUMN remind_count   INT           NOT NULL DEFAULT 0  COMMENT '累计催收次数' AFTER status,
    ADD COLUMN last_remind_at DATETIME      NULL                COMMENT '最近一次催收时间' AFTER remind_count,
    -- 【为什么承诺日和承诺金额要存在计划行上，而不是只记在流水里】
    -- 催收清单需要一眼看到"客户说 12-05 付 5000"，这个值必须能直接排序/筛逾期，
    -- 每次从流水表取最后一条会多一层聚合，且容易取错（多条流水同一时间）。
    -- 流水表保留完整历史，计划行只留"最新承诺"。
    ADD COLUMN promise_date   DATE          NULL                COMMENT '客户承诺回款日期' AFTER last_remind_at,
    ADD COLUMN promise_amount DECIMAL(12,2) NULL                COMMENT '客户承诺回款金额' AFTER promise_date;

-- 催收清单按"未收讫 + 应收日"扫，补一条联合索引
ALTER TABLE receivable_plan
    ADD KEY idx_open_plan_date (status, plan_date);

-- -----------------------------------------------------------------------------
-- 二、催收记录
--
-- 【为什么记录方式和结果要分开存】
-- "打了电话"和"客户答应付"是两件事：只记结果就不知道客服到底做了什么，
-- 只记方式就不知道这通电话有没有用。两个字段都留，才能统计"哪种方式有效"。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS collection_log;
CREATE TABLE collection_log (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    order_id       BIGINT        NOT NULL COMMENT '订单ID（冗余，便于按单查全部催收历史）',
    plan_id        BIGINT        NULL     COMMENT '对应的收款计划期次；为空表示针对整单催收',
    action         TINYINT       NOT NULL DEFAULT 1 COMMENT '催收方式：1=电话 2=微信/QQ 3=邮件 4=上门 5=其他',
    result         TINYINT       NOT NULL DEFAULT 1 COMMENT '催收结果：1=承诺付款 2=推诿 3=已付款 4=失联 5=需内部协调',
    promise_date   DATE          NULL     COMMENT '客户承诺的回款日期',
    promise_amount DECIMAL(12,2) NULL     COMMENT '客户承诺的回款金额',
    remark         VARCHAR(300)  NULL     COMMENT '沟通要点，如「财务在走流程，下周付」',
    operator_id    BIGINT        NOT NULL COMMENT '催收人',
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_order_time (order_id, created_at),
    KEY idx_plan (plan_id),
    -- 按月统计"催了多少次"要按时间扫
    KEY idx_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '催收记录（每次催款的留痕，含客户承诺）';

-- -----------------------------------------------------------------------------
-- 三、开票：抬头 / 申请 / 作废
-- -----------------------------------------------------------------------------
ALTER TABLE invoice
    -- 抬头必须单独存：订单里的 client_name 是手填简称（"青禾文化"），
    -- 而发票抬头必须是营业执照全称（"杭州青禾文化创意有限公司"），两者不能混用。
    ADD COLUMN title       VARCHAR(120)  NULL COMMENT '发票抬头（企业全称，默认取订单客户名）' AFTER order_id,
    ADD COLUMN tax_no      VARCHAR(50)   NULL COMMENT '纳税人识别号' AFTER title,
    ADD COLUMN content     VARCHAR(120)  NULL COMMENT '开票内容/品名，如「设计服务费」' AFTER invoice_type,
    ADD COLUMN apply_by    BIGINT        NULL COMMENT '申请人' AFTER status,
    ADD COLUMN applied_at  DATETIME      NULL COMMENT '申请时间' AFTER apply_by,
    ADD COLUMN void_reason VARCHAR(200)  NULL COMMENT '作废原因（作废必须填，否则无法解释为什么这张票不算数）' AFTER applied_at,
    ADD KEY idx_status_date (status, issue_date);

-- 发票号唯一：同一张票号不可能开两次。
-- 允许 NULL —— 待开状态下还没票号，NULL 不参与唯一性约束。
ALTER TABLE invoice
    ADD UNIQUE KEY uk_invoice_no (invoice_no);

-- -----------------------------------------------------------------------------
-- 四、历史数据回填：为存量订单生成收款计划
--
-- 【为什么要回填】
-- 新逻辑只在"定价"时生成计划，而库里已有的单不会再走一次定价。
-- 不回填的话，催收页上线第一天是空的，主管会认为功能没做好。
-- 回填口径与代码一致：订金 30% 于签单日，尾款 70% 于交付月月末。
-- -----------------------------------------------------------------------------
INSERT INTO receivable_plan (order_id, seq, plan_name, plan_amount, plan_date, status, remark)
SELECT o.id,
       1,
       '订金',
       ROUND(o.total_amount * 0.30, 2),
       COALESCE(o.sign_date, DATE(o.created_at)),
       1,
       '系统初始化：按 30% 自动生成'
  FROM sales_order o
 WHERE o.deleted = 0
   AND o.total_amount > 0
   AND o.status IN (2, 3, 4)
   AND NOT EXISTS (SELECT 1 FROM receivable_plan p WHERE p.order_id = o.id);

INSERT INTO receivable_plan (order_id, seq, plan_name, plan_amount, plan_date, status, remark)
SELECT o.id,
       2,
       '尾款',
       o.total_amount - ROUND(o.total_amount * 0.30, 2),
       LAST_DAY(COALESCE(o.actual_deliver_date, o.sign_date, DATE(o.created_at))),
       1,
       '系统初始化：余款于交付月月末到期，月末最后三天催收'
  FROM sales_order o
 WHERE o.deleted = 0
   AND o.total_amount > 0
   AND o.status IN (2, 3, 4)
   AND NOT EXISTS (SELECT 1 FROM receivable_plan p WHERE p.order_id = o.id AND p.seq = 2);

-- 已收金额回填：把订单级回款按期次顺序"从前往后灌"。
-- 【为什么用纯 SQL 而不是写个循环脚本】一次性完成，迁移可重放；
-- 逻辑与 Java 侧 recalc 完全一致：某期已收 = 总回款 - 前面各期应收之和（下限 0、上限本期应收）。
-- 前面各期之和用关联子查询算 —— 每张单只有两期，子查询开销可以忽略，
-- 换窗口函数（ROWS BETWEEN）反而更难读。
DROP TEMPORARY TABLE IF EXISTS tmp_plan_received;
CREATE TEMPORARY TABLE tmp_plan_received (
    id       BIGINT        NOT NULL PRIMARY KEY,
    received DECIMAL(12,2) NOT NULL
);

INSERT INTO tmp_plan_received (id, received)
SELECT r.id,
       LEAST(r.plan_amount,
             GREATEST(COALESCE(pay.paid, 0)
                      - COALESCE((SELECT SUM(r2.plan_amount)
                                    FROM receivable_plan r2
                                   WHERE r2.order_id = r.order_id
                                     AND r2.seq < r.seq), 0), 0))
  FROM receivable_plan r
  JOIN (SELECT pa.order_id, SUM(pa.amount) AS paid
          FROM payment_allocation pa
          JOIN payment pm ON pm.id = pa.payment_id AND pm.status <> 3
         GROUP BY pa.order_id) pay ON pay.order_id = r.order_id;

UPDATE receivable_plan rp
  JOIN tmp_plan_received t ON t.id = rp.id
   SET rp.received_amount = t.received,
       rp.status = CASE
                       WHEN t.received >= rp.plan_amount THEN 3
                       WHEN t.received > 0 THEN 2
                       ELSE 1
                   END;

DROP TEMPORARY TABLE tmp_plan_received;
