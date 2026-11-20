# 设计业务管理系统

平面设计公司内部使用的「订单 — 回款 — 业绩 — 工资」一体化管理系统，业务规则来自 7 年一线从业经验。
纯内部使用，无客户端门户。

**技术栈**：Java 17 / Spring Boot 3.2.5 / MyBatis-Plus 3.5.7 / MySQL 8.0 / Sa-Token / Flyway / Maven
**前端**：Vue 3.5 / Vite 5 / Element Plus / ECharts / Pinia
**验证**：39 个单元测试全绿，全套接口经真实 HTTP 请求 + 无头浏览器实跑

## 功能完成情况

| # | 交付物 | 状态 |
|---|---|---|
| 1 | 数据库结构：29 张表 / 283 字段（Flyway 迁移 v1~v7） | ✅ 已完成并校验 |
| 2 | 业绩分摊算法（核心纯函数） | ✅ 已完成并验证 |
| 3 | 业绩确认服务（事务 + 幂等） | ✅ 已完成 |
| 4 | 业绩查询服务（双层数据隔离示范） | ✅ 已完成 |
| 5 | 统一响应 / 异常 / 错误码体系 | ✅ 已完成 |
| 6 | 数据权限：`@DataScope` + 守卫 + 切面 | ✅ 已完成 |
| 7 | 领域枚举：订单状态机 / 收款状态 / 流水类型 | ✅ 已完成 |
| 8 | 接口清单（55 个 REST 接口） | ✅ 见 `docs/接口清单.md` |
| 9 | 页面原型（业绩页 + 录单页） | ✅ 见 `prototype/index.html` |
| 10 | 自检工具链（`npm run check`） | ✅ 4 项全通过 |
| 11 | **登录模块（认证 + 拦截器 + 首启管理员）** | ✅ 已完成并实测 |
| 12 | **员工档案 + 账号管理（建号 / 重置密码 / 启停用 / 角色）** | ✅ 已完成并实测 |
| 13 | 客户域 + 价目表 | ⏸ 本期不做（客户名与单价改为手填） |
| 14 | **订单 / 回款域（提单 → 定价 → 回款核销 → 业绩）** | ✅ 已完成并实测 |
| 15 | **工资阶梯核算（业绩 × 40/45/50%）+ 员工工资页** | ✅ 已完成并实测 |
| 16 | **分级底薪（普通 1500 / 精英 2000 / 主管 3000）+ 个人特例覆盖** | ✅ 已完成并实测 |
| 17 | **Vue 3 前端（登录 / 业绩 / 工资 / 订单 / 团队工资）** | ✅ 已完成并实测 |
| 18 | **稿件版本与送审（上传 / 退回 / 返工统计）+ 状态流转时间线** | ✅ 已完成并实测 |
| 19 | **审计链路（order_status_log / audit_log 全部接线）** | ✅ 已完成并实测 |
| 20 | 报表导出、收款计划 / 账龄提醒、开票 | ⏳ 待做 |

## 目录结构

