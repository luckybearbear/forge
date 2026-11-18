<script setup lang="ts">
import { computed } from 'vue';
import type { MenuNode } from '@forge/web-shared';

import { resolveIcon } from '@/utils/icons';

/** 侧边菜单递归项：DIR → el-sub-menu（自引用递归）；MENU → el-menu-item（index=路由 path）。 */
const props = defineProps<{ node: MenuNode }>();

/** visible=false 的子项：路由仍注册但菜单隐藏（MenuNode.visible 前端展示开关语义）。 */
const visibleChildren = computed(() =>
  (props.node.children ?? []).filter((child) => child.visible !== false),
);
</script>

<template>
  <el-sub-menu
    v-if="node.type === 'DIR' && node.visible !== false && visibleChildren.length > 0"
    :index="node.path ?? `/${node.code}`"
  >
    <template #title>
      <el-icon v-if="node.icon"><component :is="resolveIcon(node.icon)" /></el-icon>
      <span>{{ node.name }}</span>
    </template>
    <MenuItem v-for="child in visibleChildren" :key="child.id" :node="child" />
  </el-sub-menu>
  <el-menu-item
    v-else-if="node.type === 'MENU' && node.visible !== false && node.path"
    :index="node.path"
  >
    <el-icon v-if="node.icon"><component :is="resolveIcon(node.icon)" /></el-icon>
    <template #title>{{ node.name }}</template>
  </el-menu-item>
</template>
