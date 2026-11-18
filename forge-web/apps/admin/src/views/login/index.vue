<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { FormInstance, FormRules } from 'element-plus';
import {
  clearRememberedUsername,
  getRememberedUsername,
  login,
  setAccessToken,
  setRememberedUsername,
} from '@forge/web-shared';

/**
 * 登录页（admin 表单）：记住我只存账号不存密码（shared cache，30 天）；
 * 登录成功存 token → redirect 参数回放（守卫写入的站内路径）→ 缺省 /dashboard。
 */
const route = useRoute();
const router = useRouter();

const formRef = ref<FormInstance>();
const loading = ref(false);
const form = reactive({
  username: '',
  password: '',
  remember: false,
});

const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
};

onMounted(() => {
  const remembered = getRememberedUsername();
  if (remembered) {
    form.username = remembered;
    form.remember = true;
  }
});

async function handleSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  loading.value = true;
  try {
    const res = await login({ username: form.username, password: form.password });
    setAccessToken(res.token, res.expiresAt);
    if (form.remember) setRememberedUsername(form.username);
    else clearRememberedUsername();
    // redirect 回放：仅接受站内路径（'/' 开头且非 '//' 协议相对——防外链注入），缺省仪表盘
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '';
    const safeRedirect = redirect.startsWith('/') && !redirect.startsWith('//');
    await router.push(safeRedirect ? redirect : '/dashboard');
  } catch {
    /* 失败 toast 由 shared 拦截器统一弹（业务码失败不按码分支） */
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card">
      <template #header>
        <h3 class="login-title">Forge 管理台</h3>
      </template>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" placeholder="admin" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            autocomplete="current-password"
            @keyup.enter="handleSubmit"
          />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="form.remember">记住我（仅账号，30 天）</el-checkbox>
        </el-form-item>
        <el-button
          class="login-submit"
          type="primary"
          size="large"
          :loading="loading"
          @click="handleSubmit"
        >
          登 录
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--el-fill-color-lighter);
}

.login-card {
  width: 380px;
}

.login-title {
  margin: 0;
  text-align: center;
}

.login-submit {
  width: 100%;
}
</style>
