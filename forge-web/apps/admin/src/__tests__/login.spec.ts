import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import ElementPlus from 'element-plus';

import Login from '@/views/login/index.vue';
import {
  getAccessToken,
  getRememberedUsername,
  login,
  setRememberedUsername,
} from '@forge/web-shared';

// push 桩须单例：useRouter() 每次调用返回同一 mock（组件与断言侧共享）
const { pushMock } = vi.hoisted(() => ({ pushMock: vi.fn().mockResolvedValue(undefined) }));

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} as Record<string, string> }),
  useRouter: () => ({ push: pushMock }),
}));

vi.mock('@forge/web-shared', async (importOriginal) => {
  // cache 封装用真实实现（localStorage 在 jsdom 可用，getRememberedUsername 等经展开透传）
  // ——只 mock 网络 API：login
  const actual = await importOriginal<typeof import('@forge/web-shared')>();
  return { ...actual, login: vi.fn() };
});

const loginResponse = {
  token: 'tok-login',
  expiresAt: new Date(Date.now() + 60_000).toISOString(),
  userId: 'u-admin',
  tenantId: null,
};

/**
 * 登录页记住我（Task 4 补测——Task 3 无登录页 spec）：只存账号不存密码（shared cache 层
 * TTL/清空语义已由 cache.spec 覆盖，此处钉页面接线）；顺带钉成功流缺省跳 /dashboard。
 */
describe('登录页记住我（只存账号）', () => {
  let wrapper: ReturnType<typeof mount>;

  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    vi.mocked(login).mockReset();
  });

  afterEach(() => {
    wrapper?.unmount();
  });

  async function mountLogin(): Promise<void> {
    wrapper = mount(Login, { global: { plugins: [ElementPlus] }, attachTo: document.body });
    await nextTick();
  }

  async function fillAndSubmit(remember: boolean): Promise<void> {
    const inputs = wrapper.findAll('input');
    await inputs[0].setValue('admin');
    await inputs[1].setValue('Admin@123');
    const checkbox = wrapper.find('input[type="checkbox"]');
    if (remember !== (checkbox.element as HTMLInputElement).checked) {
      await checkbox.setValue(remember);
    }
    await wrapper.find('button.login-submit').trigger('click');
    await vi.waitFor(() => expect(login).toHaveBeenCalledTimes(1));
  }

  it('勾选记住我：登录成功只存账号（裸账号串，无密码字段），token 按 expiresAt 入存', async () => {
    vi.mocked(login).mockResolvedValue(loginResponse);
    await mountLogin();
    await fillAndSubmit(true);

    expect(login).toHaveBeenCalledWith({ username: 'admin', password: 'Admin@123' });
    expect(getRememberedUsername()).toBe('admin');
    // 只存账号：localStorage 原始值即账号串（web-storage-cache 包装仅附 TTL 元数据）——无密码
    const raw = localStorage.getItem('REMEMBER_USERNAME') ?? '';
    expect(raw).toContain('admin');
    expect(raw).not.toContain('Admin@123');
    expect(getAccessToken()).toBe('tok-login');
  });

  it('未勾选记住我：登录成功清除既有记住账号（改主意语义）', async () => {
    setRememberedUsername('legacy-user'); // 既有记住账号在场（真实 cache 实现）
    vi.mocked(login).mockResolvedValue(loginResponse);
    await mountLogin();
    await fillAndSubmit(false);

    expect(getRememberedUsername()).toBeNull();
  });

  it('无 redirect 参数：登录成功缺省跳 /dashboard', async () => {
    vi.mocked(login).mockResolvedValue(loginResponse);
    await mountLogin();
    await fillAndSubmit(true);

    expect(pushMock).toHaveBeenCalledWith('/dashboard');
  });
});
