/**
 * service 拦截器四分支（spec §5.2 / 附录 C）：
 * OK 吐 data / 业务码 reject+toast / 401 清 token 跳转 / blob 透传。
 * element-plus 以 vi.mock 解耦（shared 只引消息组件 JS，不引样式——测试无需环境）。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import MockAdapter from 'axios-mock-adapter';

vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
}));

import { ElMessage } from 'element-plus';

import { clearAllAuth, getAccessToken, setAccessToken } from '../cache';
import service from '../service';

const ok = <T>(data: T) => ({ code: 'OK', message: 'ok', data });

let mock: MockAdapter;

beforeEach(() => {
  mock = new MockAdapter(service);
  vi.mocked(ElMessage.error).mockClear();
  clearAllAuth();
});

afterEach(() => {
  mock.restore();
  vi.unstubAllGlobals();
  localStorage.clear();
});

describe('响应拦截：Result 字符串码语义', () => {
  it('OK 分支：直接吐 data（非 AxiosResponse）', async () => {
    mock.onGet('/tenants/t1').reply(200, ok({ id: 't1', code: 'acme' }));
    await expect(service.get('/tenants/t1')).resolves.toEqual({ id: 't1', code: 'acme' });
    expect(ElMessage.error).not.toHaveBeenCalled();
  });

  it('业务码失败：reject(Error(message)) + toast（不按码分支）', async () => {
    mock
      .onPut('/auth/password')
      .reply(200, { code: 'IDENT-0401', message: '旧密码不正确', data: null });
    await expect(service.put('/auth/password', { oldPassword: 'x' })).rejects.toThrow(
      '旧密码不正确',
    );
    expect(ElMessage.error).toHaveBeenCalledWith('旧密码不正确');
  });

  it('业务码失败且 message 空：toast 兜底"请求失败"', async () => {
    mock.onGet('/x').reply(200, { code: 'TENANT-0101', message: '', data: null });
    await expect(service.get('/x')).rejects.toThrow();
    expect(ElMessage.error).toHaveBeenCalledWith('请求失败');
  });
});

describe('响应拦截：HTTP 错误分支', () => {
  it('401：清 token + 跳 /login（无 refresh token——直跳，无队列回放）', async () => {
    setAccessToken('tok-401');
    expect(getAccessToken()).toBe('tok-401');
    // jsdom 的 Location 为 LegacyUnforgeable（不可 defineProperty）——vi.stubGlobal 以可写桩替换
    const location = { href: '' };
    vi.stubGlobal('location', location);
    mock.onGet('/auth/me').reply(401, { code: 'IDENT-0101', message: 'token 无效', data: null });

    await expect(service.get('/auth/me')).rejects.toMatchObject({ response: { status: 401 } });

    expect(getAccessToken()).toBeNull();
    expect(location.href).toBe('/login');
  });

  it('403：toast 没有操作权限', async () => {
    mock.onGet('/tenants/t1').reply(403, { code: 'AUTHZ-0101', message: 'forbidden', data: null });
    await expect(service.get('/tenants/t1')).rejects.toMatchObject({ response: { status: 403 } });
    expect(ElMessage.error).toHaveBeenCalledWith('没有操作权限');
  });

  it('其他错误（500/网络）：toast 后端 message 或 兜底网络异常', async () => {
    mock.onGet('/boom').reply(500, { code: 'SYS-9999', message: '服务内部错误', data: null });
    await expect(service.get('/boom')).rejects.toBeTruthy();
    expect(ElMessage.error).toHaveBeenCalledWith('服务内部错误');

    mock.onGet('/offline').networkError();
    await expect(service.get('/offline')).rejects.toBeTruthy();
    expect(ElMessage.error).toHaveBeenCalledWith('网络异常');
  });
});

describe('响应拦截：blob 透传', () => {
  it('responseType=blob：返回完整 AxiosResponse（data 为 Blob，不剥壳）', async () => {
    const blob = new Blob(['id,code\n1,acme'], { type: 'text/csv' });
    mock.onGet('/tenants/export').reply(200, blob, { 'content-type': 'text/csv' });

    const response = await service.get('/tenants/export', { responseType: 'blob' });

    expect(response).toHaveProperty('data');
    expect(response.data).toBeInstanceOf(Blob);
    expect(ElMessage.error).not.toHaveBeenCalled();
  });
});

describe('请求拦截：Bearer 白名单', () => {
  it('非白名单请求附 Authorization: Bearer <token>', async () => {
    setAccessToken('tok-abc');
    let seen: unknown = 'unset';
    mock.onGet('/auth/me').reply((config) => {
      seen = config.headers?.Authorization;
      return [200, ok(null)];
    });
    await service.get('/auth/me');
    expect(seen).toBe('Bearer tok-abc');
  });

  it('白名单 /auth/login：裸发不带 Authorization', async () => {
    setAccessToken('tok-abc');
    let seen: unknown = 'unset';
    mock.onPost('/auth/login').reply((config) => {
      seen = config.headers?.Authorization;
      return [200, ok({ token: 'next' })];
    });
    await service.post('/auth/login', { username: 'admin', password: 'x' });
    expect(seen).toBeUndefined();
  });

  it('无 token 时非白名单请求也裸发（由后端 401 兜底）', async () => {
    let seen: unknown = 'unset';
    mock.onGet('/auth/me').reply((config) => {
      seen = config.headers?.Authorization;
      return [401, { code: 'IDENT-0101', message: '未认证', data: null }];
    });
    await expect(service.get('/auth/me')).rejects.toBeTruthy();
    expect(seen).toBeUndefined();
  });
});
