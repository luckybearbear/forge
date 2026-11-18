/**
 * 后端同构 DTO 类型（spec 2026-11-18 §5.2——MVP 手写，OpenAPI 生成 Phase 3+ 候选）。
 * Instant 在 JSON 里统一以 ISO-8601 字符串表示（`string`），可空字段对齐后端 record。
 */

/** 平台统一响应包装（platform-core Result）：`code` 为字符串码，`'OK'` 即成功；业务码失败仅 toast `message`，不按码分支（附录 C）。 */
export interface Result<T = unknown> {
  code: string;
  message: string;
  data: T;
}

/** admin 端登录请求（平台管理员，全局账号无租户维度）。 */
export interface LoginRequest {
  username: string;
  password: string;
}

/** 登录响应（双端同构）：token 为不透明凭据（Bearer）；admin 端 tenantId 为 null（平台 token）。 */
export interface LoginResponse {
  token: string;
  /** ISO-8601；作为 AccessToken 存储过期秒数的依据。 */
  expiresAt: string;
  userId: string;
  tenantId: string | null;
}

/** /me 响应：当前认证主体快照（token 快照语义）；admin 端 tenantId=null、authorities 含 PLATFORM 常量。 */
export interface MeResponse {
  userId: string;
  tenantId: string | null;
  userType: string;
  authorities: string[];
}

/** 自助改密请求：成功后后端吊销该管理员全部既有 token（含本请求所用）——前端需清态跳登录。 */
export interface ChangePasswordRequest {
  oldPassword: string;
  newPassword: string;
}

/** 权限点四值（菜单合一模型）：DIR 仅分组不参与鉴权；MENU 即菜单（code 即权限码）；BUTTON/API 纯权限码。 */
export type PermissionType = 'DIR' | 'MENU' | 'BUTTON' | 'API';

/** 菜单树节点（后端 MenuNodeDTO 同构）：visible 是前端展示开关（隐藏但路由可注册），后端不据此过滤。 */
export interface MenuNode {
  id: string;
  parentId: string | null;
  type: PermissionType;
  code: string;
  name: string;
  path: string | null;
  /** 组件串约定根 `views/`（routerHelper 映射），如 `system/tenant/index`。 */
  component: string | null;
  icon: string | null;
  sort: number | null;
  visible: boolean | null;
  children: MenuNode[] | null;
}

/** 租户快照（contract-tenant TenantDTO 同构）。 */
export interface TenantDTO {
  id: string;
  code: string;
  name: string;
  packageId: string | null;
  status: string;
  /** ISO-8601 或 null（不过期）。 */
  expiredAt: string | null;
}

/** 租户新建请求（POST /tenants）。 */
export interface CreateTenantRequest {
  code: string;
  name: string;
  packageId?: string;
  expiredAt?: string;
}

/** 租户更新请求（PUT /tenants/{id}，code 不可改）。 */
export interface UpdateTenantRequest {
  name?: string;
  packageId?: string;
  expiredAt?: string | null;
}

/** 权限字典条目（admin CRUD 返回的精简视图——service 层 create/update/get 的 data 形状）。 */
export interface PermissionDTO {
  id: string;
  code: string;
  name: string;
  type: PermissionType;
}

/** 权限点新建请求（POST /permissions）。 */
export interface CreatePermissionRequest {
  code: string;
  name: string;
  type: PermissionType;
  parentId?: string;
  path?: string;
  component?: string;
  icon?: string;
  sort?: number;
  visible?: boolean;
}

/** 权限点更新请求（PUT /permissions/{id}，code 不可改）。 */
export interface UpdatePermissionRequest {
  name?: string;
  type?: PermissionType;
  parentId?: string | null;
  path?: string | null;
  component?: string | null;
  icon?: string | null;
  sort?: number | null;
  visible?: boolean;
}

/** 字典树节点（GET /permissions 默认树形返回）——与 MenuNode 同构（Task 1 已合一）。 */
export type PermissionNode = MenuNode;
