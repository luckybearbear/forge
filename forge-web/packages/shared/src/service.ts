/**
 * 请求层核心（spec §5.2，全码）：单例 axios 实例 + 拦截器。
 *
 * 关键差异（相对 yudao，README 同步记录）：
 * - 无 refresh token：HTTP 401 → 清 token 直跳 /login，不做失败队列回放；
 * - Result 字符串码语义：`code === 'OK'` 拦截器直接吐 `data`（包装层泛型即 data 类型），
 *   业务码失败仅 toast `message` 不按码分支（分支语义由 HTTP 401/403 承担——附录 C）；
 * - Bearer 白名单仅 /auth/login（无租户头体系）。
 */
import axios, {
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios';
import { ElMessage } from 'element-plus';

import { getAccessToken, clearAllAuth } from './cache';
import { BASE_URL, TIMEOUT, WHITE_LIST } from './config';
import type { Result } from './types';

const service: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: TIMEOUT,
});

// 请求拦截：非白名单附加 Bearer token（token 缺失时裸发——由后端 401 兜底）。
service.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  if (!WHITE_LIST.some((p) => config.url?.includes(p))) {
    const token = getAccessToken();
    if (token) config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

service.interceptors.response.use(
  (response: AxiosResponse) => {
    if (response.config.responseType === 'blob') return response; // 透传：download 需完整响应（headers/文件名）
    const body = response.data as Result<unknown>;
    if (body.code === 'OK') return body.data as never; // 直接吐 data
    ElMessage.error(body.message || '请求失败'); // 业务码失败：toast 不按码分支
    return Promise.reject(new Error(body.message));
  },
  (error) => {
    const status = error.response?.status;
    if (status === 401) {
      clearAllAuth(); // 无 refresh token：清凭证直跳（spec 关键差异）
      window.location.href = '/login';
    } else if (status === 403) {
      ElMessage.error('没有操作权限');
    } else {
      ElMessage.error(error.response?.data?.message || '网络异常');
    }
    return Promise.reject(error);
  },
);

export default service;
