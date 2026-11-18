/**
 * 前端自建稳定常量（与后端 contract 对应处注释同源——值是契约级稳定串，不做运行时共享）。
 */

/**
 * 平台超级权限码：同源自 forge-contract-authorization
 * `AuthorizationConstants.PLATFORM_AUTHORITY`（值 "PLATFORM"，契约级稳定串）。
 * 语义：authorities 含此码即超级全通过——菜单可见性（后端 visibleMenus）与
 * 前端按钮权限（v-permission / hasPermission）两处统一引用本常量。
 */
export const PLATFORM_AUTHORITY = 'PLATFORM';
