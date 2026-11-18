# Forge 前端基座（含后端菜单切片）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付后端菜单切片（菜单=带路由元数据的权限点，合一模型）+ forge-web 前端基座（pnpm workspace / Vue3+Vite admin MVP：登录/布局/动态路由/权限指令/两示例页/改密登出）。

**Architecture:** 后端：authorization 扩菜单查询（contract `MenuQueryService`）+ identity `/app-api/auth/menus`（token authorities 直取，快照语义）；前端：`forge-web/`（pnpm workspace：apps/admin + packages/shared），后端菜单驱动动态路由，`v-permission` 指令（PLATFORM 超级）。

**Tech Stack:** 后端既有栈；前端 Vue 3.5 + TS + Vite 8 + element-plus + pinia 3(+persistedstate) + vue-router + unocss + axios + vitest + eslint(flat)/prettier；**便携 Node 22**（D:\devloper\soft\node22，PATH 前缀，不动全局 18）

**Spec:** `docs/superpowers/specs/2026-11-18-forge-web-foundation-design.md`

## Global Constraints

- 后端约束同前（`${revision}`/无 Lombok/UTF-8/conventional+中文/单条提交/容器测试/FOREIGN_CODES 纪律[本批不加新码]/JDK 25 路径文件 `.superpowers/sdd/<本工作区>/jdk25-path.txt`）
- **菜单合一模型铁律**：不建新表/不加绑定表；DIR 不参与鉴权；MENU code 即权限码；可见性=code∈authorities（`PLATFORM` 视为超级全通过，指令与菜单两处统一常量）；空 DIR 折叠；祖先链补全
- 前端：**Result 字符串码语义**——`code==='OK'` 返 data；业务码失败 toast message 不按码分支；HTTP 401→清 token 跳 /login（无 refresh/无回放队列）；403→toast；Bearer 白名单仅 /auth/login
- 菜单/用户信息**只存 pinia 内存态**不进 localStorage；记住我只存账号不存密码（30 天）
- dev 代理模式（vite proxy /admin-api→localhost:8080），不做 CORS
- component 串约定根 `views/`；seed 路径与页面文件一一对应（测试断言）
- 脚手架风险预案（spec §8）：TS 兼容毛刺→降 5.x；unocss×ep reset 冲突→MVP 去 unocss（均记录偏离）；vue-router 无 5.x→取 4.x 最新
- 计划风格：关键/新颖逻辑全码；脚手架与 CRUD 页给精确文件清单+配置要点+验收断言

---

### Task 1: 后端菜单切片（authorization 菜单查询 + identity /menus + admin 字典树 CRUD + 迁移 seed）

**Files:**
- Modify: `forge-contracts/forge-contract-authorization/`：新增 `MenuNodeDTO.java`（record：`String id, String parentId, String type, String code, String name, String path, String component, String icon, Integer sort, Boolean visible, List<MenuNodeDTO> children`）+ `MenuQueryService.java`（`List<MenuNodeDTO> visibleMenus(Set<String> authorities)`）
- Modify: `forge-services/forge-service-authorization/`：
  - `domain/PermissionEntity.java`：+6 可空列映射（parent_id/path/component/icon/sort/visible）
  - `domain/PermissionRepository.java`：+`findByCodeIn(Collection<String>)`、`findAll()` 已有
  - `app/PermissionService.java`：+`visibleMenus(Set<String> authorities)`（**全码**：rows→MENU 可见集[code∈authorities 或 authorities 含 PLATFORM]→DIR 链补全[自底向上标记有可见后代的 DIR]→按 parent 建树[roots=parent 空]→sort 升序稳定排序→DTO 递归）+ `tree()`（admin 字典树：全量含 BUTTON/API，children 组装）
  - `app/MenuQueryServiceImpl.java`（模块根包，契约实现）
  - `controller/admin/PermissionAdminController.java`：返回改 `tree()` 默认树形；create/update 请求 DTO +6 可空字段
- Modify: `forge-services/forge-service-identity/controller/app/AuthAppController.java`：+`GET /menus`（SecurityContext 取 ForgeAuthentication→`authentication.getAuthorities()` 转 Set<String>→menuQueryService.visibleMenus→Result；**不经 UserPermissionsQuery——token 快照语义**；`MenuQueryService` 注入用 ObjectProvider 缺省空列表[identity 独立可测]）+ identity pom +contract-authorization 已有
- Create: `forge-services/forge-service-authorization/src/main/resources/db/migration/V202611190000__alter_permission_menu.sql`：

