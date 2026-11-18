/** types 编译钉子：与后端 record 同构的形状在编译期锁定（运行时仅冒烟取值）。 */
import { describe, expect, it } from 'vitest';

import type {
  LoginRequest,
  LoginResponse,
  MeResponse,
  MenuNode,
  PermissionDTO,
  PermissionNode,
  Result,
  TenantDTO,
} from '../types';

describe('shared types 同构形状', () => {
  it('Result：code/message/data 三元', () => {
    const body: Result<MenuNode[]> = { code: 'OK', message: 'ok', data: [] };
    const fail: Result<null> = { code: 'IDENT-0401', message: '旧密码不正确', data: null };
    expect(body.code).toBe('OK');
    expect(fail.message).toBe('旧密码不正确');
  });

  it('登录域：LoginRequest/LoginResponse/MeResponse', () => {
    const req: LoginRequest = { username: 'admin', password: 'admin123' };
    const res: LoginResponse = {
      token: 'tk',
      expiresAt: '2026-11-19T00:00:00Z',
      userId: 'u1',
      tenantId: null,
    };
    const me: MeResponse = {
      userId: 'u1',
      tenantId: null,
      userType: 'ADMIN',
      authorities: ['PLATFORM'],
    };
    expect(req.username).toBe('admin');
    expect(res.tenantId).toBeNull();
    expect(me.authorities).toContain('PLATFORM');
  });

  it('MenuNode/PermissionNode：MenuNodeDTO 同构（含路由元数据六列）', () => {
    const node: MenuNode = {
      id: 'perm-node-dashboard',
      parentId: null,
      type: 'MENU',
      code: 'dashboard',
      name: '仪表盘',
      path: '/dashboard',
      component: 'system/dashboard/index',
      icon: 'Odometer',
      sort: 10,
      visible: true,
      children: [],
    };
    const alias: PermissionNode = node;
    expect(alias.type).toBe('MENU');
    expect(alias.component).toBe('system/dashboard/index');
  });

  it('TenantDTO/PermissionDTO：精简视图', () => {
    const tenant: TenantDTO = {
      id: 't1',
      code: 'acme',
      name: 'ACME',
      packageId: null,
      status: 'ENABLED',
      expiredAt: null,
    };
    const perm: PermissionDTO = { id: 'p1', code: 'tenant:manage', name: '租户管理', type: 'MENU' };
    expect(tenant.status).toBe('ENABLED');
    expect(perm.type).toBe('MENU');
  });
});
