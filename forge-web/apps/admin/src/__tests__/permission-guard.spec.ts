import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import type { RouteLocationNormalized, Router } from 'vue-router';

import { getAccessToken, getMe, getMenus, setAccessToken } from '@forge/web-shared';
import type { MeResponse, MenuNode } from '@forge/web-shared';

import { permissionGuard } from '@/router/permission';
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

const fakeTo = (path: string, fullPath = path): RouteLocationNormalized =>
  ({ path, fullPath }) as unknown as RouteLocationNormalized;

const makeRouter = (): Router & { addRoute: ReturnType<typeof vi.fn> } =>
  ({ addRoute: vi.fn() }) as unknown as Router & { addRoute: ReturnType<typeof vi.fn> };

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

  it('有 token 未加载：装配 /me+/menus → addRoute 挂载 → {...to, replace:true} 重放', async () => {
    setAccessToken('t');
    vi.mocked(getMe).mockResolvedValue(meOf(['tenant:manage']));
    vi.mocked(getMenus).mockResolvedValue([dashboardMenu]);
    const router = makeRouter();
    const to = fakeTo('/dashboard');

    const result = await permissionGuard(to, router);

    expect(getMe).toHaveBeenCalledTimes(1);
    expect(getMenus).toHaveBeenCalledTimes(1);
    expect(useUserStore().authorities).toEqual(['tenant:manage']);
    expect(usePermissionStore().menus).toEqual([dashboardMenu]);
    // addRoute 循环：仪表盘包装层 + 404 兜底
    expect(router.addRoute).toHaveBeenCalledTimes(2);
    expect(router.addRoute).toHaveBeenLastCalledWith(
      expect.objectContaining({ path: '/:pathMatch(.*)*' }),
    );
    expect(result).toEqual({ ...to, replace: true });
  });

  it('有 token 未加载：并发导航去重（模块级 promise 只发一轮装配）', async () => {
    setAccessToken('t');
    let resolveMe!: (me: MeResponse) => void;
    vi.mocked(getMe).mockImplementation(
      () => new Promise<MeResponse>((resolve) => (resolveMe = resolve)),
    );
    vi.mocked(getMenus).mockResolvedValue([]);
    const to = fakeTo('/dashboard');
    const first = permissionGuard(to, makeRouter());
    const second = permissionGuard(to, makeRouter());
    resolveMe(meOf([]));
    const [r1, r2] = await Promise.all([first, second]);

    expect(getMe).toHaveBeenCalledTimes(1);
    expect(r1).toEqual({ ...to, replace: true });
    expect(r2).toEqual({ ...to, replace: true });
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
});
