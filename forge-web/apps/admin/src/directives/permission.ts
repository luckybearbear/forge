/**
 * v-permission="['tenant:create']"（spec §5.4，全码）：authorities 任一命中或含 PLATFORM
 * 超级则保留元素，否则 `el.parentNode.removeChild(el)`（DOM 移除式）。
 *
 * - authorities 来自 user store 内存态（token 快照语义）；
 * - mounted + updated 双钩子：v-permission 绑定值变化时重评；
 * - 移除式语义的固有限制：一旦移除无法随权限变化恢复插入——权限快照随会话固定、
 *   登出即整页回登录，该限制不构成实际场景；
 * - 空值/空数组不处理（防误杀未配置权限的调用点）。
 */
import type { Directive, DirectiveBinding } from 'vue';

import { checkPermission } from '@/store/permission';
import { useUserStore } from '@/store/user';

function evaluate(el: HTMLElement, binding: DirectiveBinding<string | string[]>): void {
  const required = binding.value;
  if (!required || (Array.isArray(required) && required.length === 0)) return;
  if (!checkPermission(useUserStore().authorities, required)) {
    el.parentNode?.removeChild(el);
  }
}

export const permissionDirective: Directive<HTMLElement, string | string[]> = {
  mounted: evaluate,
  updated: evaluate,
};
