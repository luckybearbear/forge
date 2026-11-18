import { describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { PLATFORM_AUTHORITY } from '@forge/web-shared';

import { checkPermission, hasPermission } from '@/store/permission';
import { useUserStore } from '@/store/user';
import type { MeResponse } from '@forge/web-shared';

const meOf = (authorities: string[]): MeResponse => ({
  userId: 'u1',
  tenantId: null,
  userType: 'ADMIN',
  authorities,
});

describe('checkPermission / hasPermission', () => {
  it('命中：required 任一在 authorities 中即通过（数组/单串两形）', () => {
    expect(
      checkPermission(['tenant:manage', 'user:read'], ['tenant:create', 'tenant:manage']),
    ).toBe(true);
    expect(checkPermission(['tenant:manage'], 'tenant:manage')).toBe(true);
  });

  it('未命中：无交集不通过（含空 authorities）', () => {
    expect(checkPermission(['user:read'], ['tenant:create'])).toBe(false);
    expect(checkPermission(['user:read'], 'tenant:create')).toBe(false);
    expect(checkPermission([], 'tenant:create')).toBe(false);
  });

  it('PLATFORM 超级全通过（任意 required）', () => {
    expect(checkPermission([PLATFORM_AUTHORITY], 'tenant:create')).toBe(true);
    expect(checkPermission([PLATFORM_AUTHORITY, 'x'], ['unknown:code'])).toBe(true);
  });

  it('hasPermission 取 user store 内存态（会话未装配即无权限）', () => {
    setActivePinia(createPinia());
    const userStore = useUserStore();
    expect(hasPermission('tenant:create')).toBe(false); // 未登录/未装配
    userStore.me = meOf(['tenant:create']);
    expect(hasPermission(['tenant:create', 'x:y'])).toBe(true);
    expect(hasPermission(['x:y'])).toBe(false);
  });
});