```
studio-erp/
├─ pom.xml                                Spring Boot 3.2 + JDK 17
├─ .editorconfig / .gitignore
├─ docs/接口清单.md                        55 个 REST 接口定义
├─ prototype/index.html                   可预览的页面原型（双击即开，无外部依赖）
├─ tools/                                 自检工具链（Node，无需 JDK）
│  ├─ check-all.mjs                       一把梭跑全部检查
│  ├─ check-sql.mjs                       SQL 语法解析 + 结构校验
│  ├─ check-java.mjs                      Java 源码语法解析
│  ├─ check-allocator.mjs                 业绩算法等价验证 + 随机压测
│  └─ check-html.mjs                      原型标签闭合 / 外部依赖检查
└─ src/
   ├─ main/java/com/studio/erp/
   │  ├─ StudioErpApplication.java
   │  ├─ common/                          ← 全局基建
   │  │  ├─ api/R.java                    统一响应体
   │  │  ├─ api/PageResult.java           统一分页结构（pageNum/pageSize/total/list）
   │  │  ├─ config/MybatisPlusConfig      分页插件 + 全表更新拦截
   │  │  ├─ exception/                    错误码 / 业务异常 / 全局处理器
   │  │  └─ enums/                        订单状态机 / 收款状态 / 流水类型 / 来源
   │  ├─ auth/                            ← 认证 + 数据权限 + 账号
   │  │  ├─ CurrentUser / CurrentUserHolder   当前登录人（ThreadLocal）
   │  │  ├─ AuthInterceptor                  登录校验 + 强制改密 + 上下文清理
   │  │  ├─ AuthService                      登录 / 登出 / 改密 / 每请求加载身份
   │  │  ├─ AdminBootstrapRunner             首启建管理员（只从环境变量取密码）
   │  │  ├─ LoginAttemptGuard                失败 5 次锁 5 分钟
   │  │  ├─ PasswordPolicy                   密码强度规则 + 初始密码生成（唯一一处）
   │  │  ├─ AccountService / AccountController   建号 / 重置密码 / 启停用 / 角色
   │  │  ├─ RoleScopeResolver                角色 → 数据范围（唯一映射处）
   │  │  ├─ DataScope / ScopeType            数据范围声明
   │  │  ├─ DataScopeGuard                   参数改写（真正的隔离）
   │  │  ├─ DataScopeAspect                  范围准入切面
   │  │  └─ RequireRole / RequireRoleAspect  角色准入（这个动作归不归你干）
   │  ├─ employee/                        ← 员工与部门
   │  │  ├─ entity/Employee · Dept
   │  │  ├─ dto/                          查询条件 / 保存请求 / 列表视图 / 下拉项
   │  │  ├─ service/EmployeeService       档案 CRUD、离职联动停号
   │  │  └─ controller/EmployeeController
   │  ├─ order/                           ← 订单与回款
   │  │  ├─ entity/                       SalesOrder / OrderItem / OrderItemAssignee / Payment / PaymentAllocation
   │  │  ├─ dto/                          提单 / 定价 / 查询 / 列表 / 详情 / 回款请求
   │  │  ├─ mapper/                       含取号行锁、CAS 状态更新、列表聚合
   │  │  ├─ service/OrderService          提单、定价、推进、登记回款（都是事务）
   │  │  ├─ service/OrderQueryService     列表与详情（服务端遮蔽金额）
   │  │  └─ controller/OrderController
   │  ├─ salary/                         ← 工资阶梯核算
   │  │  ├─ domain/                       SalaryCalculator（纯函数）/ SalaryTierDef / SalaryTierMode / SalaryBreakdown
   │  │  ├─ entity/SalaryTier · mapper/
   │  │  ├─ dto/                          SalaryVO / TierVO / TierSaveRequest / BaseSalaryRequest
   │  │  ├─ service/SalaryService         取数 + 算法调用 + 组装
   │  │  └─ controller/SalaryController
   │  └─ performance/                     ← 核心域
   │     ├─ domain/                       纯算法，零框架依赖，可独立单测
   │     ├─ entity/PerformanceLedger.java
   │     ├─ mapper/                       PerfQueryMapper / PerformanceLedgerMapper
   │     ├─ controller/                   PerformanceController
   │     └─ service/                      PerformanceService（写）/ PerformanceQueryService（读）
   ├─ main/resources/
   │  ├─ application.yml
   │  └─ db/migration/V1__schema.sql · V2__init_data.sql · V3__add_must_change_pwd.sql
   │                 · V4__unique_account_per_employee.sql
   │                 · V5__order_manual_client_and_salary.sql
   │                 · V6__employee_level_and_base_policy.sql
   └─ test/java/com/studio/erp/
      ├─ auth/RoleScopeResolverTest.java   角色→范围映射
      ├─ auth/PasswordPolicyTest.java      密码规则与随机密码
      ├─ salary/SalaryCalculatorTest.java  阶梯边界（19999.99 / 20000 / 20000.01）
      ├─ salary/BaseSalaryResolverTest.java 分级底薪（按级别 / 个人指定 / 缺配置）
      └─ performance/PerformanceAllocatorTest.java

web/                                     ← Vue 3 前端（独立工程）
├─ vite.config.ts                        /api 代理到 8080
├─ src/
│  ├─ api/                               http 封装 + 各域接口（类型对齐后端响应）
│  ├─ stores/user.ts                     登录态（只存 token，用户信息每次拉）
│  ├─ router/index.ts                    hash 路由 + 登录守卫 + 管理页拦截
│  ├─ layout/AppLayout.vue               侧栏 / 顶栏 / 改密
│  ├─ components/OrderItemsEditor.vue    明细 + 分成人编辑器（录单与定价共用）
│  └─ views/                             登录 / 我的业绩 / 我的工资 / 订单 / 团队工资
```

