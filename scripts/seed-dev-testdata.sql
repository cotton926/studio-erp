-- =============================================================================
-- 开发自测数据（仅限本地开发库，绝不用于生产）
--
-- 用途：验证「设计师只能看到自己的业绩」这条行级隔离是否真的生效。
-- 密码：两个测试账号的密码与管理员的当前密码相同（直接复制哈希），
--       避免这里再硬编码一个密码。
--
-- 注意：下面的 order_id 故意指向不存在的订单 —— 本脚本只验证业绩查询的隔离，
--      业务表没有建外键约束（财务系统保留历史数据比强约束更重要），
--      因此不会插入失败。正式联调时这些数据由订单/回款模块产生。
-- =============================================================================

-- 1. 两名设计师（is_designer=1 才参与分成）
INSERT INTO employee (emp_no, name, dept_id, position, is_designer, status)
SELECT 'D001', '设计师甲', d.id, 'DESIGNER', 1, 1 FROM dept d WHERE d.name = '设计部'
  AND NOT EXISTS (SELECT 1 FROM employee WHERE emp_no = 'D001');

INSERT INTO employee (emp_no, name, dept_id, position, is_designer, status)
SELECT 'D002', '设计师乙', d.id, 'DESIGNER', 1, 1 FROM dept d WHERE d.name = '设计部'
  AND NOT EXISTS (SELECT 1 FROM employee WHERE emp_no = 'D002');

-- 2. 账号：密码哈希复用管理员当前密码；must_change_pwd=0 方便直接调接口
INSERT INTO sys_user (username, password_hash, employee_id, status, must_change_pwd)
SELECT 'designer1', u.password_hash, e.id, 1, 0
FROM sys_user u, employee e
WHERE u.username = 'admin' AND e.emp_no = 'D001'
  AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'designer1');

INSERT INTO sys_user (username, password_hash, employee_id, status, must_change_pwd)
SELECT 'designer2', u.password_hash, e.id, 1, 0
FROM sys_user u, employee e
WHERE u.username = 'admin' AND e.emp_no = 'D002'
  AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'designer2');

-- 3. 绑定 DESIGNER 角色（scope=SELF，这是隔离测试的关键）
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r
WHERE u.username = 'designer1' AND r.code = 'DESIGNER'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role x WHERE x.user_id = u.id AND x.role_id = r.id);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r
WHERE u.username = 'designer2' AND r.code = 'DESIGNER'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role x WHERE x.user_id = u.id AND x.role_id = r.id);

-- 4. 业绩流水：甲 7000+4200，乙 3000。数字本身不重要，能区分归属即可
INSERT INTO performance_ledger
    (employee_id, order_id, ledger_type, amount, ratio_bp, period, occur_date, source_type, idem_key, remark)
SELECT e.id, 9001, 1, 7000.00, 10000, '2026-10', '2026-10-08', 1, 'SIGN:9001:D001', '自测：签单额'
FROM employee e WHERE e.emp_no = 'D001'
  AND NOT EXISTS (SELECT 1 FROM performance_ledger WHERE idem_key = 'SIGN:9001:D001');

INSERT INTO performance_ledger
    (employee_id, order_id, ledger_type, amount, ratio_bp, period, occur_date, source_type, idem_key, remark)
SELECT e.id, 9001, 2, 4200.00, 10000, '2026-10', '2026-10-20', 2, 'CONFIRM:8001:D001', '自测：订金回款业绩'
FROM employee e WHERE e.emp_no = 'D001'
  AND NOT EXISTS (SELECT 1 FROM performance_ledger WHERE idem_key = 'CONFIRM:8001:D001');

INSERT INTO performance_ledger
    (employee_id, order_id, ledger_type, amount, ratio_bp, period, occur_date, source_type, idem_key, remark)
SELECT e.id, 9002, 1, 3000.00, 10000, '2026-10', '2026-10-09', 1, 'SIGN:9002:D002', '自测：签单额'
FROM employee e WHERE e.emp_no = 'D002'
  AND NOT EXISTS (SELECT 1 FROM performance_ledger WHERE idem_key = 'SIGN:9002:D002');
