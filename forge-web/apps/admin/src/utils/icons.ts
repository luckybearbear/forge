import type { Component } from 'vue';
import * as ElementPlusIconsVue from '@element-plus/icons-vue';

const ICONS = ElementPlusIconsVue as unknown as Record<string, Component | undefined>;

/**
 * 菜单 icon 串（后端 seed 约定为 EP 图标组件名：'Setting'/'Odometer'/'OfficeBuilding'/'Key'…）
 * → 图标组件（动态 `<component :is>`）。MVP 全量命名空间映射（不做按需裁剪）；
 * 未知串返回 null（渲染层 v-if 兜底为不渲染图标）。
 */
export function resolveIcon(name: string): Component | null {
  return ICONS[name] ?? null;
}
