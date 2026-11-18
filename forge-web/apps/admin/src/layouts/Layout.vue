<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessageBox } from 'element-plus';
import { UserFilled } from '@element-plus/icons-vue';
import { clearAllAuth, logout } from '@forge/web-shared';

import Breadcrumb from '@/components/Breadcrumb.vue';
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue';
import MenuItem from '@/components/MenuItem.vue';
import { usePermissionStore } from '@/store/permission';
import { useUserStore } from '@/store/user';

/**
 * 管理台主布局（简版：无 TagsView/主题）：侧边菜单（后端菜单树递归）+ 面包屑 + 用户下拉
 * （改密[Task 4 接线]/登出——登出逻辑本任务接通）。
 */
const route = useRoute();
const permissionStore = usePermissionStore();
const userStore = useUserStore();

const menus = computed(() => permissionStore.menus);
const activeMenu = computed(() => route.path);
const account = computed(() => userStore.me?.userId ?? '-');
const changePasswordVisible = ref(false);

function onCommand(command: string | number | object): void {
  if (command === 'password') changePasswordVisible.value = true;
  else if (command === 'logout') void handleLogout();
}

async function handleLogout(): Promise<void> {
  try {
    await ElMessageBox.confirm('确定退出登录？', '提示', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消',
    });
  } catch {
    return; // 用户取消
  }
  try {
    await logout(); // 尽力而为：失败（网络/token 已失效）也继续清态——拦截器已 toast
  } catch {
    /* noop */
  }
  userStore.reset();
  permissionStore.reset();
  clearAllAuth();
  // 动态路由注册表存活于 router 实例内存（含无名包装层，无法逐名卸载）——整页回 /login
  // 即重建全部内存态（与 401 拦截跳转语义一致）；新会话装配时 addRoute 同名记录自动替换。
  window.location.href = '/login';
}
</script>

<template>
  <el-container class="layout">
    <el-aside class="layout-aside" width="220px">
      <div class="layout-logo">Forge 管理台</div>
      <el-scrollbar>
        <el-menu
          class="layout-menu"
          :default-active="activeMenu"
          router
          background-color="#001529"
          text-color="#bfcbd9"
          active-text-color="#409eff"
        >
          <MenuItem v-for="node in menus" :key="node.id" :node="node" />
        </el-menu>
      </el-scrollbar>
    </el-aside>
    <el-container>
      <el-header class="layout-header">
        <Breadcrumb />
        <el-dropdown @command="onCommand">
          <span class="layout-account">
            <el-icon><UserFilled /></el-icon>
            {{ account }}
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="password">修改密码</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="layout-main">
        <RouterView />
      </el-main>
    </el-container>
    <ChangePasswordDialog v-model="changePasswordVisible" />
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
}

.layout-aside {
  background-color: #001529;
  display: flex;
  flex-direction: column;
}

.layout-logo {
  height: 56px;
  line-height: 56px;
  text-align: center;
  color: #fff;
  font-weight: 600;
  letter-spacing: 1px;
}

.layout-menu {
  border-right: none;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--el-border-color-light);
}

.layout-account {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: var(--el-text-color-regular);
}

.layout-main {
  background-color: var(--el-fill-color-lighter);
}
</style>
