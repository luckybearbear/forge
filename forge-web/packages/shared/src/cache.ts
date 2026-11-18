/**
 * web-storage-cache 封装（借鉴 yudao 存储模式）：key 常量集中、TTL 由库托管。
 * 菜单/用户信息只存 pinia 内存态不进 localStorage（spec §5.4）——本文件只管
 * AccessToken 与记住我（记住我只存账号不存密码，30 天）。
 *
 * 库自带类型（src/web-storage-cache.d.ts，export = 类面）：实现位以本地窄接口 KVStorage
 * 收口（get 返回 any、无 ttl 查询——TTL 语义以"到期读为 null"表达），不出现在对外 d.ts 面。
 */
import WebStorageCache from 'web-storage-cache';

/** 存储 key 集中管理——新增 key 必须在此登记。 */
export const CACHE_KEYS = {
  ACCESS_TOKEN: 'ACCESS_TOKEN',
  REMEMBER_USERNAME: 'REMEMBER_USERNAME',
} as const;

/** 记住我有效期（秒）：30 天。 */
export const REMEMBER_TTL_SECONDS = 30 * 24 * 60 * 60;

/** token 缺省有效期（秒）：登录响应正常携带 expiresAt，此值仅为缺失兜底。 */
export const TOKEN_FALLBACK_TTL_SECONDS = 12 * 60 * 60;

interface KVStorage {
  set(key: string, value: unknown, options?: { exp?: number }): void;
  get<T = unknown>(key: string): T | null;
  delete(key: string): void;
  clear(): void;
}

const storage: KVStorage = new WebStorageCache({ storage: 'localStorage' });

export function getAccessToken(): string | null {
  return storage.get<string>(CACHE_KEYS.ACCESS_TOKEN) ?? null;
}

/** token 过期秒数按后端 expiresAt 计算（到期由库自动删除，读侧拿到 null 即过期）。 */
export function setAccessToken(token: string, expiresAt?: string | null): void {
  const ttl = expiresAt
    ? Math.max(1, Math.floor((Date.parse(expiresAt) - Date.now()) / 1000))
    : TOKEN_FALLBACK_TTL_SECONDS;
  storage.set(CACHE_KEYS.ACCESS_TOKEN, token, { exp: ttl });
}

/** 记住我：只存账号不存密码（30 天，登录页预填用）。 */
export function setRememberedUsername(username: string): void {
  storage.set(CACHE_KEYS.REMEMBER_USERNAME, username, { exp: REMEMBER_TTL_SECONDS });
}

export function getRememberedUsername(): string | null {
  return storage.get<string>(CACHE_KEYS.REMEMBER_USERNAME) ?? null;
}

export function clearRememberedUsername(): void {
  storage.delete(CACHE_KEYS.REMEMBER_USERNAME);
}

/**
 * 会话凭证清理（401 拦截 / 登出调用）：只清 token；记住我属 UX 偏好非凭证，保留。
 */
export function clearAllAuth(): void {
  storage.delete(CACHE_KEYS.ACCESS_TOKEN);
}
