# Forge 前端基座（含后端菜单切片）设计规格

| 项 | 值 |
|---|---|
| 日期 | 2026-11-18 |
| 状态 | 已确认（会话内设计评审通过；uni-app 分仓修订已并入） |
| 决策人 | luckybearbear |
| 阶段 | Phase 3 首块（前端基座）+ 后端菜单切片 |
| 前置 | Phase 1/2 全部交付（33 模块/197 测试）；P3 优先两项已兑现 |

---

## 1. 背景与目标

后端底座已过"能支撑业务开发"阈值（认证/RBAC/租户/审计/starter 装配齐备且测试实证）。前端基座是下一个最有价值的验证方：它将立刻检验 Result/错误码消费约定、401/403 拦截、token 生命周期、分页——底座的核心承诺。

本 spec 交付两块：
1. **后端菜单切片**（前端动态路由的数据源，先行）
2. **forge-web 前端基座**（admin 管理台 MVP：登录/布局/权限机制/两示例页）

首个验证项目（模具 SaaS）的业务前端将在此基座上开发。

## 2. 决策表（会话内定案）

| 决策 | 定案 | 理由与代价 |
|---|---|---|
| 搭建范式 | **全新搭建 + 选择性借鉴 yudao-ui-admin-vue3** | 后端契约不同（Result 字符串码/Bearer/无租户头/无 refresh token），裁剪 21 业务域包袱不如按需抄 7 项模式 |
| 菜单来源 | **后端菜单驱动** | yudao 模式；需先行建菜单 API（§3）；权限与菜单**合一模型**避免新绑定表 |
| 工程形态 | **同仓不同 workspace**：`forge-web/`（pnpm workspace，web 系）+ `forge-mobile/`（独立占位） | uni-app 工具链钉版本/双 vue 实例风险/小程序无 axios——不与 web 系共 workspace |
| 共享策略 | **两端共享仅 DTO 类型**；最佳源头=后端 OpenAPI 生成（`openapi-typescript`，Phase 3+ 候选，不进 MVP）；api-client 各端实现 | transport 不同（axios vs uni.request）；MVP 期 admin 手写 types 够用 |
| MVP 深度 | 可用骨架 + 两示例页（租户管理/权限字典） | 交付即业务页开发起点 |

## 3. 后端菜单切片（先行）

### 3.1 合一模型（权限即菜单）

现有 RBAC 为权限码驱动（role→permission codes）。**菜单=携带路由元数据的权限点**，不建新表、不加绑定表：

- `PermissionEntity`（auth_permission）扩展**可空列**：`parent_id`、`path`、`component`、`icon`、`sort`、`visible`
- type 语义扩展为四值：
  - `DIR`：目录，仅分组，不参与鉴权（不可授权，不出现在 authorities）
  - `MENU`：菜单=权限点（code 即权限码，参与授权）；携带 path/component/icon/sort/visible
  - `BUTTON` / `API`：纯权限码（现状不变）
- **可见性规则**：MENU/BUTTON 对用户可见/生效 ⟺ 其 code ∈ 用户 authorities；DIR 可见 ⟺ 存在可见后代（折叠空目录）
- 角色授权现有机制（role→codes）自动覆盖菜单可见性，零新增绑定

### 3.2 端点

| 端点 | 说明 |
|---|---|
| `GET /app-api/auth/menus` | 登录用户可见菜单树：authorities ∩ MENU 型 + 祖先 DIR 链；返回 `[{id,parentId,type,name,path,component,icon,sort,visible}]` 树形 |
| `/admin-api/permissions` 扩展 | CRUD 支持新列（树形管理）；返回形态改树形列表（带 children）或加 `?tree=true`——取**默认树形**（管理台即树形页） |

### 3.3 迁移与 seed

- Flyway：auth_permission 加 6 列（ALTER，可空）+ demo 菜单树 seed：
  - `仪表盘`（MENU，code `dashboard`，path /dashboard，component system/dashboard/index）
  - `系统管理`（DIR）→ `租户管理`（MENU，code `tenant:manage` 复用现有码，path /system/tenants，component system/tenant/index）、`权限字典`（MENU，code `role:manage` 复用，path /system/permissions，component system/permission/index）
