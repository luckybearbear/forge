/**
 * 泛型包装（直接返回 data——拦截器已剥 Result 壳）+ upload/download。
 * axios 1.21 的 AxiosResponseResult 对裸泛型 R 无法立即求值——此处以默认分支
 * （Promise<AxiosResponse<any>>）+ 单次断言收口为 Promise<T>（运行时即 data）；
 * blob 分支拦截器返回完整 AxiosResponse，download 保留之（取 headers/文件名）。
 */
import type { AxiosResponse } from 'axios';

import service from './service';

export function get<T>(url: string, params?: object): Promise<T> {
  return service.get(url, { params }) as Promise<T>;
}

export function post<T>(url: string, data?: object): Promise<T> {
  return service.post(url, data) as Promise<T>;
}

export function put<T>(url: string, data?: object): Promise<T> {
  return service.put(url, data) as Promise<T>;
}

export function del<T>(url: string): Promise<T> {
  return service.delete(url) as Promise<T>;
}

/** 下载（占位）：blob 透传分支——返回完整响应，调用方自行从 data 取 Blob、headers 取文件名。 */
export function download(url: string, params?: object): Promise<AxiosResponse<Blob>> {
  return service.get<Blob, AxiosResponse<Blob>>(url, { params, responseType: 'blob' });
}

/** 上传（占位）：FormData 交 axios 自动设 multipart 边界（勿手动设 Content-Type）。 */
export function upload<T>(url: string, data: FormData): Promise<T> {
  return service.post(url, data) as Promise<T>;
}

export * from './config';
export * from './cache';
export * from './types';
export * from './api/auth';
export * from './api/tenant';
export * from './api/permission';