```sql
ALTER TABLE auth_permission
    ADD COLUMN parent_id VARCHAR(36),
    ADD COLUMN path VARCHAR(256),
    ADD COLUMN component VARCHAR(256),
    ADD COLUMN icon VARCHAR(64),
    ADD COLUMN sort INT,
    ADD COLUMN visible BOOLEAN NOT NULL DEFAULT TRUE;
-- 菜单树 seed（固定 id，目录在前）
INSERT INTO auth_permission (id, code, name, type, parent_id, path, component, icon, sort, visible, created_at, deleted)
VALUES ('perm-node-dir-sys',  'sys-dir',       '系统管理', 'DIR',  NULL,       NULL,             NULL,                    'Setting',  100, TRUE, now(), FALSE),
       ('perm-node-dashboard','dashboard',     '仪表盘',   'MENU', NULL,       '/dashboard',     'system/dashboard/index','Odometer', 10,  TRUE, now(), FALSE),
       ('perm-node-tenant',   'tenant:manage', '租户管理', 'MENU', 'perm-node-dir-sys', '/system/tenants', 'system/tenant/index', 'OfficeBuilding', 110, TRUE, now(), FALSE);
UPDATE auth_permission SET type='MENU', parent_id='perm-node-dir-sys', path='/system/permissions',
       component='system/permission/index', icon='Key', sort=120
WHERE code='role:manage' AND deleted=FALSE;
```

（seed 组件路径与 Task 4 页面文件一一对应——Task 4 验收断言。）
- Modify: `forge-distributions/forge-demo-app/`（`DemoDataSeeder`：ADMIN 角色授权差集机制已自动含新码——**验证**即可；若 seeder 显式列码则补）+ demo 集成测试 +`menusForAdminIncludesAllMenuNodes`（admin token /menus 全 MENU 可见）
- Test（authorization，容器）：visibleMenus 五语义——按 authorities 过滤（无权菜单不返回）/PLATFORM 超级全量/空 DIR 折叠/祖先 DIR 链补全/sort 排序；identity：/menus 经 ForgeAuthentication authorities（stub PLATFORM→全量；stub 受限→过滤）+ ObjectProvider 缺省空列表；admin 字典 CRUD 树形返回 + 新列读写
- Modify: `docs/architecture/dependency-rules.md`（事件表不变；菜单合一模型一段：可见性规则/PLATFORM 超级/token 快照语义）

**Steps:** 契约先行（签名测试红）→ 实体/迁移 → visibleMenus 全码实现 → 端点 → 容器测试绿（197 基线+新增）→ 文档 → Commit `feat(authorization,identity): 菜单切片——权限点携带路由元数据 + /menus 可见菜单树`

---

### Task 2: forge-web 骨架 + packages/shared（含便携 Node 22）

