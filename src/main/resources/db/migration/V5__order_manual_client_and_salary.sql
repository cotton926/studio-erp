-- =====================================================================
-- V5：跳过客户域与价目表；接入「工资按业绩阶梯比例核算」
--
-- 【为什么要把客户做成手填而不是建客户档案】
-- V1 里 sales_order.client_id 是 NOT NULL，依赖 client 表。但如果先做客户档案，
-- 客服提一张单之前必须先把客户建档、维护联系人、维护专属价，流程一步没走完就下不了单。
-- 实际沟通结果是：客户名称就是一句话的事，直接在单据上手填最快。
-- 做法是把 client_id 放开为可空、补一个 client_name 快照列，客户表保留不用。
-- 会出什么问题：同名客户可能写法不一致（"某某科技"和"某某科技有限公司"），
--   导致按客户统计时被拆成两个主体。
-- 怎么解决：本期用 client_name + 索引做模糊归并，等客户档案真正上线时
--   由主管用「历史订单归属客户」批量绑定，而不是现在逼所有人先建档。
--
-- 【为什么单价也手填】
-- 价目表的价值是"同品项同价、避免主管乱报价"。但设计公司报价高度个案化
-- （同一个"画册设计"，16 页和 48 页不是一个价），前期强行上价目表只会带来
-- "建了一堆价目项，每单还是手改"的负担。order_item.unit_price 本来就是快照列，
-- 手填后直接固化成历史，业绩不会因为以后改价而漂移。
-- 代价是失去了"报价一致性校验"，所以补了主管定价权限 + 明细留痕来兜底。
-- =====================================================================

-- ---------- 1. 订单：客户名称手填 ----------
ALTER TABLE sales_order
    MODIFY COLUMN client_id BIGINT NULL
        COMMENT '客户ID；客户域本期不做，此列为空',
    ADD COLUMN client_name VARCHAR(60) NOT NULL DEFAULT ''
        COMMENT '客户名称（客服提单时手填；不建客户档案，故此处存文本快照）' AFTER client_id,
    -- 按客户名查历史订单是客服最高频的动作（"这家上次做的多少钱"），
    -- 没有索引会随订单量增长逐月变慢
    ADD KEY idx_client_name (client_name);

-- ---------- 2. 回款：同样只存客户名 ----------
ALTER TABLE payment
    MODIFY COLUMN client_id BIGINT NULL
        COMMENT '客户ID；客户域本期不做，此列为空',
    ADD COLUMN client_name VARCHAR(60) NOT NULL DEFAULT ''
        COMMENT '客户名称快照（取自订单，保证与订单口径一致）' AFTER client_id;

-- ---------- 3. 员工：底薪 ----------
-- 【为什么要加底薪，而工资不是纯按比例】
-- 需求是"按业绩阶梯比例发放"，但真实场景里总会有 0 元业绩的月份（新人、长假、内勤）。
-- 若模型里没有底薪这一列，那些月份就只能发出 0 元工资，系统上线第一个月就会出问题。
-- 底薪默认 0：纯提成制公司填 0 即可，不影响任何计算；
-- 有底薪的公司填进去，应发 = 底薪 + 提成，模型不用改。
ALTER TABLE employee
    ADD COLUMN base_salary DECIMAL(12,2) NOT NULL DEFAULT 0.00
        COMMENT '月底薪；纯提成制填 0。应发工资 = base_salary + 业绩×阶梯比例' AFTER position;

-- ---------- 4. 工资阶梯表 ----------
-- 【为什么阶梯要进数据库，而不是写成代码里的 if-else 或枚举】
-- 阶梯比例是最容易被调整的东西：今年 40/45/50，明年可能变 38/43/48，
-- 或者加一档"30000 以上 55%"。写死在代码里意味着每改一次就要改代码、重新编译、发版，
-- 财务等不了，最后变成"先在 Excel 算，系统下个版本再改"——系统就此被绕过。
-- 做成表 + 管理接口，主管自己就能改，改完立即生效。
--
-- 【rate_bp 为什么用整数万分比】
-- 40% 存成 4000。理由同 order_item_assignee.ratio_bp：
-- 0.4 在 double 下不精确，乘出来的金额会出现 5999.999999999 这种值，
-- 而工资是要打到卡上的数字，一位都不能错。
CREATE TABLE salary_tier (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    level_no    INT           NOT NULL COMMENT '档位序号，从 1 开始，仅用于展示与排序',
    -- 【区间为什么是左闭右开】min_amount <= 业绩 < max_amount。
    -- 若用闭区间，15000 会同时落在第 1 档和第 2 档，到底按哪个比例？
    -- 左闭右开让每个数值只属于一个档，边界无歧义。
    min_amount  DECIMAL(12,2) NOT NULL COMMENT '业绩下限（含）',
    max_amount  DECIMAL(12,2) NULL     COMMENT '业绩上限（不含）；NULL = 无上限，即最高档',
    rate_bp     INT           NOT NULL COMMENT '该档提成比例，万分比：4000 = 40%',
    remark      VARCHAR(200)  NULL     COMMENT '档位说明，直接展示给员工看，避免"为什么我这个月是 45%"的追问',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_min_amount (min_amount),
    -- 上限必须大于下限，否则这一档永远取不到值，等于凭空少了一档
    CONSTRAINT ck_tier_range CHECK (max_amount IS NULL OR max_amount > min_amount),
    CONSTRAINT ck_tier_rate  CHECK (rate_bp >= 0 AND rate_bp <= 10000)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '工资提成阶梯（业绩 → 提成比例）';

-- 【种子数据为什么是这三档】
-- 需求原话是"业绩在 15000 以下是 40%，20000 是 45%，25000 是 50%"。
-- 这三句话能同时成立的唯一分档是：0~20000 是 40%，20000~25000 是 45%，25000 以上 50%。
-- （若把 15000 当成分界点，那"20000 是 45%"就不成立了，因为 20000 会落在 50% 档。）
-- 即：20000 是第一次跳点，25000 是第二次跳点，15000 只是"确定还在 40% 档"的参考线。
-- 如果这不是本意，直接改这张表的三行数据即可，代码不用动、不用重启。
INSERT INTO salary_tier (level_no, min_amount, max_amount, rate_bp, remark) VALUES
    (1,      0.00, 20000.00, 4000, '月业绩 20000 以下：40%'),
    (2,  20000.00, 25000.00, 4500, '月业绩 20000 ~ 25000：45%'),
    (3,  25000.00,     NULL, 5000, '月业绩 25000 以上：50%');
