/** 租户域（/tenants*——后端 TenantAdminController；当前无列表/删除端点，Task 4 需全量列表时再补后端）。 */
import type { CreateTenantRequest, TenantDTO, UpdateTenantRequest } from '../types';
import { get, post, put } from '../index';

export function createTenant(data: CreateTenantRequest): Promise<TenantDTO> {
  return post('/tenants', data);
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