## 本地启动

前置：JDK 17、Maven 3.9+、MySQL 8.0。

> 本机已装好（用户级，无需管理员权限，**新开终端**才生效）：
> - JDK：`%LOCALAPPDATA%\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.21+9`（Temurin 17.0.21）
> - Maven：`%LOCALAPPDATA%\AppData\Local\Programs\apache-maven-3.9.16`
> - MySQL：`%LOCALAPPDATA%\AppData\Local\Programs\mysql-8.0.28-winx64`（绿色版，非 Windows 服务）
> - 环境变量：`JAVA_HOME` / `MAVEN_HOME` / `MYSQL_HOME` / `Path`
> - Maven 镜像配置在 `%LOCALAPPDATA%\.m2\settings.xml`（阿里云 public），只影响下载地址

```bash
# 1. 启动数据库（双击也行，脚本会前台打印 mysqld 日志）
scripts\start-db.bat        # 关闭用 scripts\stop-db.bat

# 2. 启动应用（凭据从 .env.local 读取，不写进命令行）
#    .env.local 已在 .gitignore 中，按 .env.local 内的字段填即可
set -a; . ./.env.local; set +a
mvn spring-boot:run

# 3. 单元测试
mvn test

# 4. 启动前端（另开一个终端，后端要保持运行）
cd web
npm install          # 首次
npm run dev          # → http://localhost:5173
```

前端通过 Vite 把 `/api` 代理到 `127.0.0.1:8080`，所以后端不需要开 CORS。
部署时把 `npm run build` 产出的 `web/dist` 丢给 Nginx，用一段
`location /api { proxy_pass http://127.0.0.1:8080; }` 即可，前端代码不用改。

> **为什么前端用 hash 路由**：history 模式要求服务器把所有未匹配路径回退到
> `index.html`，少配这一行刷新就是 404。内部系统往往是"丢个 dist 到 Nginx"，
> 没人会记得改配置，hash 模式在任何静态服务器上都能直接跑。

> **端口坑**：本机环境变量里有 `SERVER__PORT=3113`，Spring 的宽松绑定会把
> `SERVER__PORT` 当成 `server.port`，于是应用去抢宿主自己占用的 3113 并启动失败。
> 启动时显式覆盖即可：`mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8080`。

## 登录与权限

首次启动时 `AdminBootstrapRunner` 会在 `sys_user` 为空时创建管理员：

- 用户名固定 `admin`
- 密码取环境变量 `ADMIN_INIT_PASSWORD`，**缺失或强度不足则跳过创建并告警**（绝不使用默认密码兜底）
- 新账号 `must_change_pwd=1`，首次登录除改密外的接口一律返回 `40104`
- 已有账号时不会重置密码（否则每次重启都等于给自己开后门）

```bash
# 登录 → 拿 token
curl -X POST http://127.0.0.1:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"你的密码"}'

# 带 token 访问
curl -H "studio-token: <token>" http://127.0.0.1:8080/api/auth/me

# 改密（改完所有会话失效，需重新登录）
curl -X POST http://127.0.0.1:8080/api/auth/password \
  -H "Content-Type: application/json" -H "studio-token: <token>" \
  -d '{"oldPassword":"旧密码","newPassword":"新密码至少8位含字母数字"}'
```

跑行级隔离自测（建两个设计师 + 若干业绩流水，密码复用管理员当前密码）：

```bash
mysql -ustudio -p --default-character-set=utf8mb4 studio_erp < scripts/seed-dev-testdata.sql
```


## 自检（不需要 JDK）

任何改动后先跑这个，能拦住绝大多数低级错误：

```bash
cd tools
npm install        # 首次
npm run check      # 4 项检查，任一失败退出码非 0
```

| 检查项 | 抓什么 |
|---|---|
| `check:sql` | 用 MySQL 方言解析每条 DDL + 主键/索引列存在性/DROP 与 CREATE 配对 |
| `check:java` | 逐个解析 Java 源码，抓括号、泛型、注解、record 构造器语法错 |
| `check:alloc` | 8 个边界用例 + 1000 组随机压测，验证「合计==核销额、无负数」 |
| `check:html` | 原型标签闭合、无外部 CDN、关键视图齐全 |

> 语法通过 ≠ 编译通过。类型不存在、方法签名不匹配这类问题只有 `mvn compile` 能发现。

