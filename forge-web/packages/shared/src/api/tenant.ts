/** 租户域（/tenants*——后端 TenantAdminController；list/delete 为 Task 4 后端补齐端点）。 */
import type { CreateTenantRequest, TenantDTO, UpdateTenantRequest } from '../types';
import { del, get, post, put } from '../index';

export function createTenant(data: CreateTenantRequest): Promise<TenantDTO> {
  return post('/tenants', data);
}

/** 全量列表（code 升序，全局表无租户语义）：管理端表格数据源——量级=租户数，前端过滤即可。 */
export function listTenants(): Promise<TenantDTO[]> {
  return get('/tenants');
}

export function getTenant(id: string): Promise<TenantDTO> {
  return get(`/tenants/${id}`);
}

export function getTenantByCode(code: string): Promise<TenantDTO> {
  return get('/tenants', { code });
}

export function updateTenant(id: string, data: UpdateTenantRequest): Promise<TenantDTO> {
  return put(`/tenants/${id}`, data);
}

export function disableTenant(id: string): Promise<TenantDTO> {
  return post(`/tenants/${id}/disable`);
}

export function enableTenant(id: string): Promise<TenantDTO> {
  return post(`/tenants/${id}/enable`);
}

/** 软删除：不存在→TENANT-0404；删后编码可复用；删除亦触发租户下线（存量 token 全吊销）。 */
export function deleteTenant(id: string): Promise<void> {
  return del(`/tenants/${id}`);
}
