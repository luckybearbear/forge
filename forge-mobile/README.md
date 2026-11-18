# forge-mobile（占位）

> 移动端工程**占位目录**——本期（前端基座 Phase 3 首块）只建说明不建工程（spec
> `docs/superpowers/specs/2026-11-18-forge-web-foundation-design.md` §9 出界项）。

## 工程形态（启动时机：首个移动端业务立项时）

独立 uni-app 工程（HBuilderX/CLI 皆可），形态预期：

- 目录：`forge-mobile/` 根即 uni-app 项目根（`pages/`、`manifest.json`、`uni.scss`…），
  或内部再分 `apps/*` 多端壳——启动时按业务面定；
- 与后端协作：**两端共享仅 DTO 类型**（最佳源头=后端 OpenAPI 生成 `openapi-typescript`，
  Phase 3+ 候选）；api-client 各端自建（transport 不同，见下）；
- 认证：`/app-api` 面（租户账号体系，非 admin 面）；token 生命周期遵循底座约定
  （无 refresh token，401 → 重登）。

## 为何不与 forge-web 共用 pnpm workspace（spec §2 三条理由）

1. **uni-app 工具链钉版本**：uni-app 编译器/依赖树对 Vue/Vite 版本强绑定且发布节奏独立，
   与 web 线（Vite 8 + Vue 3.5 最新线）并轨会互相锁死升级步调；
2. **双 Vue 实例风险**：uni-app 运行时（含小程序端）自带 Vue 定制实现，与 workspace
   hoist 出的 web 线 Vue 共存时易出现双实例/类型错配；
3. **小程序端无 axios**：请求层 transport 不同（axios vs `uni.request`），web 系的
   `@forge/web-shared`（axios 拦截器/浏览器 storage）在移动端不可复用——共享层收益仅剩
   DTO 类型，不值得为此共仓共依赖树。

## 启动前的准备清单（立项时）

- [ ] 定 uni-app 版本线与 CLI/HBuilderX 基线；
- [ ] DTO 类型来源决策（OpenAPI 生成 vs 手写镜像）；
- [ ] `/app-api` 登录/刷新策略对齐（无 refresh token：401 直跳重登）；
- [ ] 本 README 升级为正式工程 README，删除占位说明。