## 业绩口径（系统唯一真理，别处不要再定义一遍）

| 指标 | 何时产生 | 归属期间 |
|---|---|---|
| 签单额 | 主管确认订单、明细落库时 | 签单月 |
| **业绩**（主指标） | 每笔回款核销成功时 | **回款到账月** |
| 未收业绩 | 派生，不落库 | 截至期末 |

关键规则：订金与尾款分别确认；本月未收的尾款，下月到账则计入下月业绩。
因此 `period`（归属期间）与 `occur_date`（发生日期）必须是两个独立字段。

## 员工与账号（人员这条链怎么走）

```
建档案（/employees）→ 开账号（/accounts）→ 分配角色 → 员工首登改密
                     ↘ 离职（/employees/{id}/status=0）→ 账号自动停用
```

四条硬规则，都写在代码里而不是流程文档里：

| 规则 | 为什么不能靠人记 |
|---|---|
| 一个员工最多一个账号 | 并发点两次"开号"会插出两条账号，人有两个登录名，各有一套密码和角色。靠唯一索引 `uk_employee` 兜底，代码只负责把冲突翻译成"该员工已有账号" |
| 离职必须自动停号 | 前员工能登录 = 客户名单和业绩数据随时可导出。这类事故的标准版本就是"人走了三个月，账号还在" |
| 初始密码只出现一次 | 返回给管理员是为了转达给员工；数据库只有 BCrypt 哈希，接口不提供"再查一次"。忘了只能重置 |
| 最后一个启用的管理员不能停用、也不能被摘掉 ADMIN | 否则没人能管系统，只能改数据库，客户现场做不到 |

**为什么"看员工列表"和"改密码"权限不同**：列表是人事信息（手机号、岗位），
给 `ADMIN + SUPERVISOR`；账号操作等价于"可以冒充任何人"，只给 `ADMIN`。
这两件事用同一套数据范围控制是管不住的 —— 客服的 `ScopeType` 也是 TEAM。

**权限改动为什么立即生效**：每个请求都重新查角色（不走 Session），
所以在账号页把某人从设计师提成主管，对方**下一次点击**就生效，不用等重新登录。
已实测：同一个旧 token，改角色前 403、改完 200、收回再变 403。

## 五条不能碰的红线

1. **金额只能走 `MoneyUtil`**，禁止裸写 `BigDecimal.multiply`。漏一次 `setScale`，
   MySQL 会静默截断（不是四舍五入），个人业绩之和就少于订单总额。
2. **分成比例只存万分比整数**（10000 = 100%）。小数存 33.33% 三份永远凑不满 100%。
3. **`performance_ledger` 只追加**。Mapper 故意不提供 update/delete，改数一律追加反向流水。
4. **数据权限必须双层**：`@DataScope` 管接口准入，`DataScopeGuard` 改写参数管行级范围。
   只做前者，改个 URL 参数就绕过去了；只做后者，新增接口容易漏。
5. **`CurrentUserHolder.clear()` 必须在 `finally` 里调**。Tomcat 线程复用，
   不清就会串号，出现"设计师看到主管数据"这种无法复现的事故。

## 验证记录（2026-11-13 更新）

**JDK 17 + Maven 3.9.16 + MySQL 8.0.28 均已安装，以下为真实编译、测试、运行结果：**

