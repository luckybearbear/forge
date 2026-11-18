import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { createRouter, createWebHistory } from 'vue-router';
import type { RouteLocationNormalized, Router } from 'vue-router';
import { defineComponent, h } from 'vue';

import { getAccessToken, getMe, getMenus, setAccessToken } from '@forge/web-shared';
import type { MeResponse, MenuNode } from '@forge/web-shared';

import { permissionGuard, setupPermissionGuard } from '@/router/permission';
import { usePermissionStore } from '@/store/permission';
import { useUserStore } from '@/store/user';

vi.mock('@forge/web-shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@forge/web-shared')>();
  return { ...actual, getMe: vi.fn(), getMenus: vi.fn() };
});

const meOf = (authorities: string[]): MeResponse => ({
  userId: 'u1',
  tenantId: null,
  userType: 'ADMIN',
  authorities,
});

const dashboardMenu: MenuNode = {
  id: 'perm-node-dashboard',
  parentId: null,
  type: 'MENU',
  code: 'dashboard',
  name: '仪表盘',
  path: '/dashboard',
  component: 'error/404', // 测试以已存在视图占位（映射逻辑单测在 routerHelper.spec）
  icon: null,
  sort: null,
  visible: true,
  children: null,
};

/**
 * 首轮真实形状：装配前任何未注册路径必命中具名 catch-all（name:'NotFound'+params.pathMatch）
 * ——Critical #1 的形状钉子（守卫重放不得回显 name/params）。已装配会话可覆写为解析后形状。
 */
const fakeTo = (
  path: string,
  fullPath = path,
  over: Partial<RouteLocationNormalized> = {},
): RouteLocationNormalized =>
  ({
    path,
    fullPath,
    name: 'NotFound',
    params: { pathMatch: path.slice(1).split('/') },
    query: {},
    hash: '',
    ...over,
  }) as unknown as RouteLocationNormalized;

const makeRouter = (): Router & { addRoute: ReturnType<typeof vi.fn> } =>
  ({ addRoute: vi.fn() }) as unknown as Router & { addRoute: ReturnType<typeof vi.fn> };

/** 集成测试桩组件（单组件复用两路由——身份无关，且规避 one-component-per-file 告警）。 */
const RouteStub = defineComponent({ name: 'RouteStub', render: () => h('div') });

