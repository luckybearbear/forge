/**
 * 全局路由守卫（spec §5.4，全码）：token 判定 → 会话装配（/me + /menus）→ 动态路由挂载重放。
 *
 * - token 唯一存于 shared cache（web-storage-cache 托管 TTL）；菜单/用户只存 pinia 内存态；
 * - 并发去重：模块级 promise——刷新后的导航风暴只发一轮 /me+/menus；addRoute 循环也在
 *   共享装配 promise 内（并发导航只挂载一次，不重复注册 matcher）；
 * - 装配失败（含 401）：shared 拦截器已 window.location 跳 /login（401 分支），守卫此处清
 *   token/内存双保险并防循环（目标已是 /login 时 return false 中止本轮导航）；
 * - 重放陷阱（Critical）：装配前的首轮导航，任何未注册路径必命中具名 catch-all
 *   （to.name='NotFound' + to.params.pathMatch），而 vue-router resolve 的 **name 分支优先于
 *   path 分支**——若展开 `to` 整体回传（{...to, replace:true}）会按 name 回落 404，动态注册
 *   的路由永不被使用（登录首跳/刷新深链/已登录访 /login 均触发）。故重放必须按路径重建：
 *   `{ path, query, hash, replace }`；按 path 解析时动态精确路由评分恒高于 catch-all，
 *   与注册顺序无关。
 */
import type { NavigationGuardReturn, RouteLocationNormalized, Router } from 'vue-router';
import { clearAllAuth, getAccessToken } from '@forge/web-shared';

import { usePermissionStore } from '@/store/permission';
import { useUserStore } from '@/store/user';

/** 免登录白名单（路径精确匹配）。 */
const WHITE_LIST = ['/login'];

/** 并发去重：进行中的装配 promise（成功后 loaded=true 短路、失败后置空允许重试）。 */
let bootstrapPromise: Promise<void> | null = null;

/** 会话装配（共享 promise 内一次完成）：/me 与 /menus 并发拉取 → 菜单树 → 动态路由 → addRoute 循环。 */
async function bootstrap(router: Router): Promise<void> {
  const userStore = useUserStore();
  const permissionStore = usePermissionStore();
  await Promise.all([userStore.loadMe(), permissionStore.loadMenus()]);
  permissionStore.generateRoutes();
  for (const route of permissionStore.dynamicRoutes) {
    router.addRoute(route);
  }
}

export async function permissionGuard(
  to: RouteLocationNormalized,
  router: Router,
): Promise<NavigationGuardReturn> {
  const token = getAccessToken();
  if (!token) {
    if (WHITE_LIST.includes(to.path)) return true;
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  if (WHITE_LIST.includes(to.path)) {
    return { path: '/dashboard' }; // 已登录再访 /login：直去首页（防登录页自环）
  }

  const userStore = useUserStore();
  const permissionStore = usePermissionStore();
  if (!userStore.loaded) {
    try {
      bootstrapPromise ??= bootstrap(router);
      await bootstrapPromise;
    } catch {
      // /me 或 /menus 失败：清态回登录。401 时 shared 拦截器已整页跳 /login（内存态随重载重置），
      // 此处清 token 与内存双保险；防循环：目标已是 /login 则中止本轮导航（不再发跳转指令）。
      userStore.reset();
      permissionStore.reset();
      clearAllAuth();
      if (to.path === '/login') return false;
      return { path: '/login', query: { redirect: to.fullPath } };
    } finally {
      bootstrapPromise = null;
    }
    // 按路径重放（不携带首轮的 name/params——name 优先陷阱见文件头）；query/hash 原样保留
    return { path: to.path, query: to.query, hash: to.hash, replace: true };
  }
  return true;
}

/** 注册守卫（main.ts 调用——router 实例经参数注入，避免与本文件循环 import）。 */
export function setupPermissionGuard(router: Router): void {
  router.beforeEach((to) => permissionGuard(to, router));
}
