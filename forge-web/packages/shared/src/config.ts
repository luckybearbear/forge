/**
 * 请求层配置单源（spec §5.2）。
 * `import.meta.env` 在 app（vite）下由 VITE_ 前缀注入；在 vitest/node 下不存在——
 * 此处以可选读取解耦，未注入时回落默认值（测试与本地脚本不依赖 vite 上下文）。
 */
function resolveBaseUrl(): string {
  const env = (import.meta as ImportMeta & { env?: Record<string, string | undefined> }).env;
  return env?.VITE_API_URL ?? '/admin-api';
}

/** 后端网关前缀（dev 经 vite proxy → localhost:8080，生产同源反代——代理模式，不做 CORS）。 */
export const BASE_URL: string = resolveBaseUrl();

/** 请求超时（毫秒）。 */
export const TIMEOUT = 30_000;

/** 免 Bearer 白名单：仅登录（子串匹配——与计划拦截器骨架一致）。 */
export const WHITE_LIST = ['/auth/login'];
