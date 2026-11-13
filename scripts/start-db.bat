@echo off
chcp 65001 > nul
rem ============================================================
rem 用途：启动本地开发用 MySQL 实例（绿色解压版，非 Windows 服务）。
rem 为什么不用 Windows 服务：注册服务需要管理员权限，且开发机不需要开机自启；
rem   绿色版用当前用户权限即可运行，卸载时删目录即可，不留系统残留。
rem 会出现的问题：若 3306 被占用，mysqld 会直接退出并在日志里写
rem   "Do you already have another mysqld server running on port: 3306"，
rem   解决办法是执行一次 stop-db.bat，或改 my.ini 里的 port。
rem ============================================================
if "%MYSQL_HOME%"=="" (
    echo [错误] 未设置 MYSQL_HOME 环境变量，请先设置为本机 MySQL 解压目录。
    exit /b 1
)

if not exist "%MYSQL_HOME%\bin\mysqld.exe" (
    echo [错误] 在 %MYSQL_HOME%\bin 下找不到 mysqld.exe，路径配置有误。
    exit /b 1
)

rem --console 让日志输出到当前窗口，方便直接看到启动失败原因
"%MYSQL_HOME%\bin\mysqld.exe" --defaults-file="%MYSQL_HOME%\my.ini" --console
