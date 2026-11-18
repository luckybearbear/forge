# @forge/admin — Forge 管理台

Vue 3.5 + TypeScript + Vite 8 + element-plus + pinia 3 + vue-router。登录 → 守卫拉
`/me` + `/menus` → 后端菜单驱动动态路由 → Layout 布局渲染。请求层/类型/存储经
`@forge/web-shared`（workspace 依赖）。

## 启动

```bash
# 后端（demo profile，默认 8080）：仓库根
SPRING_PROFILES_ACTIVE=demo ./mvnw -pl forge-distributions/forge-demo-app -am spring-boot:run
# 端口被占时后端换端口（如 --server.port=18080），并同步改 vite.config.ts 的 proxy target；
# 或后端仍走 8080、前端换 VITE_PORT=.env.local——两者只改其一即可对上

# 前端：forge-web/ 根
pnpm install && pnpm dev      # 或本包内 pnpm dev；默认 http://localhost:5173
```

登录账号：平台管理员 `admin / admin123`（初始口令——首次登录后立即在右上用户下拉改密；
改密成功会吊销该管理员全部 token 并跳登录页）。vite dev 为**代理模式**：
`/admin-api` → `http://localhost:8080`（无 CORS；生产同源反代）。

## 结构

```
src/
├── main.ts                  # 装配：pinia(+persist) / router / EP 全量+样式 / v-permission 指令
├── router/
│   ├── index.ts             # 静态路由：/login、/ → /dashboard、404 + 动态挂载位
│   ├── permission.ts        # 守卫：白名单 /login；无 token → login(带 redirect)；
│   │                        #   有 token 未加载 → loadMe+generateRoutes → addRoute → fullPath 重放；
│   │                        #   /me /menus 失败 → 清态跳登录
│   └── routerHelper.ts      # 后端菜单树 → 路由：import.meta.glob('../views/**') 映射 component 串；
│                            #   顶级 MENU 挂 Layout、DIR 挂 ParentView；末尾 404 兜底
├── store/
│   ├── user.ts              # token/用户信息（内存态，不进 localStorage）+ loadMe()
│   └── permission.ts        # menus + generateRoutes() + hasPermission()（PLATFORM 超级）
├── directives/permission.ts # v-permission="['tenant:manage']"：任一命中或含 PLATFORM 保留，否则移除节点
├── layouts/Layout.vue       # 侧边 el-menu 递归菜单 + 面包屑（route matched）+ 用户下拉（改密/登出）
├── components/              # MenuItem（递归）/ Breadcrumb / ChangePasswordDialog / ParentView
├── utils/                   # passwordPolicy（改密校验单源）/ icons（菜单图标名映射）
└── views/
    ├── login/index.vue      # 记住我只存账号（30 天，不存密码）
    ├── system/dashboard/    # 欢迎卡 + 统计占位 ×4
    ├── system/tenant/       # 租户 CRUD（表格/搜索/弹窗；操作按钮挂 v-permission=['tenant:manage']）
    ├── system/permission/   # 权限字典树 CRUD（树形表格；BUTTON/API 隐藏路由字段）
    └── error/404.vue
```

## 约定

- **component 串约定根 `views/`**：后端 seed 的 component（如 `system/tenant/index`）必须与
  `views/system/tenant/index.vue` 一一对应——`__tests__/viewsRegistry.spec.ts` 断言钉死
  （拼错路径=死页面，测试直接红）；
- **权限两处同源**：指令 `v-permission` 与菜单可见性同引 `PLATFORM` 超级常量
  （`@forge/web-shared` 导出），规则=authorities 任一命中或含 PLATFORM；admin 面
  仅 PLATFORM 账号，按钮门禁单测覆盖（`permission-directive.spec.ts`/`hasPermission.spec.ts`）；
- **菜单码即权限码**：页级权限直接用 MENU code（如租户页按钮用 `tenant:manage`）；
- **改密/登出闭环**：改密成功 → 清 token 整页跳 /login（后端已吊销全部 token）；
  登出 → logout API → 清态 → /login。

## 已知取舍（MVP）

- element-plus 全量引入（bundle ~888kB，页面经路由懒加载各自分包；按需引入 Phase 3+）；
- 无 TagsView/主题/i18n/验证码（spec 附录 B 舍弃清单）；
- 租户编辑 packageId 为裸 id 输入（无套餐下拉，Phase 3+ 候选）；
- TypeScript ~5.9 / 无 unocss（spec §8 预案处置，见 forge-web/README）。

## 测试

`pnpm test`（vitest，jsdom）：守卫分支/routerHelper/hasPermission/指令/改密校验/登录记住我/
viewsRegistry——35 用例；shared 层另 21（`packages/shared`）。
