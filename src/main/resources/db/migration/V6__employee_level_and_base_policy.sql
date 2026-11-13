-- =====================================================================
-- V6：员工级别 + 分级底薪政策
--
-- 【为什么底薪要跟"级别"走，而不是一人一填】
-- 老板给的口径是三档：普通员工 1500、精英员工 2000、主管 3000。
-- 如果只留 employee.base_salary 一列让人手填，会遇到三个必然会发生的麻烦：
--   1) 招人时忘了填 —— 默认 0，工资直接少 1500，而且要到发薪日才发现；
--   2) 调薪政策时（比如全员 +200）要挨个改几十行，改漏一个没人知道；
--   3) 两个人级别一样但底薪不同，查不出是"填错"还是"特批"。
-- 做成"级别 → 底薪"的政策表后，绝大多数人只挂一个级别，底薪自动带出；
-- 真有个别谈好特殊待遇的，用 employee.base_salary 单独覆盖。
--
-- 【为什么级别不复用 position（岗位）】
-- position 回答的是"干什么活"（设计 / 客服 / 财务），emp_level 回答的是"哪一档"（普通 / 精英 / 主管）。
-- 这两件事互相独立：一个设计师可以是精英，一个客服也可以是精英；反过来，
-- 主管岗位的人通常就是主管档，但也有"代主管"只拿精英档的情况。
-- 硬塞进同一列，日后加一档"资深"就要动所有按岗位判断的代码（派单、数据权限都在用 position）。
--
-- 【会出什么问题】
-- 建了级别却没人维护，所有人都是默认的 NORMAL —— 数据看起来合法，实际全错。
-- 怎么解决：迁移时按现有 position 回填（主管/管理员 → SUPERVISOR），
-- 并且员工新增页把"级别"设成必选（不给默认值），入职录入时就定下来。
-- =====================================================================

-- ---------- 1. 员工：级别 ----------
ALTER TABLE employee
    ADD COLUMN emp_level VARCHAR(20) NOT NULL DEFAULT 'NORMAL'
        COMMENT '员工级别：NORMAL=普通员工 ELITE=精英员工 SUPERVISOR=主管；决定默认底薪' AFTER position,
    -- 按级别筛查（"精英有几个""主管档一共多少人"）是调薪前的常规动作
    ADD KEY idx_emp_level (emp_level);

-- 回填：现有主管与管理员归到主管档，其余保持普通员工。
-- 这里不动 position 本身 —— 岗位是人事编制，级别是薪酬档，两套口径分开存。
UPDATE employee SET emp_level = 'SUPERVISOR' WHERE position IN ('SUPERVISOR', 'ADMIN');

-- ---------- 2. 员工：底薪改为"个人覆盖值" ----------
-- 【语义变更：0 不再表示"没有底薪"，NULL 才表示"按级别自动取"】
-- 原来 base_salary DEFAULT 0 是"纯提成制"的表达。现在底薪有了级别兜底，
-- 再用 0 当默认值就会出现"所有人都拿 0 元底薪"的假象，把政策表架空。
-- 改成可空后：
--   NULL      → 跟随 salary_base_policy 里该级别的金额（99% 的人属于这种）
--   非 NULL   → 个人特例，按这个数发（谈好的、实习期打折的）
-- 用 NULL 而不是 0 表达"未设置"，是因为 0 本身也可能是合法的特例值；
-- 两者混用就再也分不清"没填"和"故意填 0"。
ALTER TABLE employee
    MODIFY COLUMN base_salary DECIMAL(12,2) NULL DEFAULT NULL
        COMMENT '个人指定底薪；NULL=按 emp_level 从 salary_base_policy 取。应发 = 实际底薪 + 业绩×阶梯比例';

-- 存量数据全部是默认的 0（V5 刚加的列，业务上还没人维护过），统一置空走级别政策
UPDATE employee SET base_salary = NULL WHERE base_salary = 0.00;

-- 覆盖值不允许是负数：负数底薪等于公司倒扣钱，只能是录入错误
ALTER TABLE employee
    ADD CONSTRAINT ck_emp_base_salary CHECK (base_salary IS NULL OR base_salary >= 0);

-- ---------- 3. 分级底薪政策表 ----------
-- 【为什么也进表，而不是写死成常量】
-- 与 salary_tier 同理：底薪是全公司最常被调整的数字，每年至少一次。
-- 写死在代码里意味着改一次要发一次版，最后一定演变成"系统里是旧数，实际按 Excel 发"。
-- 【为什么不复用 salary_tier】
-- 提成阶梯是"业绩区间 → 比例"，按月业绩动态命中；底薪是"人 → 固定金额"，跟业绩无关。
-- 两者的主键、维度、变更频率都不同，硬塞进一张表会让两个查询都变得要先判断"这行是哪类"。
CREATE TABLE salary_base_policy (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    level_code  VARCHAR(20)   NOT NULL COMMENT '级别码：NORMAL / ELITE / SUPERVISOR',
    level_name  VARCHAR(20)   NOT NULL COMMENT '级别名称，直接展示：普通员工 / 精英员工 / 主管',
    base_salary DECIMAL(12,2) NOT NULL COMMENT '该级别的默认月底薪',
    sort_no     INT           NOT NULL DEFAULT 0 COMMENT '展示顺序，小的在前',
    remark      VARCHAR(200)  NULL COMMENT '说明，给主管看（如"含全勤，试用期按 80%"）',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- 一个级别只能有一条政策：出现两条时"到底取哪条"取决于查询顺序，工资就会随机变
    UNIQUE KEY uk_level_code (level_code),
    CONSTRAINT ck_base_policy_amount CHECK (base_salary >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '分级底薪政策（员工级别 → 默认底薪）';

-- 老板给的三档。这张表是"唯一事实来源"，改数字只改这里，代码不用动。
INSERT INTO salary_base_policy (level_code, level_name, base_salary, sort_no, remark) VALUES
    ('NORMAL',     '普通员工', 1500.00, 1, '默认级别；应发 = 1500 + 业绩×阶梯比例'),
    ('ELITE',      '精英员工', 2000.00, 2, '骨干/资深；应发 = 2000 + 业绩×阶梯比例'),
    ('SUPERVISOR', '主管',     3000.00, 3, '部门主管；应发 = 3000 + 业绩×阶梯比例');
