/** cache 封装：token 读写删/TTL（expiresAt 驱动）/记住我 30d（只存账号）/清理语义。 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import WebStorageCache from 'web-storage-cache';

import {
  CACHE_KEYS,
  REMEMBER_TTL_SECONDS,
  TOKEN_FALLBACK_TTL_SECONDS,
  clearAllAuth,
  clearRememberedUsername,
  getAccessToken,
  getRememberedUsername,
  setAccessToken,
  setRememberedUsername,
} from '../cache';

const probe = new WebStorageCache({ storage: 'localStorage' });

beforeEach(() => {
  probe.clear();
});

afterEach(() => {
  vi.useRealTimers();
  probe.clear();
});

describe('AccessToken', () => {
  it('写读删', () => {
    expect(getAccessToken()).toBeNull();
    setAccessToken('tok-1');
    expect(getAccessToken()).toBe('tok-1');
    clearAllAuth();
    expect(getAccessToken()).toBeNull();
  });

  it('TTL：按后端 expiresAt 计算秒数，到期读为 null', () => {
    vi.useFakeTimers();
    setAccessToken('tok-60s', new Date(Date.now() + 60_000).toISOString());
    expect(getAccessToken()).toBe('tok-60s');

    vi.advanceTimersByTime(59_000);
    expect(getAccessToken()).toBe('tok-60s');

    vi.advanceTimersByTime(2_000); // 累计 61s，越界过期
    expect(getAccessToken()).toBeNull();
  });

  it('缺省 expiresAt：走兜底 TTL（12h）', () => {
    vi.useFakeTimers();
    setAccessToken('tok-fallback');
    vi.advanceTimersByTime((TOKEN_FALLBACK_TTL_SECONDS - 60) * 1000);
    expect(getAccessToken()).toBe('tok-fallback');
    vi.advanceTimersByTime(61_000);
    expect(getAccessToken()).toBeNull();
  });

  it('过期时间为过去时刻：钳为 1 秒（不抛错、即刻到期）', () => {
    vi.useFakeTimers();
    setAccessToken('tok-past', new Date(Date.now() - 5_000).toISOString());
    expect(getAccessToken()).toBe('tok-past'); // 1s 宽限
    vi.advanceTimersByTime(1_100);
    expect(getAccessToken()).toBeNull();
  });
});

describe('RememberUsername（记住我：只存账号不存密码，30 天）', () => {
  it('写入/读取/删除', () => {
    setRememberedUsername('admin');
    expect(getRememberedUsername()).toBe('admin');

    clearRememberedUsername();
    expect(getRememberedUsername()).toBeNull();
  });

  it('TTL≈30 天（29d 仍在、31d 已过期），且存储值只是账号串（无密码语义字段）', () => {
    vi.useFakeTimers();
    setRememberedUsername('admin');

    const raw = localStorage.getItem(CACHE_KEYS.REMEMBER_USERNAME) ?? '';
    expect(raw).toContain('admin');
    expect(Object.keys(JSON.parse(raw) as object)).not.toContain('password');

    vi.advanceTimersByTime((REMEMBER_TTL_SECONDS - 24 * 60 * 60) * 1000); // 29 天
    expect(getRememberedUsername()).toBe('admin');

    vi.advanceTimersByTime(2 * 24 * 60 * 60 * 1000); // 累计 31 天
    expect(getRememberedUsername()).toBeNull();
  });
});

describe('clearAllAuth 语义', () => {
  it('清 token、保留记住我（UX 偏好非凭证）', () => {
    setAccessToken('tok-x');
    setRememberedUsername('admin');

    clearAllAuth();

    expect(getAccessToken()).toBeNull();
    expect(getRememberedUsername()).toBe('admin');
  });
});