- admin（PLATFORM token）菜单可见性：PLATFORM authority 命中所有 MENU 码？——**不**：seed 的菜单码 `tenant:manage`/`role:manage` 已在字典且 admin 面门槛即 PLATFORM；菜单树对 admin 返回**全部 MENU**（`PLATFORM` authority 视为超级，§5.4 指令同规则）

### 3.4 测试

树组装/按 authorities 过滤（无权限菜单不返回）/空 DIR 折叠/祖先链补全/双租户隔离（字典全局表，菜单树无租户语义——但 /menus 依赖登录 token）/admin 全量可见。

## 4. forge-web 工程结构

```
forge/
├── forge-web/                      # web 系（pnpm workspace）
│   ├── pnpm-workspace.yaml         # apps/*, packages/*
│   ├── package.json                # 根脚本（-r dev/build/lint）+ engines(node≥20.19, pnpm≥10)
│   ├── .gitignore                  # node_modules/dist 等
│   ├── apps/
│   │   └── admin/                  # 管理台（MVP 主体）
│   └── packages/
│       └── shared/                 # @forge/web-shared：api 客户端(axios)/Result 语义/类型/存储封装——web 系复用
└── forge-mobile/                   # 移动端占位（独立 uni-app 工程，README 说明形态与启动时机；不建 package）
```

- forge/ 根 `.gitignore` 增补 node 系条目
- 根 README 模块导览增 forge-web/forge-mobile 行

## 5. apps/admin 技术栈与核心机制

### 5.1 技术栈（跟随 yudao 参考，2026.10 快照线）

Vue `3.5` + TypeScript + Vite `8` + element-plus + pinia `3`（+persistedstate）+ vue-router `5` + unocss + axios；eslint（flat config）/prettier（带 cache）；vitest。**舍弃**（附录 B 清单）：i18n、TagsView、Setting 抽屉、验证码、社交登录、API 加密、字典体系、mock。

### 5.2 请求层（packages/shared，三层借鉴 yudao）

- `config.ts`：baseURL=`VITE_API_URL`（默认 `/admin-api`）、timeout 30s、白名单（/auth/login）
- `service.ts` 拦截器：请求加 `Authorization: Bearer <token>`（白名单跳过）；响应处理 **Result 字符串码语义**：`code === 'OK'` → 返回 `data`；业务码失败 → ElMessage 报 `message` 并 reject；HTTP 401 → 清 token → 跳 /login（**无 refresh token**，不做队列回放——与 yudao 的关键差异，代码注释/README 记录）；blob 透传
- `index.ts`：get/post/put/del 包装（直接返回 data）+ download/upload
- token 存取经 shared 的存储封装（web-storage-cache 依赖，key 集中管理：ACCESS_TOKEN/user/menus 内存态）

### 5.3 Dev 联调与认证流

- vite `server.proxy`：`/admin-api` → `http://localhost:8080`（**代理模式**，非 yudao 的 CORS 直连——后端未配 CORS；生产同源反代）；.env.local 管理 `VITE_API_URL`/`VITE_PORT`
- 登录：平台管理员表单（username/password，无租户字段——admin 端语义；**记住我**只存账号不存密码，30 天，借鉴 yudao）→ `POST /admin-api/auth/login` → 存 token → 守卫期拉 `/me` + `/menus`
- 布局用户菜单：改密对话框（`PUT /admin-api/auth/password`——2b 终审补的端点；成功后清 token 跳登录）+ 登出（`POST /admin-api/auth/logout` → 清理 → 跳登录）

### 5.4 动态路由与权限

- 路由守卫（`permission.ts` 副作用注册，借鉴）：白名单 /login；已登录未加载用户时并发去重 → `useUserStore.loadMe()` + `usePermissionStore.generateRoutes(menus)` → addRoute → redirect 重放；**菜单/用户信息只存 pinia 内存态，不缓存 localStorage**（精简，避免一致性）
- 动态路由：后端菜单树 → `import.meta.glob('../views/**/*.{vue,tsx}')` 映射 component 串；顶级挂 Layout、目录级用 ParentView；末尾 404 兜底（借鉴 yudao routerHelper）
- 权限指令：`v-permission="['tenant:create']"`（authorities 任一命中；**`PLATFORM` 视为超级全通过**）+ 函数式 `hasPermission()`；无权限 removeChild（借鉴）
- 菜单码即权限码：页面内按钮权限直接用后端 seed 的 API 型码（tenant:create 等）

### 5.5 布局与页面（MVP 面）

