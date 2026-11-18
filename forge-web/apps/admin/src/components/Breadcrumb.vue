<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();

/**
 * 面包屑：route.matched 的 meta.title 链（routerHelper 外层 Layout 包装不带 title——
 * DIR 与叶子唯一发声；末项为当前页，不可点击）。
 */
const items = computed(() =>
  route.matched
    .filter((record) => typeof record.meta.title === 'string' && record.meta.title !== '')
    .map((record) => ({ key: record.path, title: record.meta.title as string })),
);
</script>

<template>
  <el-breadcrumb separator="/">
    <el-breadcrumb-item v-for="item in items" :key="item.key">
      {{ item.title }}
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>
