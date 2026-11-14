studio-erp v2.2 · 平面设计公司业务管理系统
===============================================

环境要求：JDK 17 + MySQL 8.0.x

三步启动
--------
1) 建库：mysql -uroot -p < sql\01_create_database.sql
   （执行前先把脚本里应用账号的占位密码改掉）

2) 配置：把 config.env.example 复制为 config.env，填数据库密码

3) 启动：双击 start.bat
   浏览器自动打开 http://127.0.0.1:8080
   局域网同事访问 http://服务器IP:8080

停止服务：双击 stop.bat
查日志：  logs\studio-erp.log
稿件文件：data\uploads（必须纳入备份！）

详细步骤、备份方案、升级流程、故障排查 → docs\部署手册.md
接口定义（二次开发）→ docs\接口清单.md
