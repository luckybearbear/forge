<script setup lang="ts">
import { reactive, watch } from 'vue';

/**
 * 改密弹窗骨架（逻辑 Task 4 接线）：旧/新/确认三字段校验（新 ≥8 含字母数字）→
 * PUT /auth/password → 成功 toast + 清 token 跳 /login（旧 token 已被后端吊销）。
 */
const visible = defineModel<boolean>({ default: false });

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
});

watch(visible, (open) => {
  if (open) {
    form.oldPassword = '';
    form.newPassword = '';
    form.confirmPassword = '';
  }
});

function handleSubmit(): void {
  // Task 4 接线：校验 → changePassword(form) → 成功清态跳登录。
}
</script>

<template>
  <el-dialog v-model="visible" title="修改密码" width="480px" append-to-body>
    <el-form :model="form" label-width="90px">
      <el-form-item label="旧密码" prop="oldPassword">
        <el-input
          v-model="form.oldPassword"
          type="password"
          show-password
          placeholder="Task 4 接线"
        />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input
          v-model="form.newPassword"
          type="password"
          show-password
          placeholder="Task 4 接线"
        />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          show-password
          placeholder="Task 4 接线"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>
