-- =============================================================================
-- 平面设计公司内部业务管理系统 · 数据库结构 V1
-- MySQL 8.0+ / InnoDB / utf8mb4
-- -----------------------------------------------------------------------------
-- 【全局约定，后面每张表都遵守，不再重复解释】
--
-- 1) 金额一律 DECIMAL(12,2)，禁止 FLOAT/DOUBLE
--    · 为什么：二进制浮点无法精确表示 0.1，0.1+0.2 会得到 0.30000000000000004
--    · 作用：保证「明细合计 = 订单总额」「个人业绩合计 = 订单业绩」永远精确相等
--    · 不这么做会怎样：月底对账差几分钱，设计师质疑系统，最后回到 Excel 手工核
--    · 怎么解决：DB 用 DECIMAL，Java 用 BigDecimal，中间任何一层都不许转 double
--
-- 2) 比例一律 INT 万分比存储（10000 = 100%），禁止 DECIMAL(5,4)/FLOAT
--    · 为什么：三个人各 33.33%，用小数相加是 99.99%，校验永远过不去
--    · 作用：sum(ratio_bp) = 10000 是精确整数比较，不存在舍入误差
--    · 不这么做会怎样：多人协作单要么存不进去，要么被迫放宽校验导致比例超 100%
--    · 怎么解决：录入界面收百分比（70%），后端 ×100 转 7000 存储，展示时 ÷100
--
-- 3) 财务相关表禁止物理删除，只允许 deleted 标记或状态作废
--    · 为什么：业绩算错时要能追溯原始数据，删了就永久失去证据
--    · 作用：任何历史数字都能还原
--    · 不这么做会怎样：员工申诉时拿不出凭证，系统公信力崩塌
--
-- 4) 每张表带 created_at / updated_at；涉及金额写入的表额外带 created_by
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================================
-- 一、组织与权限域
-- =============================================================================

