<script setup lang="ts">
import { ref } from 'vue';

import { BASE_URL, TIMEOUT } from '@forge/web-shared';

// 冒烟页：vue + element-plus + router + workspace 包（@forge/web-shared）装配实证。
const count = ref(0);
</script>

<template>
  <el-container class="smoke">
    <el-header>
      <h2>Forge 管理台 · 工程骨架冒烟页</h2>
    </el-header>
    <el-main>
      <el-space direction="vertical">
        <el-tag type="success">vite + vue + ts + element-plus + pinia + vue-router</el-tag>
        <el-tag>API {{ BASE_URL }} · timeout {{ TIMEOUT / 1000 }}s</el-tag>
        <el-button type="primary" @click="count++">count = {{ count }}</el-button>
      </el-space>
    </el-main>
  </el-container>
</template>

<style scoped>
.smoke {
  min-height: 100vh;
}
</style>
