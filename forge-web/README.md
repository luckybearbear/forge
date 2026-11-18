# forge-web

Forge web 系前端（pnpm workspace）：**apps/admin**（管理台）+ **packages/shared**
（`@forge/web-shared` 请求层/存储/类型）。与 Java 仓同仓不同 workspace——构建互不相扰
（Java 侧 Maven，本目录 pnpm），CI 经根 `.gitlab-ci.yml` 的 `web` job 全量跑。

## 快速开始

前置：Node ≥ 20.19（推荐 22.x）、pnpm ≥ 10（`npm i -g pnpm@10` 或 corepack）。

```bash
# 1) 起后端（demo profile：seed 两租户三账号 + 平台管理员 admin/admin123）
#    仓库根执行；端口被占时加 --server.port=18080 之类，并同步改 apps/admin/vite.config.ts 代理目标
SPRING_PROFILES_ACTIVE=demo ./mvnw -pl forge-distributions/forge-demo-app -am spring-boot:run
#    （-pl 无 -am 需先 install 兄弟模块；后端 README 见 forge-distributions/forge-demo-app/）

# 2) 起前端
cd forge-web
pnpm install
pnpm dev          # = pnpm -r dev；admin 默认 http://localhost:5173
                  #   /admin-api 经 vite proxy → http://localhost:8080（代理模式，无 CORS）

# 3) 登录 apps/admin：admin / admin123（初始口令，首次登录后立即改密——PUT /admin-api/auth/password，
#    改密会吊销该管理员全部既有 token 需重登）
```

全量校验（CI 同款）：`pnpm install --frozen-lockfile && pnpm -r lint && pnpm -r build && pnpm -r test`。

## 结构

```
forge-web/
├── apps/
│   └── admin/            # 管理台 MVP（详见其 README：启动/结构/约定）
└── packages/
    └── shared/           # @forge/web-shared：Result 语义 axios 封装 / web-storage-cache 存储封装 /
                          #   后端同构 DTO 类型（MenuNode/TenantDTO/LoginResponse…）——web 系复用
```

- 根脚本统一 `-r` 透传：`pnpm dev/build/lint/test`；lint 根级先跑 prettier --check 再各包 eslint；
- `engines`：node ≥ 20.19、pnpm ≥ 10（`pnpm-workspace.yaml` 管 apps/* 与 packages/*）；
- 便携 Node 22 开发位（本机全局 18 时）：PATH 前缀指向解压的 node22 目录即可，不动全局。

## 约定（web 系）

- **Result 字符串码语义**：成功 `code === 'OK'` 直接吐 `data`；业务码失败（FORGE-xxxx 等）仅
  toast `message`、**不按码分支**（分支语义由 HTTP 401/403 承担，前后端契约备忘见设计规格附录 C）；
- **401 直跳无 refresh**：HTTP 401 → 清 token → 整页跳 /login。底座无 refresh token、不做
  失败队列回放（与 yudao 的关键差异，有意为之）；403 → toast 无权限；
- **token/用户/菜单只存内存态**（pinia），不进 localStorage；记住我只存账号不存密码（30 天）；
- **后端菜单驱动动态路由**：component 串约定根 `views/`（如 `system/tenant/index`），
  seed 的 component 与页面文件一一对应（`viewsRegistry.spec.ts` 断言钉死）。

## 已知取舍（MVP）

| 取舍                  | 说明                                                               |
| --------------------- | ------------------------------------------------------------------ |
| 无 refresh token      | 401 直跳重登；token 过期即整会话失效（底座安全姿态）               |
| element-plus 全量引入 | bundle ~888kB；页面级组件经路由懒加载分包，按需引入 Phase 3+ 候选  |
| TypeScript ~5.9       | Vite 8/vue-tsc 线上 TS 6 尚有毛刺，钉 5.9（spec §8 预案授权）      |
| 无 unocss             | 与 ep 样式 reset 冲突实测后按 spec §8 预案移除，MVP 用 EP 自带样式 |
| 分页无统一形状        | 示例页用全量列表（后端 PageResult 未进示例端点）；Phase 3+ 统一    |
