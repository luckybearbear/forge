<script setup lang="ts">
import { computed } from 'vue';
import { useUserStore } from '@/store/user';

/**
 * 仪表盘（seed component 串 system/dashboard/index——页面路径与菜单字典一一对应）：
 * 欢迎卡（当前账号/租户占位——admin 端为平台 token，tenantId 恒 null，租户名以占位表达）
 * + 4 张统计占位卡（真实统计口径 Phase 3+ 接入，el-card 网格）。
 */
const userStore = useUserStore();

const account = computed(() => userStore.me?.userId ?? '-');
/** admin 端无租户上下文（平台视角）：租户名以占位表达，Phase 3+ 再接平台侧概览接口。 */
const tenantName = computed(() => '平台（全部租户）');

const stats = [
  { title: '租户总数', value: '—' },
  { title: '用户总数', value: '—' },
  { title: '今日登录', value: '—' },
  { title: '在线会话', value: '—' },
];
</script>

<template>
  <div class="dashboard">
    <el-card class="dashboard-welcome" shadow="never">
      <h3>欢迎回来，{{ account }}</h3>
      <p>当前租户：{{ tenantName }} · 统计口径接入前为占位展示</p>
    </el-card>
    <el-row :gutter="16" class="dashboard-stats">
      <el-col v-for="item in stats" :key="item.title" :span="6">
        <el-card shadow="hover">
          <template #header>{{ item.title }}</template>
          <div class="dashboard-stat-value">{{ item.value }}</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dashboard-welcome h3 {
  margin: 0 0 8px;
}

.dashboard-welcome p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.dashboard-stat-value {
  font-size: 24px;
  font-weight: 600;
  text-align: center;
}
</style>