| 项 | 结果 |
|---|---|
| `mvn -DskipTests package` | ✅ BUILD SUCCESS，产出 `target/studio-erp-1.0.0.jar`（57MB fat jar） |
| `mvn test` | ✅ Tests run: 39, Failures: 0, Errors: 0（业绩算法 8 + 角色映射 7 + 密码策略 6 + 工资阶梯 11 + 分级底薪 7） |
| `npm run check` | ✅ 4 项全通过（SQL / Java / 分摊算法 / 原型） |
| **稿件链路** | ✅ 上传 V1 → 退回（必填原因 + 自动生成返工记录）→ 上传 V2 → 通过；版本号服务端递增；.exe 上传被拒 45003 |
| **返工统计** | ✅ 2 版 / 1 退回 / 1 返工 / 通过率 50%，按人分组正确 |
| **状态时间线** | ✅ 提单→定价→推进全程留痕，设计师与管理员可见同一份 |
| **审计落库** | ✅ 底薪调整（含撤销）、角色变更、稿件审核全部进 audit_log，带操作人与 IP |
| **参数异常** | ✅ 漏传/类型错/坏 JSON 一律 400 + 明确文案（原先是 50000「系统开小差」） |
| 应用启动 | ✅ `Started StudioErpApplication in 1.87 seconds`，Tomcat 监听 8080 |
| Flyway 迁移 | ✅ 6 个脚本 success=1（v1 schema / v2 init / v3 must_change_pwd / v4 uk_employee / v5 订单手填+工资阶梯 / v6 员工级别+分级底薪） |
| 建表结果 | ✅ 实际落库 29 张业务表 / 280 字段 + `flyway_schema_history` |
| 初始化数据 | ✅ dept 3 / sys_role 5 / price_item 5；管理员由 Runner 创建 |
| **登录链路** | ✅ 登录发 token、`/me` 返回角色与 `scope=ALL`、登出失效 |
| **未登录拦截** | ✅ 无 token 访问业务接口 → HTTP 401 + code 40100 |
| **登录失败锁定** | ✅ 连错 5 次后第 6 次返回 40103，锁定期间正确密码同样被拒 |
| **强制改密** | ✅ 改密前访问业务接口 → 40104；改密后旧 token 立即失效；弱密码被拒 |
| **行级数据隔离** | ✅ 设计师甲传 `employeeId=乙` 仍只返回自己的 2 条；管理员传同参数能看到乙的 1 条 |
| **员工列表分页** | ✅ `total=3`、`pageSize=2` 返回 2 条；请求 `pageSize=1000` 被截断为 200 |
| **员工校验** | ✅ 工号重复 40010、岗位非法 40011、手机号格式 40000、岗位小写入库自动大写 |
| **员工权限** | ✅ 设计师访问 `/employees`、`/accounts` → 40300；`/depts` 200 |
| **开账号** | ✅ 不传登录名默认取工号；返回 10 位随机初始密码；重复开号 40014 |
| **首登强制改密** | ✅ 新账号 `mustChangePwd=true`，未改密访问业务接口 → 40104 |
| **离职联动** | ✅ 员工置离职后账号 `accountStatus=0`；该账号登录 40102；离职人员再开号 40012 |
| **重置密码** | ✅ 返回新密码；旧 token 立即 401；旧密码登录 40101 |
| **角色即时生效** | ✅ 同一 token：改角色前 403 → 加 SUPERVISOR 后 200 → 收回后 403 |
| **管理员保护** | ✅ 摘掉自己 ADMIN 角色 40016；停用自己账号 40015；自己离职 40015 |
| **并发防线** | ✅ `sys_user` 上 `uk_username` + `uk_employee` 双唯一索引已建（复测 `SHOW INDEX`） |
| 404 语义 | ✅ `GET /` 返回 HTTP 404 + code 40400，不再被兜底吞成 200 |
| 中文日志 | ✅ 修掉 Windows 控制台编码导致的 `???`，日志可读；本轮全程 0 条 ERROR |
| **订单状态机** | ✅ 待定价→已交付 被拒（41001）；已交付→取消 被拒；有回款取消 被拒（提示先退款冲销） |
| **定价校验** | ✅ 分成 80% → 41003 且提示到具体行；重复定价 41001；已收款单改价 41006 |
| **跨月业绩** | ✅ 3 万元订单：11-05 收 2 万 → 计入 11 月；12-15 收 1 万 → 计入 12 月（**不并入 11 月**） |
| **多人分成** | ✅ 8,000 元行 70/30：甲 +5,600、乙 +2,400，合计精确等于 8,000 |
| **工资阶梯** | ✅ 甲 11 月业绩 20,000 → 45% → 提成 9,000；12 月 27,600 → 50% → 13,800 |
| **阶梯提示** | ✅ 18000 档显示"距下一档还差 X 元、达到后多拿 Y 元"；最高档不再提示 |
| **阶梯校验** | ✅ 断档（20000~21000 空档）→ 44002 并指出具体两档；首档非 0 → 44002 |
| **底薪（分级）** | ✅ 普通 1500 / 精英 2000 / 主管 3000 由 `salary_base_policy` 带出；甲应发 = 1500 + 9000 = 10500 |
| **底薪（个人特例）** | ✅ 单独指定 1800 → `baseSalarySource=PERSONAL`、应发 10800；传 null 撤销后自动回到 1500（LEVEL） |
| **底薪政策改动** | ✅ 政策改 1600，甲工资立即变 10600，无需重启；缺级别 → 44006，负数 → 40000 |
| **级别维护** | ✅ 乙升为 ELITE → 底薪 2000；非法级别 `SENIOR` → 40018 |
| **阶梯整表替换** | ✅ 三档原样回写 200；修复此前被全表删除保护拦截导致的 500 |
| **金额遮蔽** | ✅ 设计师订单列表/详情响应中 `totalAmount`、`paidAmount`、`unitPrice`、`amount` 全部缺失 |
| **分成遮蔽** | ✅ 乙看协作单只返回自己那条 30%，甲的 70% 整条不在数组里 |
| **越权查看** | ✅ 乙查看自己未参与的单 → 40300；设计师访问 `/salary/team`、改阶梯 → 40300 |
| SQL 语法 / 结构 | 29 张表全部有主键，索引列存在，DROP 与 CREATE 配对，280 字段 |
| **业绩汇总接口** | ✅ `/performance/summary` 一次返回 4 个 KPI + 近 12 月趋势 + 本月订单明细；设计师只拿到自己那份 |
| **前端构建** | ✅ `npm run build` 通过，按视图拆包（element 1.1MB / echarts 1.0MB 单独分包） |
| **前端联调** | ✅ 无头浏览器实跑：设计师登录 → 业绩/工资/订单三页数据全部正确；管理员 → 录单页/团队工资 |
| 控制台错误 | ✅ 0 条（修掉了缺 favicon 的 404） |
| 分摊算法 | 8/8 边界用例通过；1000 组随机压测两条不变量 100% 成立 |
| 原型 | 标签闭合正常、零外部依赖 |