**Files:**
- Create: `D:\devloper\soft\node-v22x\`（便携 Node：`https://nodejs.org/dist/latest-v22.x/` win-x64 zip 解压；shell 用 `export PATH="/d/devloper/soft/node-v22x:$PATH"`——**不动全局 node18**；版本写入 `.superpowers/sdd/<ws>/node22-path.txt`）
- Create: `forge-web/`（pnpm-workspace.yaml[apps/*, packages/*]；根 package.json[engines node≥20.19 pnpm≥10；scripts -r dev/build/lint/test]；.gitignore[node_modules/dist/*.local]；.npmrc[registry 可默认]）
- Create: `forge-web/apps/admin/`（create-vue 或手搭：vite.config.ts[plugin vue+alias @；server.proxy '/admin-api'→'http://localhost:8080'；port env]；tsconfig+tsconfig.node；src/main.ts[router/pinia/persist/element-plus 全量引入+样式/unocss 若用]；env.d.ts；index.html；.env.example[VITE_API_URL=/admin-api, VITE_PORT=5173]；一个冒烟页验证 dev/build 跑通——兼容毛刺在此暴露并按预案处置）
- Create: `forge-web/packages/shared/`（`@forge/web-shared`）：
  - `types.ts`：`Result<T>{code,message,data}`、`LoginRequest/LoginResponse{token,expiresAt,userId,tenantId}`、`MeResponse{userId,tenantId,userType,authorities}`、`MenuNode`（后端 MenuNodeDTO 同构）、`TenantDTO`、`PermissionNode`
  - `config.ts`：baseURL=`import.meta.env.VITE_API_URL ?? '/admin-api'`、TIMEOUT=30_000、WHITE_LIST=['/auth/login']
  - `cache.ts`：web-storage-cache 封装——`AccessToken` 读写删、`RememberUsername` 读写（30d）、key 常量集中
  - `service.ts`（**全码**核心）：

```typescript
// 拦截器骨架（关键分支全码，import 从略）
axios.interceptors.request.use((config) => {
  if (!WHITE_LIST.some((p) => config.url?.includes(p))) {
    const token = getAccessToken();
    if (token) config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});
axios.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') return response; // 透传
    const body = response.data as Result<unknown>;
    if (body.code === 'OK') return body.data as never; // 直接吐 data
    ElMessage.error(body.message || '请求失败'); // 业务码失败：toast 不按码分支
    return Promise.reject(new Error(body.message));
  },
  (error) => {
    const status = error.response?.status;
    if (status === 401) { clearAllAuth(); window.location.href = '/login'; } // 无 refresh token：直跳（spec 关键差异）
    else if (status === 403) ElMessage.error('没有操作权限');
    else ElMessage.error(error.response?.data?.message || '网络异常');
    return Promise.reject(error);
  },
);
```

  - `index.ts`：get/post/put/del 泛型包装（直接返回 data）+ upload/download 占位
  - `api/`：`auth.ts`（login/logout/me/password/menus）、`tenant.ts`（CRUD 全套）、`permission.ts`（树 CRUD）
- Test: shared vitest——service 分支（OK 吐 data/业务码 reject+toast/401 清 token 跳转/blob 透传——axios-mock-adapter 或 interceptor 直测）、cache 封装（TTL/删除）、types 编译
- eslint flat + prettier（cache）配置；`pnpm -r lint && pnpm -r build && pnpm -r test` 全绿

**Steps:** 便携 Node 下载冒烟（node -v≥22）→ workspace/脚手架 → dev/build 冒烟（兼容预案记录）→ shared 全码+类型+api → vitest 绿 → lint/build 绿 → Commit `feat(forge-web): 工程骨架 + @forge/web-shared 请求层/存储/类型`

---

### Task 3: 布局 / 路由守卫 / 权限机制（apps/admin）

**Files:**
- Create: `apps/admin/src/`：
  - `router/index.ts`：静态路由（/login、/ redirect /dashboard、404）+ 动态挂载位
  - `router/permission.ts`（守卫，**全码**）：白名单 /login；无 token→redirect login（带 redirect 参数）；有 token 未加载→并发去重（`loadMe()`+`generateRoutes(menus)`→addRoute 循环→`next({...to, replace:true})` 重放）；已加载→放行；`/menus`、`/me` 失败→清态跳登录
  - `router/routerHelper.ts`（**全码**）：`import.meta.glob('../views/**/*.{vue,tsx}')` 映射 `component` 串（约定根 views/）；顶级 MENU 挂 `Layout`、DIR 级用 `ParentView`（自建极简 `<router-view/>` 组件）；树→routes 递归；末尾 404 兜底
  - `store/user.ts`（pinia）：token/user 内存态 + `loadMe()`；`store/permission.ts`：menus + `generateRoutes()` + `hasPermission()`（**`PLATFORM` 常量超级全通过**，指令同源引用）
  - `directives/permission.ts`（**全码**）：`v-permission="['tenant:create']"`——authorities 任一命中或含 PLATFORM 则保留，否则 `el.parentNode.removeChild(el)`；`main.ts` 注册
  - `layouts/Layout.vue` + `components/`（侧边 el-menu 递归渲染菜单树+icon/面包屑（route matched）/用户下拉[改密/登出]）——简版（无 TagsView/主题）
  - `views/login/index.vue`：admin 表单（username/password/记住我只存账号）→ login → 存 token → `redirect||/dashboard`
  - `components/ChangePasswordDialog.vue`（骨架，逻辑 Task 4 接线）+ `ParentView.vue`
- Test: vitest——`hasPermission`（含 PLATFORM 超级/无命中）、routerHelper（mock glob 标注映射/Layout 挂载/404 兜底）、守卫分支（无 token/已加载/重放——mock store）
- 验收：lint/build/test 绿；（联调冒烟留 Task 5 手检）

**Steps:** 静态路由+login → 守卫+routerHelper 全码 → store/指令 → Layout → vitest → Commit `feat(admin): 布局/动态路由/权限指令/守卫（后端菜单驱动）`

---

### Task 4: 两示例页 + Dashboard + 改密/登出

**Files:**
- Create: `views/system/dashboard/index.vue`（欢迎卡+统计占位卡 ×4）
- Create: `views/system/tenant/index.vue`：el-table（code/name/status/expiredAt 列）+ 顶部搜索（code/name）+ 新增/编辑弹窗（el-form 校验 code/name 必填）+ 启用/停用（confirm）+ 删除——连 `api/tenant.ts`；行操作按钮挂 `v-permission="['tenant:manage']"`（MENU 码即页级权限）；**页面路径 = seed component 串**（system/tenant/index）
- Create: `views/system/permission/index.vue`：el-table tree（row-key=id, tree-props children）全字段列（type/code/name/path/icon/sort/visible）+ 新增/编辑弹窗（parent 下拉树/type 四值 select/BUTTON/API 型隐藏路由字段）+ 删除——连 `api/permission.ts`；同路径断言 system/permission/index
- Create: `views/error/404.vue`；`ChangePasswordDialog.vue` 接线（旧/新/确认三字段校验[新≥8 含字母数字]→ `PUT /auth/password` → 成功 ElMessage+清 token 跳 /login[**旧 token 已被后端吊销**]）；Layout 用户下拉登出（logout→清态→/login）
- Test: vitest——改密表单校验/成功流（mock api）、登录页记住我（只存账号）、（组件测试按需，two pages 以 build+手检为主）
- 验收：`pnpm -r lint && build && test` 绿；**seed component 串 ↔ views 文件一一对应断言**（vitest 里 glob keys 对照常量清单——spec §8 风险项落地）

**Steps:** Dashboard → tenant 页 → permission 页 → 改密/登出接线 → 对应断言 → Commit `feat(admin): 租户管理/权限字典示例页 + 改密登出闭环`

---

### Task 5: CI / 文档 / 终验（含手检流）

**Files:**
- Modify: `.gitlab-ci.yml`：+`web` job（image node:22-alpine[pnpm via corepack]；`cd forge-web && pnpm install --frozen-lockfile && pnpm -r lint && pnpm -r build && pnpm -r test`；rules 同 verify 全量简版）
- Modify: 根 `.gitignore`（node 系条目）；根 README（模块导览 + forge-web/forge-mobile 行 + 前端快速开始[后端 demo profile + pnpm dev + admin/admin123 改密提示 + 代理说明/端口被占改法]）
- Create: `forge-mobile/README.md`（占位：独立 uni-app 工程形态/启动时机/为何不共 workspace——spec §2 三条理由）
- Create: `forge-web/README.md` + `apps/admin/README.md`（启动/结构/约定：Result 语义/401 直跳无 refresh/component 约定/权限指令）

**Steps:**
- [ ] 手检流全录（spec 验收 3）：起 demo 后端（profile demo, 18080 规避占用→proxy 目标同步）→ forge-web dev → admin/admin123 登录 → 菜单树渲染（仪表盘+系统管理>租户/权限字典）→ 租户 CRUD 增删改查 → 权限字典树编辑 → 改密（旧 token 失效跳登录）→ 登出——逐步输出留档（curl 前置起后端 + 浏览器/或以 vitest 联调级覆盖 + 构建产物验证；**至少后端起+登录+menus+一轮 CRUD 以 HTTP 实证**）
- [ ] 验收对照 spec §7 五条逐项（按钮权限实证按验收 4 口径：指令单测+PLATFORM 规则）
- [ ] `./mvnw --batch-mode clean verify` + `pnpm -r lint/build/test` 双绿；统计
- [ ] Commit `feat(forge-web): CI web job + 文档三处 + 手检流终验` → push → 交付报告（提交列表/双栈统计/验收五条/Phase 3 后续候选更新）

---

## Self-Review 记录

- **Spec 覆盖**：§3 菜单切片↔T1（合一模型/端点/迁移/测试五语义/admin 全量规则）；§4 结构↔T2；§5.2 请求层↔T2 service.ts 全码；§5.3 代理/登录/记住我↔T2/T3；§5.4 守卫/动态路由/指令↔T3 全码；§5.5 页面↔T4；§6 质量工程↔T2/T5；§7 五条验收↔T5。
- **占位符**：无 TBD；兼容预案（TS 降级/unocss 去除/vue-router 4 回退/Node22 便携）均为 spec §8 授权的确定性处置。
- **类型一致性**：MenuNodeDTO↔前端 MenuNode 同构；seed component 串↔T4 页面路径断言闭环；authorities 快照↔/menus 直取 authentication 一致。