describe('permissionGuard（路由守卫三分支）', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('无 token：白名单 /login 放行', async () => {
    expect(await permissionGuard(fakeTo('/login'), makeRouter())).toBe(true);
  });

  it('无 token：受保护路径携 redirect 跳登录', async () => {
    const result = await permissionGuard(fakeTo('/system/tenants'), makeRouter());
    expect(result).toEqual({ path: '/login', query: { redirect: '/system/tenants' } });
  });

  it('有 token 已加载：直接放行（不再请求 /me）', async () => {
    setAccessToken('t');
    useUserStore().loaded = true;
    expect(await permissionGuard(fakeTo('/dashboard'), makeRouter())).toBe(true);
    expect(getMe).not.toHaveBeenCalled();
  });

  it('有 token 未加载：装配 /me+/menus → addRoute 挂载 → 按 path 重建位置重放（不回显 name/params）', async () => {
    setAccessToken('t');
    vi.mocked(getMe).mockResolvedValue(meOf(['tenant:manage']));
    vi.mocked(getMenus).mockResolvedValue([dashboardMenu]);
    const router = makeRouter();
    const to = fakeTo('/dashboard'); // 首轮形状：name:'NotFound'+params.pathMatch

    const result = await permissionGuard(to, router);

    expect(getMe).toHaveBeenCalledTimes(1);
    expect(getMenus).toHaveBeenCalledTimes(1);
    expect(useUserStore().authorities).toEqual(['tenant:manage']);
    expect(usePermissionStore().menus).toEqual([dashboardMenu]);
    // addRoute 循环（装配 promise 内）：仪表盘包装层 + 404 兜底
    expect(router.addRoute).toHaveBeenCalledTimes(2);
    expect(router.addRoute).toHaveBeenLastCalledWith(
      expect.objectContaining({ path: '/:pathMatch(.*)*' }),
    );
    // 重放按 path 重建——若回显 name:'NotFound'，vue-router resolve 的 name 分支优先于 path，回落 404
    expect(result).toEqual({ path: '/dashboard', query: {}, hash: '', replace: true });
  });

  it('有 token 未加载：并发导航去重（一轮装配，addRoute 也只执行一次）', async () => {
    setAccessToken('t');
    let resolveMe!: (me: MeResponse) => void;
    vi.mocked(getMe).mockImplementation(
      () => new Promise<MeResponse>((resolve) => (resolveMe = resolve)),
    );
    vi.mocked(getMenus).mockResolvedValue([]);
    const router = makeRouter(); // 并发导航共享同一 router 实例（真实运行时即如此）
    const to = fakeTo('/dashboard');
    const first = permissionGuard(to, router);
    const second = permissionGuard(to, router);
    resolveMe(meOf([]));
    const [r1, r2] = await Promise.all([first, second]);

    expect(getMe).toHaveBeenCalledTimes(1);
    expect(router.addRoute).toHaveBeenCalledTimes(1); // 空菜单 → 仅 404 兜底一条，且只挂载一次
    expect(r1).toEqual({ path: '/dashboard', query: {}, hash: '', replace: true });
    expect(r2).toEqual({ path: '/dashboard', query: {}, hash: '', replace: true });
  });

  it('有 token 未加载：装配失败 → 清内存态+清 token → 跳登录（携 redirect）', async () => {
    setAccessToken('t');
    vi.mocked(getMe).mockRejectedValueOnce(new Error('网络异常'));
    const result = await permissionGuard(fakeTo('/dashboard'), makeRouter());

    expect(getAccessToken()).toBeNull(); // token 已清（防重试死循环）
    expect(useUserStore().loaded).toBe(false);
    expect(usePermissionStore().menus).toEqual([]);
    expect(result).toEqual({ path: '/login', query: { redirect: '/dashboard' } });
  });

  it('有 token 访问 /login：直去 /dashboard（防登录页自环）', async () => {
    setAccessToken('t');
    useUserStore().loaded = true;
    expect(await permissionGuard(fakeTo('/login'), makeRouter())).toEqual({ path: '/dashboard' });
  });

  it('真实 router 集成：深链（含 query）装配后命中动态路由而非 404（name 优先陷阱回归钉子）', async () => {
    // 装配前 '/dashboard' 必命中具名 catch-all（name:'NotFound'+params.pathMatch）——守卫重放
    // 若按 name 解析会回落 404、动态路由永不被使用；必须按 path 重解析且 query 保留。
    setAccessToken('t');
    vi.mocked(getMe).mockResolvedValue(meOf(['dashboard']));
    vi.mocked(getMenus).mockResolvedValue([
      { ...dashboardMenu, component: 'login/index' }, // 占位已存在视图（懒加载，不渲染）
    ]);

    const router = createRouter({
      history: createWebHistory(),
      routes: [
        { path: '/login', name: 'Login', component: RouteStub },
        { path: '/', redirect: '/dashboard' },
        { path: '/:pathMatch(.*)*', name: 'NotFound', component: RouteStub },
      ],
    });
    setupPermissionGuard(router);

    await router.push('/dashboard?tab=2');

    expect(router.currentRoute.value.name).toBe('dashboard'); // 动态叶子（name=菜单 code），非 NotFound
    expect(router.currentRoute.value.path).toBe('/dashboard');
    expect(router.currentRoute.value.query.tab).toBe('2'); // query 经重放保留
    window.history.replaceState({}, '', '/'); // 还原 jsdom URL
  });
});
