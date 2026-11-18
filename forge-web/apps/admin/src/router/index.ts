import { createRouter, createWebHistory } from 'vue-router';

// Task 2 仅静态冒烟路由；/login、动态挂载位与 404 兜底在 Task 3 铺全。
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'smoke',
      component: () => import('@/views/smoke.vue'),
    },
  ],
});

export default router;
