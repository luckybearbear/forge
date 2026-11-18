/** 权限字典域（/permissions*——后端 PermissionAdminController；GET 默认树形返回 MenuNodeDTO 同构节点）。 */
import type {
  CreatePermissionRequest,
  PermissionDTO,
  PermissionNode,
  UpdatePermissionRequest,
} from '../types';
import { del, get, post, put } from '../index';

/** 字典树（全量含 BUTTON/API 与空 DIR——管理视角）。 */
export function getPermissionTree(): Promise<PermissionNode[]> {
  return get('/permissions');
}

export function getPermission(id: string): Promise<PermissionDTO> {
  return get(`/permissions/${id}`);
}

export function createPermission(data: CreatePermissionRequest): Promise<PermissionDTO> {
  return post('/permissions', data);
}

export function updatePermission(
  id: string,
  data: UpdatePermissionRequest,
): Promise<PermissionDTO> {
  return put(`/permissions/${id}`, data);
}

export function deletePermission(id: string): Promise<void> {
  return del(`/permissions/${id}`);
}
