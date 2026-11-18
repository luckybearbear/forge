import { createRouter, createWebHistory } from 'vue-router';
import type { RouteRecordRaw } from 'vue-router';

/**
 * 静态路由：/login、/ 重定向 /dashboard、404 兜底。动态路由（后端菜单驱动）由守卫在
 * /me+/menus 就绪后 addRoute（见 routerHelper.ts / permission.ts）。注意：装配前的首轮
 * 导航必命中具名 catch-all（name:'NotFound'）——守卫按 path 重建位置重放（不可展开 to，
 * vue-router resolve 的 name 分支优先于 path）；按路径解析时动态精确路由评分恒高于
 * catch-all，与注册顺序无关。
 */
export const staticRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/',
    redirect: '/dashboard',
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404' },
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes: staticRoutes,
});

export default router;
