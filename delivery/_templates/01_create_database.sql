-- ============================================================
-- studio-erp 数据库初始化（MySQL 8.0+）
-- 只需执行一次；表结构由程序内的 Flyway 在首次启动时自动创建，
-- 这里只负责建库和应用账号（Flyway 不会替你建库）。
-- 用 root 或有建库建用户权限的账号执行：
--   mysql -uroot -p < 01_create_database.sql
-- ============================================================

-- 1) 业务库：utf8mb4 才能存客户名里的生僻字和 emoji
CREATE DATABASE IF NOT EXISTS `studio_erp`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

-- 2) 应用专用账号：不给 root。
--    只授权 studio_erp 一个库，程序被拿下时也动不了别的库。
--    【上线前必须把下面的密码改掉】，并同步写进 config.env 的 DB_PASSWORD。
CREATE USER IF NOT EXISTS 'studio'@'%' IDENTIFIED BY 'ChangeMe@2026';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, DROP
    ON `studio_erp`.* TO 'studio'@'%';
FLUSH PRIVILEGES;

-- 3) 说明
--    · Flyway 需要 CREATE/ALTER/INDEX 权限来建表和加索引，所以上面给了 DDL 权限；
--     如果运维要求应用账号只能 DML，则改由 DBA 在发布前手工执行
--     程序内 db/migration 下的 V1~V10 脚本，届时可把 DDL 权限收回。
--    · 首次启动后请用管理员账号登录并立即修改初始密码。
