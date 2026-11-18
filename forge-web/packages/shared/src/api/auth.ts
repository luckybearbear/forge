/** 认证域（/auth/*——后端 AuthAdminController，双端前缀自动前插 /admin-api）。 */
import type {
  ChangePasswordRequest,
  LoginRequest,
  LoginResponse,
  MeResponse,
  MenuNode,
} from '../types';
import { get, post, put } from '../index';

export function login(data: LoginRequest): Promise<LoginResponse> {
  return post('/auth/login', data);
}

export function logout(): Promise<void> {
  return post('/auth/logout');
}

export function getMe(): Promise<MeResponse> {
  return get('/auth/me');
}

/** 自助改密：成功后旧 token 已被后端吊销——调用方需清态跳登录（Task 4 接线）。 */
export function changePassword(data: ChangePasswordRequest): Promise<void> {
  return put('/auth/password', data);
}

/**
 * 登录用户可见菜单树（token 快照语义：authorities ∩ MENU + 祖先 DIR 链）。
 * /auth/menus 在后端 admin/app **双面同路径**（AuthAdminController 与 AuthAppController 同实现
 * 镜像——review Important-1）：admin 端解析为 /admin-api/auth/menus（vite 代理转发此前缀）。
 */
export function getMenus(): Promise<MenuNode[]> {
  return get('/auth/menus');
}
