<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import type { FormInstance } from 'element-plus';
import { ElMessage } from 'element-plus';
import { changePassword, clearAllAuth } from '@forge/web-shared';

import { makePasswordRules } from '@/utils/passwordPolicy';

/**
 * 自助改密弹窗（Task 4 接线）：旧/新/确认三字段——全必填；新密码 ≥8 位且同时含字母与数字；
 * 确认与新密码一致（规则单源于 {@link makePasswordRules}，纯函数直测三分支）。提交
 * PUT /auth/password，成功后：
 * <b>后端已吊销该管理员全部既有 token（含本请求所用）</b>——清 token 后整页跳 /login 重登
 * （与登出同语义：动态路由等内存态随之重建，不依赖 401 兜底）。
 */
const visible = defineModel<boolean>({ default: false });

const formRef = ref<FormInstance>();
const submitting = ref(false);

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
});

const rules = makePasswordRules(() => form);

watch(visible, (open) => {
  if (open) {
    form.oldPassword = '';
    form.newPassword = '';
    form.confirmPassword = '';
    formRef.value?.clearValidate();
  }
});

async function handleSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  submitting.value = true;
  try {
    await changePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
    });
    visible.value = false;
    ElMessage.success('密码已修改，请重新登录');
    clearAllAuth(); // 后端已吊销全部旧 token——本地凭证同步清除（防残留在跳转间隙被复用）
    window.location.href = '/login'; // 整页跳：内存态（store/动态路由）随之重建
  } catch {
    /* 失败 toast 由 shared 拦截器统一弹（旧密码错误等业务码失败不按码分支） */
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="修改密码" width="480px" append-to-body>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="旧密码" prop="oldPassword">
        <el-input v-model="form.oldPassword" type="password" show-password placeholder="当前密码" />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input
          v-model="form.newPassword"
          type="password"
          show-password
          placeholder="至少 8 位，须含字母和数字"
        />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          show-password
          placeholder="再次输入新密码"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>
