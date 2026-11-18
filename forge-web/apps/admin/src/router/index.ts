import { createRouter, createWebHistory } from 'vue-router';
import type { RouteRecordRaw } from 'vue-router';

/**
 * 静态路由：/login、/ 重定向 /dashboard、404 兜底。动态路由（后端菜单驱动）由守卫在
 * /me+/menus 就绪后 addRoute（见 routerHelper.ts / permission.ts）；静态 404 与守卫重放
 * 共同保证刷新深链时先装配后解析（动态精确路由评分恒高于 catch-all，不受注册顺序影响）。
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
