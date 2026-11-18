/**
 * 动态路由装配（spec §5.4，全码）：后端菜单树 → RouteRecordRaw[]。
 *
 * 约定：
 * - component 串根 `views/`（如 `system/tenant/index`），经 import.meta.glob 映射为懒加载
 *   模块（`.vue` 优先于 `.tsx`）；视图未创建的 MENU 跳过注册（Task 4 页面就位前的窗口期防崩）；
 * - 顶级 MENU/DIR 以 Layout 承载（外层不携带 title——面包屑由叶子/DIR 唯一发声，防重复）；
 *   嵌套 DIR 用 ParentView（极简 &lt;router-view/&gt; 直通层）；子 MENU path 为绝对串（seed 约定）；
 * - DIR 无 path（seed 约定路由字段可不填）：以 `/{code}` 兜底作分组占位段（不参与导航）；
 * - 末位恒为 404 兜底（catch-all 须最后注册；静态 404 先行 + 守卫重放共同保证解析正确）。
 */
import type { RouteComponent, RouteRecordRaw } from 'vue-router';
import type { MenuNode } from '@forge/web-shared';

import Layout from '@/layouts/Layout.vue';
import ParentView from '@/components/ParentView.vue';

/** 视图模块加载器（import.meta.glob 懒加载面——值形如 `() => import('...')`）。 */
export type ViewModule = () => Promise<RouteComponent | { default: RouteComponent }>;
export type ViewModules = Record<string, ViewModule>;

/** views 目录懒加载映射（glob 依赖参数化注入——buildDynamicRoutes 的 modules 形参即测试注入口）。 */
export const VIEW_MODULES = import.meta.glob('../views/**/*.{vue,tsx}') as unknown as ViewModules;

/** 404 兜底（buildDynamicRoutes 末位 + router/index.ts 静态双保险）。 */
export const NOT_FOUND_ROUTE: RouteRecordRaw = {
  path: '/:pathMatch(.*)*',
  name: 'NotFound',
  component: () => import('@/views/error/404.vue'),
  meta: { title: '404' },
};

/**
 * component 串 → glob 模块加载器：`system/tenant/index` → `../views/system/tenant/index.vue`
 * （`.vue` 优先于 `.tsx`——同名共存时 Vue SFC 为主）；未命中返回 null。
 */
export function resolveViewModule(
  component: string | null,
  modules: ViewModules,
): ViewModule | null {
  if (!component) return null;
  for (const ext of ['.vue', '.tsx']) {
    const loader = modules[`../views/${component}${ext}`];
    if (loader) return loader;
  }
  return null;
}

/** 菜单树 → 动态路由（含末位 404 兜底）。 */
export function buildDynamicRoutes(
  menus: MenuNode[],
  modules: ViewModules = VIEW_MODULES,
): RouteRecordRaw[] {
  const routes: RouteRecordRaw[] = [];
  for (const node of menus) {
    const record = toRouteRecord(node, modules, true);
    if (record) routes.push(record);
  }
  routes.push(NOT_FOUND_ROUTE);
  return routes;
}

/** 单节点递归：顶级（isTopLevel）以 Layout 承载；BUTTON/API 不进路由。 */
function toRouteRecord(
  node: MenuNode,
  modules: ViewModules,
  isTopLevel: boolean,
): RouteRecordRaw | null {
  const meta = { title: node.name, icon: node.icon ?? undefined, code: node.code };

  if (node.type === 'MENU') {
    const view = resolveViewModule(node.component, modules);
    if (!node.path || !view) {
      // 路由元数据不全或视图模块未建：跳过该节点（不阻断兄弟节点注册）
      console.warn(
        `[router] 菜单「${node.name}(${node.code})」缺少 path/component 或视图模块未创建，已跳过路由注册`,
      );
      return null;
    }
    if (!isTopLevel) {
      return { path: node.path, name: node.code, component: view, meta };
    }
    // 顶级 MENU：Layout 包装（外层不带 name/title——叶子唯一发声，避免面包屑重复与重名告警）
    return {
      path: node.path,
      component: Layout,
      children: [{ path: '', name: node.code, component: view, meta }],
    };
  }

  if (node.type === 'DIR') {
    const children = (node.children ?? [])
      .map((child) => toRouteRecord(child, modules, false))
      .filter((record): record is RouteRecordRaw => record !== null);
    if (children.length === 0) return null; // 空 DIR 折叠（后端已折，双保险）
    return {
      path: node.path ?? `/${node.code}`,
      name: node.code,
      component: isTopLevel ? Layout : ParentView,
      meta,
      children,
    };
  }

  return null; // BUTTON/API 不进路由
}