> 当前有 Controller 的域：认证、员工、账号、订单、稿件与审核、回款与发票、业绩与工资、
> 报表与 Excel 导出，共 55 个 REST 接口。客户档案与价目表未做接口（客户名与单价手填），
> 表结构保留。

## 开发库测试账号

| 账号 | 密码 | 角色 | 用途 |
|---|---|---|---|
| `admin` | `********` | ADMIN | 管理员（初始密码在首登时已被强制改掉） |
| `designer1` | `********` | DESIGNER | 行级隔离验证（甲） |
| `designer2` | `********` | DESIGNER | 行级隔离验证（乙） |
| `Z003` | 已重置 | DESIGNER | **已离职**，账号已停用，用于验证离职联动 |

> 开发库密码不入库，本地按 `ADMIN_INIT_PASSWORD` 自行初始化。生产环境由该变量生成首号，其余账号由管理员在界面上建。

## 后续规划

主链路已闭环：提单 → 定价 → 回款核销 → 业绩确认 → 工资核算 → 报表导出，
每一步都有真实 HTTP 请求和无头浏览器跑过。

尚未动工（表结构已就绪，缺接口）：

1. 客户档案与价目表（当前客户名与单价手填）
2. 收款计划（订金 / 尾款节点）与账龄提醒
3. 工资条 PDF 导出（当前提供 Excel 导出）

### 已确认的两个口径

**1）阶梯算法：跳点**

需求原话是"业绩在 15000 以下是 40%，20000 是 45%，25000 是 50%"。
这三句话能同时成立的唯一分档是 **0~20000 是 40%、20000~25000 是 45%、25000 以上 50%**
（若把 15000 当分界点，"20000 是 45%"就不成立了）。

"阶梯比例"有两种算法，结果差很多：

| 业绩 26,000 | 跳点（已确认） | 超额累进 |
|---|---|---|
| 提成 | 26,000 × 50% = **13,000** | 20000×40% + 5000×45% + 1000×50% = **10,750** |

已按 **跳点** 实现并确认（"做到 20000 拿 45%"读起来就是整笔按该档）。
要换成累进，改 `application.yml` 的 `studio.salary.tier-mode: MARGINAL` 即可，代码不用动；
分档数字也能由管理员通过 `PUT /salary/tiers` 直接改，不必重新部署。

**2）底薪：普通 1500 / 精英 2000 / 主管 3000**

存在 `salary_base_policy` 表里，管理员通过 `PUT /salary/base-policy` 整表替换，改完立即生效。
员工挂级别（`employee.emp_level`）自动带出底薪；个别谈好特殊待遇的，用
`PUT /salary/base/{employeeId}` 单独指定，传 null 即撤销特例回到级别金额。
