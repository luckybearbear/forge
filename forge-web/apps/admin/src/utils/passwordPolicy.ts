import type { FormRules } from 'element-plus';

/**
 * 改密表单校验规则（纯函数抽取——vitest 直测三分支，不经 EP 异步渲染管线）：
 * 全必填 + 新密码 ≥8 位且同时含字母与数字 + 确认一致。
 *
 * required 分支折叠进自定义 validator（async-validator 的 required 语义与
 * 空串/null 判定在此统一——错误消息单一来源）。
 */
export interface PasswordFormState {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
}

/** 新密码策略：非空 → ≥8 位 → 含字母且含数字。返回 null 表示通过。 */
export function newPasswordError(value: string): string | null {
  if (!value) return '请输入新密码';
  if (value.length < 8) return '新密码至少 8 位';
  if (!/[A-Za-z]/.test(value) || !/\d/.test(value)) return '新密码须同时包含字母和数字';
  return null;
}

/** 确认密码：非空 → 与新密码一致。返回 null 表示通过。 */
export function confirmPasswordError(value: string, newPassword: string): string | null {
  if (!value) return '请再次输入新密码';
  if (value !== newPassword) return '两次输入的密码不一致';
  return null;
}

type ValidatorCallback = (error?: Error) => void;

/** el-form rules（getForm 惰性取值——确认校验实时对照最新新密码）。 */
export function makePasswordRules(getForm: () => PasswordFormState): FormRules {
  return {
    oldPassword: [
      {
        validator: (_rule: unknown, value: string, callback: ValidatorCallback) =>
          callback(value ? undefined : new Error('请输入旧密码')),
        trigger: 'blur',
      },
    ],
    newPassword: [
      {
        validator: (_rule: unknown, value: string, callback: ValidatorCallback) => {
          const error = newPasswordError(value);
          callback(error ? new Error(error) : undefined);
        },
        trigger: 'blur',
      },
    ],
    confirmPassword: [
      {
        validator: (_rule: unknown, value: string, callback: ValidatorCallback) => {
          const error = confirmPasswordError(value, getForm().newPassword);
          callback(error ? new Error(error) : undefined);
        },
        trigger: 'blur',
      },
    ],
  };
}
