/**
 * 权限 store：可见菜单树 + 动态路由（均只存内存，登出/装配失败清态）。
 * hasPermission 为函数式导出（v-permission 指令同源复用 checkPermission——单一判定源）。
 */
import { computed, ref } from 'vue';
import type { ComputedRef, Ref } from 'vue';
import { defineStore } from 'pinia';
import type { RouteRecordRaw } from 'vue-router';
import { getMenus } from '@forge/web-shared';
import type { MenuNode } from '@forge/web-shared';
import { PLATFORM_AUTHORITY } from '@forge/web-shared';

import { buildDynamicRoutes, VIEW_MODULES } from '@/router/routerHelper';
import type { ViewModules } from '@/router/routerHelper';
import { useUserStore } from '@/store/user';

/**
 * 显式 store 面声明：vue-router 5 的 RouteRecordRaw 泛型面过深，推断返回类型超出 TS 序列化
 * 上限（TS7056/TS4023——.pnpm 路径不可名）——以命名接口收口。
 */
interface PermissionStore {
  menus: Ref<MenuNode[]>;
  dynamicRoutes: Ref<RouteRecordRaw[]>;
  isGenerated: ComputedRef<boolean>;
  loadMenus(): Promise<void>;
  generateRoutes(viewModules?: ViewModules): void;
  reset(): void;
}

export const usePermissionStore = defineStore('permission', (): PermissionStore => {
  const menus = ref<MenuNode[]>([]);
  const dynamicRoutes = ref<RouteRecordRaw[]>([]);
  const isGenerated = computed(() => dynamicRoutes.value.length > 0);

  async function loadMenus(): Promise<void> {
    menus.value = await getMenus();
  }

  /** 菜单树 → 动态路由（含 404 兜底）；viewModules 参数化即测试注入口（glob 依赖注入）。 */
  function generateRoutes(viewModules: ViewModules = VIEW_MODULES): void {
    dynamicRoutes.value = buildDynamicRoutes(menus.value, viewModules);
  }

  function reset(): void {
    menus.value = [];
    dynamicRoutes.value = [];
  }

  return { menus, dynamicRoutes, isGenerated, loadMenus, generateRoutes, reset };
});

/**
 * 纯权限判定（无 store 依赖——指令/函数式/测试同源）：required 任一命中，或 authorities
 * 含 PLATFORM 超级码（同源后端 contract AuthorizationConstants.PLATFORM_AUTHORITY）即通过。
 */
export function checkPermission(authorities: string[], required: string | string[]): boolean {
  if (authorities.includes(PLATFORM_AUTHORITY)) return true;
  const need = Array.isArray(required) ? required : [required];
  return need.some((code) => authorities.includes(code));
}

/** 函数式权限判定：authorities 取 user store 内存态（token 快照语义——权限随会话固定）。 */
export function hasPermission(required: string | string[]): boolean {
  return checkPermission(useUserStore().authorities, required);
}