- `Layout`：侧边菜单（el-menu，据菜单树渲染+icon）/面包屑/用户下拉——**简版**（无 TagsView/主题/锁屏）
- 页面：`Login`、`Dashboard`（骨架：欢迎卡+统计占位）、`system/tenant`（表格+搜索+弹窗 CRUD，连 /admin-api/tenants 全套）、`system/permission`（树形表格 CRUD，连 /admin-api/permissions）

## 6. 质量与工程化

- vitest：shared 层（Result 处理分支/401 流/存储封装）+ admin 守卫与 hasPermission 逻辑（组件测试按需）
- 根脚本统一 `pnpm -r lint / build`；CI 增 web job（node 镜像：pnpm install→lint→build；与既有 verify job 并列，仅 forge-web 变更触发或全量——取全量简版）
- README（forge-web 根+admin）：启动方式（后端 demo profile + pnpm dev）、代理说明、账号（admin/admin123+改密提示）

## 7. 验收标准（Done）

1. 后端：`./mvnw verify` 全绿（197+菜单切片新增）；/menus 树过滤语义有容器测试实证
2. 前端：`pnpm -r lint && pnpm -r build` 通过；vitest 全绿
3. **手检流**（记录在案）：起 demo 后端 → forge-web dev → admin/admin123 登录 → 菜单树渲染（仪表盘/系统管理）→ 租户 CRUD 增删改查 → 权限字典树编辑 → 改密（旧 token 失效跳登录）→ 登出
4. 按钮权限实证：无 `tenant:create` 的受限用户登录 admin？——admin 面仅 PLATFORM（受限用户属 app 面），MVP 以指令单测+PLATFORM 全通过规则覆盖
5. CI web job 就位；文档三处（根 README/forge-web/forge-mobile 占位）

## 8. 风险与实施时验证项

| 风险 | 应对 |
|---|---|
| vue-router 5 / Vite 8 / TS 6 较新，脚手架组合可能有兼容毛刺 | 实施时以 create-vue 或手搭最小组合先冒烟；必要时 TS 降至 5.x（记录偏离） |
| element-plus 与 unocss 的样式 reset 冲突 | unocss preset 与 ep 样式引入顺序实测；冲突则 MVP 去掉 unocss（记录） |
| 菜单 component 串与文件路径约定漂移 | shared 的 routerHelper 用常量约定根 `views/`；seed 的 component 路径与页面文件一一对应，测试断言 |
| admin 全量菜单的超级规则与现有 PLATFORM 门槛耦合 | 指令/菜单两处统一 `PLATFORM` 常量，单测钉住 |
| dev proxy 与后端 8080 被占 | README 记录改 VITE_PORT/代理目标 |

## 9. 出界清单

- 移动端 uni-app 工程初始化（forge-mobile 仅占位 README）
- OpenAPI 生成 types 包（openapi-typescript）——Phase 3+ 候选
- refresh token/无感刷新、i18n、TagsView、主题、验证码、字典体系、mock server
- app 面（/app-api）页面——属业务应用
- 前端 e2e（Playwright）——Phase 4

## 附录 A：借鉴清单（yudao-ui-admin-vue3，2026.10-snapshot）

请求层三层结构 / 401 处理骨架（去 refresh 改直跳）/ 动态路由 routerHelper（glob 映射+Layout/ParentView+404）/ v-hasPermi 指令模式（改名 v-permission）/ web-storage-cache 存储+key 集中管理 / 记住我安全做法（不存密码）/ 多 mode env+构建脚本健壮性（chunk 失效自动 reload 可选借鉴）。

## 附录 B：舍弃清单

21 业务域 views 及重依赖（bpmn/gantt/form-create/monaco…）/ 租户双 header 体系（TenantVisit）/ OAuth2+社交登录语义 / API 加解密 / 全局字典 store+DictTag / localStorage 整包用户缓存 / i18n / Setting/锁屏/主题。

## 附录 C：错误码消费约定（前后端契约备忘）

成功 `code === 'OK'`；错误码字符串（FORGE-xxxx/IDENT-xxxx…）前端仅 toast `message`，**不按码分支**（分支语义由 HTTP 401/403 承担）；401=未认证→重登、403=无权限→toast；分页暂无统一形状（后端 PageResult 未在示例端点使用）——本批不改后端，示例页用全量列表。
