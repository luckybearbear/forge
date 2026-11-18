import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { DOMWrapper } from '@vue/test-utils';
import { nextTick } from 'vue';
import ElementPlus, { ElMessage } from 'element-plus';

import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue';
import {
  confirmPasswordError,
  makePasswordRules,
  newPasswordError,
  type PasswordFormState,
} from '@/utils/passwordPolicy';
import { changePassword, clearAllAuth } from '@forge/web-shared';

vi.mock('@forge/web-shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@forge/web-shared')>();
  return { ...actual, changePassword: vi.fn(), clearAllAuth: vi.fn() };
});

/**
 * 改密弹窗（Task 4 接线）：
 * - 校验三分支直测组件同源的 makePasswordRules（EP 表单校验为异步渲染管线，jsdom 下
 *   错误节点显隐受 debounce 影响不稳——规则单源抽取后纯函数钉死语义）；
 * - 成功流走真实挂载：PUT /auth/password → 清 token → 整页跳 /login（后端已吊销全部旧 token）。
 *   弹窗 append-to-body——经 document 直查（DOMWrapper）。
 */
describe('改密校验三分支（makePasswordRules——组件同源规则）', () => {
  it('分支 1 必填：三字段空值各自报必填错', () => {
    const form: PasswordFormState = { oldPassword: '', newPassword: '', confirmPassword: '' };
    const rules = makePasswordRules(() => form);
    const messageOf = (prop: keyof PasswordFormState): string | undefined => {
      let message: string | undefined;
      const validator = (rules[prop] as { validator: unknown }[])[0].validator as (
        rule: unknown,
        value: string,
        callback: (error?: Error) => void,
      ) => void;
      validator({}, form[prop], (error?: Error) => (message = error?.message));
      return message;
    };
    expect(messageOf('oldPassword')).toBe('请输入旧密码');
    expect(messageOf('newPassword')).toBe('请输入新密码');
    expect(messageOf('confirmPassword')).toBe('请再次输入新密码');
  });

  it('分支 2 新密码策略：<8 位 / 无数字 / 无字母均拒，合规值通过', () => {
    expect(newPasswordError('ab1')).toBe('新密码至少 8 位');
    expect(newPasswordError('abcdefgh')).toBe('新密码须同时包含字母和数字'); // 无数字
    expect(newPasswordError('12345678')).toBe('新密码须同时包含字母和数字'); // 无字母
    expect(newPasswordError('')).toBe('请输入新密码'); // 必填折叠
    expect(newPasswordError('good1pass')).toBeNull();
  });

  it('分支 3 确认一致：不一致/空拒，与新密码一致通过（惰性取最新新密码）', () => {
    expect(confirmPasswordError('other1pass', 'valid1pass')).toBe('两次输入的密码不一致');
    expect(confirmPasswordError('', 'valid1pass')).toBe('请再次输入新密码');
    expect(confirmPasswordError('valid1pass', 'valid1pass')).toBeNull();
    // rules 侧经 getForm 惰性取值：表单值变化后确认校验对照最新新密码
    const form: PasswordFormState = {
      oldPassword: 'o',
      newPassword: 'first1pass',
      confirmPassword: 'second1pas',
    };
    const rules = makePasswordRules(() => form);
    const confirmValidator = (rules.confirmPassword as { validator: unknown }[])[0].validator as (
      rule: unknown,
      value: string,
      callback: (error?: Error) => void,
    ) => void;
    let message: string | undefined;
    confirmValidator({}, form.confirmPassword, (error?: Error) => (message = error?.message));
    expect(message).toBe('两次输入的密码不一致');
    form.newPassword = 'second1pas';
    confirmValidator({}, form.confirmPassword, (error?: Error) => (message = error?.message));
    expect(message).toBeUndefined();
  });
});

describe('ChangePasswordDialog 成功流（真实挂载）', () => {
  let wrapper: ReturnType<typeof mount>;

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(changePassword).mockReset();
    vi.mocked(clearAllAuth).mockReset();
  });

  afterEach(() => {
    wrapper?.unmount();
    document.body.innerHTML = '';
    vi.unstubAllGlobals();
  });

  it('changePassword(old,new) → clearAllAuth → 整页跳 /login + 成功 toast', async () => {
    vi.mocked(changePassword).mockResolvedValue(undefined);
    // jsdom Location 不可 defineProperty——沿用 shared service.spec 的 stubGlobal 桩
    const location = { href: '' };
    vi.stubGlobal('location', location);
    const successSpy = vi.spyOn(ElMessage, 'success');

    wrapper = mount(ChangePasswordDialog, {
      props: { modelValue: true },
      global: { plugins: [ElementPlus] },
      attachTo: document.body,
    });
    await nextTick();
    await nextTick();

    const inputs = Array.from(document.querySelectorAll<HTMLInputElement>('.el-dialog input')).map(
      (el) => new DOMWrapper(el),
    );
    expect(inputs).toHaveLength(3);
    await inputs[0].setValue('old-pass');
    await inputs[1].setValue('new1pass8');
    await inputs[2].setValue('new1pass8');

    const submitBtn = Array.from(document.querySelectorAll('.el-dialog__footer .el-button')).find(
      (b) => b.textContent?.includes('确定'),
    );
    await new DOMWrapper(submitBtn!).trigger('click');

    await vi.waitFor(() => expect(changePassword).toHaveBeenCalledTimes(1));
    expect(changePassword).toHaveBeenCalledWith({
      oldPassword: 'old-pass',
      newPassword: 'new1pass8',
    });
    // 后端已吊销该管理员全部 token（含本次）——本地凭证同步清除并整页跳登录
    expect(clearAllAuth).toHaveBeenCalledTimes(1);
    expect(location.href).toBe('/login');
    expect(successSpy).toHaveBeenCalledWith('密码已修改，请重新登录');
  });
});