DROP TABLE IF EXISTS dept;
CREATE TABLE dept (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name          VARCHAR(50)  NOT NULL                COMMENT '部门名称',
    parent_id     BIGINT       NOT NULL DEFAULT 0      COMMENT '上级部门ID，0 表示顶级',
    leader_emp_id BIGINT       NULL                    COMMENT '部门负责人（员工ID）',
    sort_no       INT          NOT NULL DEFAULT 0      COMMENT '排序号，越小越靠前',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '部门';

DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(50)  NOT NULL                COMMENT '登录名',
    -- 为什么单独建账号表而不直接把密码放 employee：
    -- 离职员工的账号要能停用但人事记录要保留；未来若开放老板远程查看看板，也可能有非员工账号。
    password_hash VARCHAR(100) NOT NULL                COMMENT 'BCrypt 哈希；绝对禁止存明文或 MD5',
    employee_id   BIGINT       NULL                    COMMENT '关联员工ID；纯系统账号可为空',
    status        TINYINT      NOT NULL DEFAULT 1      COMMENT '1=启用 0=停用',
    last_login_at DATETIME     NULL                    COMMENT '最后登录时间，用于识别僵尸账号',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_employee (employee_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '登录账号';

DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    code        VARCHAR(30) NOT NULL                COMMENT '角色码：ADMIN/SUPERVISOR/CS/DESIGNER/FINANCE',
    name        VARCHAR(50) NOT NULL                COMMENT '角色名称',
    -- 为什么用 code 常量而不是 id 判断：
    -- 代码里 if (role.getId() == 3) 这种硬编码，一旦初始化数据顺序变了权限就全乱。
    remark      VARCHAR(200) NULL                   COMMENT '备注',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色';

DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL COMMENT '账号ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (user_id, role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '账号-角色关联（多对多，一人可兼多角色，如主管兼设计师）';

DROP TABLE IF EXISTS employee;
CREATE TABLE employee (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    emp_no      VARCHAR(20) NOT NULL                COMMENT '工号',
    name        VARCHAR(50) NOT NULL                COMMENT '姓名',
    dept_id     BIGINT      NULL                    COMMENT '部门ID',
    position    VARCHAR(20) NOT NULL                COMMENT '岗位：SUPERVISOR=主管 CS=客服 DESIGNER=设计师 FINANCE=财务 ADMIN=管理员',
    -- 为什么单独一个 is_designer 而不是靠 position 判断：
    -- 主管自己要出图、客服转岗做设计的情况很常见。业绩分摊查询条件必须简单可靠，
    -- 靠 position 字符串匹配一旦有人录错岗位，业绩就漏算。
    is_designer TINYINT     NOT NULL DEFAULT 0      COMMENT '1=参与业绩分成 0=不参与',
    phone       VARCHAR(20) NULL                    COMMENT '手机号',
    hire_date   DATE        NULL                    COMMENT '入职日期，用于计算人效',
    status      TINYINT     NOT NULL DEFAULT 1      COMMENT '1=在职 0=离职（离职后历史业绩必须保留）',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_emp_no (emp_no),
    KEY idx_dept (dept_id),
    KEY idx_status_designer (status, is_designer)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '员工';

-- =============================================================================
-- 二、客户域
-- =============================================================================

DROP TABLE IF EXISTS client;
CREATE TABLE client (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    code          VARCHAR(30)   NOT NULL                COMMENT '客户编码',
    name          VARCHAR(100)  NOT NULL                COMMENT '企业全称（开票用）',
    -- 为什么单独存简称：订单号要拼"客户简称-年月-流水"，全称太长且含特殊字符
    short_name    VARCHAR(20)   NOT NULL                COMMENT '简称，用于生成订单号',
    settle_type   TINYINT       NOT NULL DEFAULT 2      COMMENT '结算方式：1=预付 2=分期 3=月结',
    -- 账期天数直接决定"未收业绩"是否算逾期，月结大客户通常 30/60/90 天
    credit_days   INT           NOT NULL DEFAULT 0      COMMENT '账期天数（月结客户用）',
    credit_limit  DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '信用额度，0=不限制',
    owner_emp_id  BIGINT        NULL                    COMMENT '归属客服（谁开发的客户）',
    contact_name  VARCHAR(50)   NULL                    COMMENT '主联系人',
    contact_phone VARCHAR(20)   NULL                    COMMENT '主联系人电话',
    tax_no        VARCHAR(50)   NULL                    COMMENT '纳税人识别号（开票用）',
    address       VARCHAR(200)  NULL                    COMMENT '地址',
    status        TINYINT       NOT NULL DEFAULT 1      COMMENT '1=合作中 0=停止合作',
    remark        VARCHAR(500)  NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code),
    KEY idx_name (name),
    KEY idx_owner (owner_emp_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '客户企业';

DROP TABLE IF EXISTS client_contact;
CREATE TABLE client_contact (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    client_id  BIGINT      NOT NULL COMMENT '客户ID',
    name       VARCHAR(50) NOT NULL COMMENT '姓名',
    phone      VARCHAR(20) NULL     COMMENT '电话',
    position   VARCHAR(50) NULL     COMMENT '职务',
    is_primary TINYINT     NOT NULL DEFAULT 0 COMMENT '1=主联系人',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_client (client_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '客户联系人（一个企业多个对接人）';

-- =============================================================================
-- 三、价目域
-- =============================================================================

DROP TABLE IF EXISTS price_item;
CREATE TABLE price_item (
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    code           VARCHAR(30)   NOT NULL                COMMENT '品项编码',
    name           VARCHAR(80)   NOT NULL                COMMENT '品项名称，如「单页海报·初稿3版」',
    category       VARCHAR(40)   NULL                    COMMENT '品类：海报/画册/包装/LOGO/展架…',
    unit           VARCHAR(10)   NOT NULL DEFAULT '项'    COMMENT '计价单位：张/页/套/项',
    std_price      DECIMAL(12,2) NOT NULL                COMMENT '标准单价（主管录入时的默认值）',
    cost_ref       DECIMAL(12,2) NULL                    COMMENT '成本参考价，仅老板/财务可见（算毛利用）',
    effective_from DATE          NOT NULL                COMMENT '生效起始日',
    effective_to   DATE          NULL                    COMMENT '生效截止日，NULL=长期有效',
    status         TINYINT       NOT NULL DEFAULT 1      COMMENT '1=启用 0=停用',
    created_by     BIGINT        NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code),
    KEY idx_category (category)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '标准价目表';

DROP TABLE IF EXISTS client_price;
CREATE TABLE client_price (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    client_id      BIGINT        NOT NULL COMMENT '客户ID',
    price_item_id  BIGINT        NOT NULL COMMENT '品项ID',
    price          DECIMAL(12,2) NOT NULL COMMENT '该客户的专属单价',
    effective_from DATE          NOT NULL COMMENT '生效起始日',
    effective_to   DATE          NULL     COMMENT '生效截止日',
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- 同一客户同一品项在同一天只能有一口价，否则录单时不知道该带出哪个
    UNIQUE KEY uk_client_item_from (client_id, price_item_id, effective_from),
    KEY idx_item (price_item_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '客户专属价（老客户/大客户单独定价）';

DROP TABLE IF EXISTS price_change_log;
CREATE TABLE price_change_log (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    price_item_id BIGINT        NOT NULL COMMENT '品项ID',
    old_price     DECIMAL(12,2) NULL     COMMENT '改前单价',
    new_price     DECIMAL(12,2) NULL     COMMENT '改后单价',
    reason        VARCHAR(200)  NULL     COMMENT '改价原因',
    operator_id   BIGINT        NOT NULL COMMENT '操作人',
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_item_time (price_item_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '调价留痕（历史订单用快照，不受调价影响）';

-- =============================================================================
-- 四、订单域
-- =============================================================================

DROP TABLE IF EXISTS sales_order;
CREATE TABLE sales_order (
    id                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no            VARCHAR(30)   NOT NULL                COMMENT '订单号：客户简称-YYMM-三位流水',
    client_id           BIGINT        NOT NULL                COMMENT '客户ID',
    title               VARCHAR(120)  NOT NULL                COMMENT '项目名称',
    category            VARCHAR(40)   NULL                    COMMENT '品类',
    cs_emp_id           BIGINT        NULL                    COMMENT '客服ID（提单人）',
    supervisor_id       BIGINT        NULL                    COMMENT '录单主管ID',
    sign_date           DATE          NULL                    COMMENT '签单日期；待定价状态下为空',
    expect_deliver_date DATE          NULL                    COMMENT '期望交付日',
    actual_deliver_date DATE          NULL                    COMMENT '实际交付日',
    -- 为什么总额要冗余存一份（不每次 SUM 明细）：
    -- ① 列表页按总额排序筛选，每次 SUM 会拖慢；② 业绩分摊基数需要稳定值，
    --    如果明细被改，总额跟着变，已生成的业绩流水就对不上了。
    -- 一致性怎么保证：只允许通过服务层统一重算写入（recalcTotal），并提供对账SQL定期校验。
    total_amount        DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '订单总额 = Σ明细金额，禁止手工填',
    -- 为什么单独存业绩基数：赠品/返工行金额为 0 但不该占分摊比例，
    -- 若用 total_amount 当分母，一旦以后出现负数行（折扣），分摊比例会被拉偏。
    perf_base_amount    DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '业绩分摊基数 = Σ正常行金额',
    -- 【打磨时修正的一处设计缺陷】原来用单个 status 同时表达"执行进度"和"收款进度"，
    -- 但这两件事互不阻塞：订单可能已交付却一分未收，也可能还在设计中客户就先付了全款。
    -- 合成一个字段必然互相覆盖，报表也就无法同时回答"这个月交付了几单"和"还有多少没收"。
    -- 拆成两个字段后，状态机各自独立推进。
    status              TINYINT       NOT NULL DEFAULT 0      COMMENT '执行状态：0草稿 1待定价 2已确认 3执行中 4已交付 9已取消',
    payment_status      TINYINT       NOT NULL DEFAULT 0      COMMENT '收款状态：0未收 1部分收款 2已结清',
    settle_type         TINYINT       NOT NULL DEFAULT 2      COMMENT '结算方式（默认继承客户，可覆盖）',
    credit_days         INT           NOT NULL DEFAULT 0      COMMENT '账期天数',
    version             INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号，防两人同时改同一单',
    remark              VARCHAR(500)  NULL,
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：1=已删（财务单据实际不允许删）',
    created_by          BIGINT        NULL,
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_client_sign (client_id, sign_date),
    KEY idx_supervisor (supervisor_id),
    KEY idx_cs (cs_emp_id),
    KEY idx_status_sign (status, sign_date),
    -- 未收账龄报表按"收款状态 + 签单日"扫，没有这条索引会走全表
    KEY idx_payment_status (payment_status, sign_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单（业绩的唯一载体）';

DROP TABLE IF EXISTS order_item;
CREATE TABLE order_item (
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id       BIGINT        NOT NULL                COMMENT '订单ID',
    line_no        INT           NOT NULL                COMMENT '行号',
    price_item_id  BIGINT        NULL                    COMMENT '来源价目项ID（可为空，支持临时品项）',
    -- 【重要】下面两个字段是"快照"，不是外键引用
    -- 为什么：价目表明年调价，去年订单的业绩不能跟着变。如果只存 price_item_id 再 join 取价，
    --         一旦改价，历史业绩全部漂移，员工会认为系统在篡改他的钱。
    -- 作用：单价在落单那一刻固化，永久不变。
    -- 会出什么问题：品项改名后，报表里显示的是老名字——这是"正确"的行为，历史就该是历史。
    item_name      VARCHAR(80)   NOT NULL                COMMENT '品项名称快照',
    spec           VARCHAR(100)  NULL                    COMMENT '规格描述',
    unit           VARCHAR(10)   NOT NULL DEFAULT '项'    COMMENT '单位',
    qty            DECIMAL(10,2) NOT NULL DEFAULT 1.00   COMMENT '数量，支持小数（如 2.5 米）',
    unit_price     DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '成交单价快照（主管录入）',
    amount         DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '小计 = qty × unit_price，服务层计算',
    item_type      TINYINT       NOT NULL DEFAULT 1      COMMENT '1=正常 2=赠品(金额0,计单量不计业绩) 3=返工(不计业绩)',
    status         TINYINT       NOT NULL DEFAULT 1      COMMENT '1=正常 0=作废',
    remark         VARCHAR(500)  NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_order (order_id),
    KEY idx_price_item (price_item_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单明细行';

DROP TABLE IF EXISTS order_item_assignee;
CREATE TABLE order_item_assignee (
    id            BIGINT  NOT NULL AUTO_INCREMENT,
    order_item_id BIGINT  NOT NULL COMMENT '明细行ID',
    employee_id   BIGINT  NOT NULL COMMENT '员工ID',
    assignee_role TINYINT NOT NULL DEFAULT 1 COMMENT '1=主设 2=协作',
    -- 【核心字段】为什么是 INT 万分比而不是 DECIMAL(5,4)：
    -- · 为什么：比例要参与金额乘法，任何精度损失都会放大到钱上；
    --           且"合计=100%"需要精确相等判断，小数做不到。
    -- · 作用：sum(ratio_bp) 必须 = 10000，可用于强校验。
    -- · 会出什么问题：若用 decimal(5,4) 存 33.33%，三人合计 99.99%，校验失败；
    --                 若放宽校验，又会出现比例 100.02% 导致业绩总额超过订单额。
    -- · 怎么解决：整数存储 + 唯一索引 + 服务层事务内校验合计。
    ratio_bp      INT     NOT NULL COMMENT '分成比例，万分比：7000 = 70%，10000 = 100%',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_emp (order_item_id, employee_id),
    KEY idx_emp (employee_id),
    -- 单行比例不能为负、不能超过 100%；"合计=100%" 跨行无法用 CHECK 表达，由服务层保证
    CONSTRAINT ck_ratio_range CHECK (ratio_bp >= 0 AND ratio_bp <= 10000)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '明细分成人（80%单人一行一条，20%多人多行）';

DROP TABLE IF EXISTS order_status_log;
CREATE TABLE order_status_log (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    order_id    BIGINT      NOT NULL COMMENT '订单ID',
    from_status TINYINT     NULL     COMMENT '变更前状态',
    to_status   TINYINT     NOT NULL COMMENT '变更后状态',
    operator_id BIGINT      NOT NULL COMMENT '操作人',
    remark      VARCHAR(200) NULL,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_order_time (order_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单状态流转日志（谁在什么时候把单改成了什么）';

-- =============================================================================
-- 五、收款域
-- =============================================================================

DROP TABLE IF EXISTS receivable_plan;
CREATE TABLE receivable_plan (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    order_id        BIGINT        NOT NULL COMMENT '订单ID',
    seq             INT           NOT NULL COMMENT '期次：1=订金 2=中期款 3=尾款',
    plan_name       VARCHAR(30)   NOT NULL COMMENT '节点名称：订金/中期款/尾款',
    plan_amount     DECIMAL(12,2) NOT NULL COMMENT '计划收款金额',
    plan_date       DATE          NULL     COMMENT '计划收款日期',
    -- 为什么把已收金额冗余在计划行上：
    -- 未收账龄要按"节点"算（尾款逾期 60 天），若每次从核销表聚合，报表SQL会非常重。
    received_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '该节点已收金额（由核销累加，冗余字段）',
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '1=未收 2=部分收 3=已收讫',
    remark          VARCHAR(200)  NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_seq (order_id, seq),
    KEY idx_plan_date (plan_date, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '收款计划（订金/中期/尾款节点，未收账龄的基础）';

DROP TABLE IF EXISTS payment;
CREATE TABLE payment (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    payment_no  VARCHAR(30)   NOT NULL COMMENT '回款单号',
    client_id   BIGINT        NOT NULL COMMENT '客户ID',
    -- 为什么外键在 allocation 表而不在这里：月结大客户一笔款覆盖多张单，必须多对多
    amount      DECIMAL(12,2) NOT NULL COMMENT '到账金额',
    pay_date    DATE          NOT NULL COMMENT '到账日期（业绩归属月就按这个日期算）',
    pay_method  TINYINT       NOT NULL DEFAULT 1 COMMENT '1=银行转账 2=现金 3=承兑 4=其他',
    bank_serial VARCHAR(60)   NULL     COMMENT '银行流水号，用于和财务流水核对',
    voucher_url VARCHAR(300)  NULL     COMMENT '回款凭证图片地址',
    status      TINYINT       NOT NULL DEFAULT 1 COMMENT '1=待核销 2=已核销 3=已作废',
    operator_id BIGINT        NOT NULL COMMENT '录入人',
    remark      VARCHAR(500)  NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_no (payment_no),
    KEY idx_client_date (client_id, pay_date),
    KEY idx_pay_date (pay_date),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '回款单（一笔到账）';

DROP TABLE IF EXISTS payment_allocation;
CREATE TABLE payment_allocation (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    payment_id    BIGINT        NOT NULL COMMENT '回款单ID',
    order_id      BIGINT        NOT NULL COMMENT '核销到的订单',
    order_item_id BIGINT        NULL     COMMENT '核销到具体明细行；为空表示按行金额占比自动分摊',
    plan_id       BIGINT        NULL     COMMENT '对应的收款节点（订金/尾款），用于更新计划状态',
    amount        DECIMAL(12,2) NOT NULL COMMENT '本次核销金额',
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by    BIGINT        NULL,
    PRIMARY KEY (id),
    KEY idx_payment (payment_id),
    KEY idx_order (order_id),
    KEY idx_plan (plan_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '回款核销（回款单 → 订单，多对多拆分明细）';

DROP TABLE IF EXISTS invoice;
CREATE TABLE invoice (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    client_id  BIGINT        NOT NULL COMMENT '客户ID',
    order_id   BIGINT        NULL     COMMENT '关联订单，可为空（合并开票）',
    invoice_no VARCHAR(50)   NULL     COMMENT '发票号',
    amount     DECIMAL(12,2) NOT NULL COMMENT '开票金额',
    tax_rate   DECIMAL(5,4)  NULL     COMMENT '税率，如 0.0600',
    invoice_type TINYINT     NOT NULL DEFAULT 1 COMMENT '1=普通发票 2=专用发票',
    issue_date DATE          NULL     COMMENT '开票日期',
    status     TINYINT       NOT NULL DEFAULT 1 COMMENT '1=待开 2=已开 3=已作废',
    remark     VARCHAR(300)  NULL,
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_client (client_id),
    KEY idx_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '开票记录（与回款解耦，先票后款/先款后票都支持）';

DROP TABLE IF EXISTS statement;
CREATE TABLE statement (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    statement_no VARCHAR(30)   NOT NULL COMMENT '对账单号',
    client_id    BIGINT        NOT NULL COMMENT '客户ID',
    period       CHAR(7)       NOT NULL COMMENT '对账期间 YYYY-MM',
    total_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '本期对账金额',
    paid_amount  DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '本期已回款金额',
    status       TINYINT       NOT NULL DEFAULT 1 COMMENT '1=已生成 2=已发送 3=客户确认 4=已结清',
    file_url     VARCHAR(300)  NULL     COMMENT '导出的 PDF 地址',
    confirmed_at DATETIME      NULL     COMMENT '客户确认时间',
    created_by   BIGINT        NULL,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_client_period (client_id, period),
    UNIQUE KEY uk_statement_no (statement_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '月结对账单（2-3 个月结大客户专用）';

DROP TABLE IF EXISTS statement_item;
CREATE TABLE statement_item (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    statement_id BIGINT        NOT NULL COMMENT '对账单ID',
    order_id     BIGINT        NOT NULL COMMENT '订单ID',
    order_no     VARCHAR(30)   NOT NULL COMMENT '订单号快照（对账单是给客户看的凭证，必须固化）',
    amount       DECIMAL(12,2) NOT NULL COMMENT '该单本期对账金额',
    PRIMARY KEY (id),
    KEY idx_statement (statement_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '对账单明细';

-- =============================================================================
-- 六、业绩域（系统心脏）
-- =============================================================================

DROP TABLE IF EXISTS performance_ledger;
CREATE TABLE performance_ledger (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    employee_id   BIGINT        NOT NULL COMMENT '员工ID',
    order_id      BIGINT        NOT NULL COMMENT '订单ID',
    order_item_id BIGINT        NULL     COMMENT '明细行ID',
    ledger_type   TINYINT       NOT NULL COMMENT '1=SIGN 签单额 2=CONFIRM 业绩确认(回款触发) 3=REVERSE 冲销 4=ADJUST 人工调整',
    -- 为什么允许负数：废单、退款、改派都要冲销，用负数流水冲比"删掉原记录"可追溯得多
    amount        DECIMAL(12,2) NOT NULL COMMENT '业绩金额，可为负',
    ratio_bp      INT           NOT NULL COMMENT '分成比例快照，万分比',
    -- 为什么 period 单独存而不是从 occur_date 推：
    -- 跨月补录/审批调整归属月的场景，归属期 ≠ 发生日期所在月，两个都必须是独立字段。
    period        CHAR(7)       NOT NULL COMMENT '业绩归属期间 YYYY-MM',
    occur_date    DATE          NOT NULL COMMENT '实际发生日期',
    source_type   TINYINT       NOT NULL COMMENT '1=订单 2=回款核销 3=人工',
    source_id     BIGINT        NULL     COMMENT '来源单据ID（核销ID/订单ID）',
    -- 【最关键的一个字段】幂等键
    -- · 为什么：网络重试、用户重复点击、定时任务重跑都可能让同一笔回款生成两次业绩。
    --           靠代码里 if 判断"是否已生成"在并发下不可靠（两个线程同时查到"没有"）。
    -- · 作用：唯一索引让数据库做最后一道防线，第二次插入直接抛 DuplicateKey，业务层捕获即可。
    -- · 会出什么问题：没有它，一次重试 = 员工业绩翻倍，月底发现要手工挑出来删，极难定位。
    -- · 怎么解决：格式固定为 type:sourceId:employeeId:itemId，插入前不查、直接靠唯一索引兜底。
    idem_key      VARCHAR(100)  NOT NULL COMMENT '幂等键：CONFIRM:{核销ID}:{员工ID}:{明细ID}',
    remark        VARCHAR(500)  NULL,
    created_by    BIGINT        NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_idem (idem_key),
    -- 员工个人业绩页的核心查询：where employee_id=? and period=? —— 这条索引直接决定页面响应
    KEY idx_emp_period (employee_id, period),
    KEY idx_order (order_id),
    KEY idx_period_type (period, ledger_type),
    KEY idx_source (source_type, source_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '业绩流水（只追加不修改，任何数字可回溯到来源单据）';

DROP TABLE IF EXISTS monthly_perf_summary;
CREATE TABLE monthly_perf_summary (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    employee_id   BIGINT        NOT NULL COMMENT '员工ID',
    period        CHAR(7)       NOT NULL COMMENT '期间 YYYY-MM',
    sign_amount   DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '当月签单额',
    perf_amount   DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '当月确认业绩',
    item_count    INT           NOT NULL DEFAULT 0    COMMENT '当月单量（明细行数）',
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- 为什么要有这张汇总表：看板每次 SUM 十万行流水会慢；预聚合后 1 秒内出数。
    -- 为什么要能重算：汇总表是"派生数据"，任何时候都能从流水全量重算，重算幂等，所以不怕算错。
    UNIQUE KEY uk_emp_period (employee_id, period),
    KEY idx_period (period)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '员工业绩月汇总（可随时由流水重算）';

-- =============================================================================
-- 七、执行与支撑域
-- =============================================================================

DROP TABLE IF EXISTS deliverable;
CREATE TABLE deliverable (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    order_id      BIGINT       NOT NULL COMMENT '订单ID',
    order_item_id BIGINT       NULL     COMMENT '明细行ID，可空',
    version_no    INT          NOT NULL COMMENT '版本号 V1/V2/V3',
    file_name     VARCHAR(200) NOT NULL COMMENT '文件名',
    file_url      VARCHAR(300) NOT NULL COMMENT '存储地址（MinIO/OSS）',
    file_size     BIGINT       NOT NULL DEFAULT 0 COMMENT '字节数',
    file_type     VARCHAR(20)  NULL     COMMENT '扩展名',
    -- 客户仍在微信确认，系统只登记结果；不做在线批注（v2 已明确砍掉客户端）
    review_result TINYINT      NOT NULL DEFAULT 1 COMMENT '1=待审 2=客户通过 3=客户退回',
    feedback      VARCHAR(500) NULL     COMMENT '客户反馈摘要（退回原因）',
    uploaded_by   BIGINT       NOT NULL COMMENT '上传人',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_version (order_id, version_no),
    KEY idx_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '稿件版本';

DROP TABLE IF EXISTS rework_log;
CREATE TABLE rework_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    order_id      BIGINT       NOT NULL COMMENT '订单ID',
    deliverable_id BIGINT      NOT NULL COMMENT '被退回的稿件版本',
    from_version  INT          NOT NULL COMMENT '退回时的版本',
    reason        VARCHAR(500) NULL     COMMENT '退回原因分类/描述',
    -- 为什么单独记返工：返工不产生业绩，但它是评估设计师与项目质量最硬的指标，
    -- 也是报价时判断"这客户是不是难缠"的依据。
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_order (order_id),
    KEY idx_deliverable (deliverable_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '返工记录（用于统计返工率，不参与业绩计算）';

DROP TABLE IF EXISTS approval;
CREATE TABLE approval (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    biz_type    VARCHAR(30)  NOT NULL COMMENT '业务类型：ORDER_CANCEL/PRICE_CHANGE/PERF_ADJUST',
    biz_id      BIGINT       NOT NULL COMMENT '业务单据ID',
    applicant_id BIGINT      NOT NULL COMMENT '申请人',
    approver_id BIGINT       NULL     COMMENT '审批人',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=待审 2=通过 3=驳回',
    reason      VARCHAR(500) NULL     COMMENT '申请理由 / 审批意见',
    handled_at  DATETIME     NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_biz (biz_type, biz_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审批（废单/改价/业绩调整，只有老板需要审）';

DROP TABLE IF EXISTS audit_log;
CREATE TABLE audit_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    biz_type    VARCHAR(30)  NOT NULL COMMENT '业务类型',
    biz_id      BIGINT       NOT NULL COMMENT '业务单据ID',
    field_name  VARCHAR(50)  NULL     COMMENT '变更字段',
    old_value   VARCHAR(500) NULL     COMMENT '变更前',
    new_value   VARCHAR(500) NULL     COMMENT '变更后',
    operator_id BIGINT       NOT NULL COMMENT '操作人',
    ip          VARCHAR(45)  NULL     COMMENT '操作 IP',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_biz (biz_type, biz_id),
    KEY idx_operator_time (operator_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作日志（金额改动必须留痕，这是内部争议的唯一证据）';

DROP TABLE IF EXISTS notification;
CREATE TABLE notification (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    receiver_id BIGINT       NOT NULL COMMENT '接收人员工ID',
    type        VARCHAR(30)  NOT NULL COMMENT '类型：ORDER_ASSIGNED/ORDER_PENDING_PRICE/PAYMENT_DUE',
    title       VARCHAR(100) NOT NULL,
    content     VARCHAR(500) NULL,
    biz_id      BIGINT       NULL     COMMENT '关联业务ID，点通知直接跳转',
    is_read     TINYINT      NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_receiver (receiver_id, is_read, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站内通知';

SET FOREIGN_KEY_CHECKS = 1;
