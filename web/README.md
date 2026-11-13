# web · 前端工程

设计业务管理系统的内部界面。Vue 3 + TypeScript + Vite + Element Plus。

## 启动

```bash
# 先确保后端在 8080 运行（见上级 README）
npm install      # 首次
npm run dev      # → http://localhost:5173
npm run build    # 产出 dist/，交给 Nginx 或任意静态服务器
```

`vite.config.ts` 已把 `/api` 代理到 `127.0.0.1:8080`，所以后端不需要开 CORS。

## 目录

```
src/
├─ api/           按域拆分的接口 + 类型（类型必须与后端响应一字不差）
│  ├─ http.ts     axios 封装：统一解包 R<T>、注入 token、401 跳登录
│  └─ ...         auth / performance / salary / order
├─ stores/user.ts 登录态；只持久化 token，用户信息每次刷新重拉
├─ router/        hash 路由 + 守卫（未登录跳转、管理页拦截）
├─ layout/        AppLayout：侧栏、顶栏、改密对话框
├─ components/    OrderItemsEditor：明细 + 分成人编辑器（录单与定价共用）
└─ views/         登录 / 我的业绩 / 我的工资 / 订单管理 / 订单录入 / 团队工资
```

## 几条约定

**接口解包**：后端所有响应都是 `{code,msg,data}`。`http.ts` 在拦截器里统一解成 `data`，
页面里 `await getXxx()` 拿到的就是业务对象。不要在页面里再写 `.data.data`。

**null 字段可能整条缺失**：后端配了 `default-property-inclusion: non_null`，
值为 null 的字段不会出现在 JSON 里。所以类型里几乎所有字段都是可选的，
模板里取值一律用 `?.` 和 `??`。

**金额遮蔽在服务端**：设计师看到的订单列表/详情里没有 `totalAmount`、`paidAmount`、
`unitPrice`，业绩明细里只有 `mySignAmount / myPaidAmount / myUnpaid`。
前端不要试图"算回"整单金额，也不要把这些字段做成可选后随便显示。

**角色只控界面**：`user.isManager` 决定菜单和按钮显示与否，真正的权限在后端。
前端这套只是为了别让用户点了半天才被拒，不是安全边界。

**提成比例用百分比填**：界面上填 70 表示 70%，提交时乘 100 转成后端要的万分比（7000）。
转换只在 `OrderItemsEditor` 一处做，避免某个页面忘了乘。

## 已知取舍

- **Element Plus 全量引入**：没有做按需自动导入。内部系统的用户是固定几十个人、
  内网环境，多下载 300KB 换来"少一层构建插件配置"，权衡后选了简单。
- **hash 路由**：history 模式需要服务器回退配置，少一行刷新就 404。
- **没有做单元测试**：界面逻辑靠无头浏览器跑真实流程验证（登录 → 三个页面 → 数据核对），
  比 mock 一堆组件的收益更直接。
