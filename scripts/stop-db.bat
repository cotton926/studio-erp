@echo off
chcp 65001 > nul
rem ============================================================
rem 用途：优雅关闭本地 MySQL 实例。
rem 为什么必须用 mysqladmin shutdown 而不是直接杀进程：
rem   InnoDB 依赖正常关闭来刷脏页、清理 undo log。强杀进程虽然通常能靠
rem   崩溃恢复救回来，但会留下未提交事务的回滚过程，重启后表可能短暂锁住。
rem 会出现的问题：root 密码错误时 mysqladmin 会提示 Access denied，
rem   此时改用任务管理器结束 mysqld.exe 也能关闭，只是不优雅。
rem ============================================================
if "%MYSQL_HOME%"=="" (
    echo [错误] 未设置 MYSQL_HOME 环境变量。
    exit /b 1
)

"%MYSQL_HOME%\bin\mysqladmin.exe" -uroot -p -h127.0.0.1 -P3306 shutdown
echo 已发送关闭指令。
