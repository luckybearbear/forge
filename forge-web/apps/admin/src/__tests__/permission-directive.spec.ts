import { describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { defineComponent, h, nextTick, ref } from 'vue';
import type { Ref } from 'vue';
import { mount } from '@vue/test-utils';
import { withDirectives } from 'vue';

import { permissionDirective } from '@/directives/permission';
import { useUserStore } from '@/store/user';
import type { MeResponse } from '@forge/web-shared';

const meOf = (authorities: string[]): MeResponse => ({
  userId: 'u1',
  tenantId: null,
  userType: 'ADMIN',
  authorities,
});

/** 宿主组件：绑定值取响应式 ref（值变化触发重渲染 → updated 钩子重评）。 */
const makeHost = (perm: Ref<string | string[]>) =>
  defineComponent({
    setup: () => () =>
      withDirectives(h('div', { class: 'target' }, '按钮'), [[permissionDirective, perm.value]]),
  });

/**
 * 断言以真实 document 为准（attachTo）——移除式指令的语义是"脱离父节点"，
 * detached 元素仍可被 wrapper.find 命中，不能作移除判据。
 */
const mountWithAuthorities = (authorities: string[], perm: Ref<string | string[]>) => {
  const pinia = createPinia();
  setActivePinia(pinia);
  useUserStore().me = meOf(authorities);
  return mount(makeHost(perm), { attachTo: document.body, global: { plugins: [pinia] } });
};

const inDocument = (): boolean => document.querySelector('.target') !== null;

describe('v-permission 指令（authorities 取 user store 内存态）', () => {
  it('未命中：元素从 DOM 移除', () => {
    const wrapper = mountWithAuthorities(['user:read'], ref(['tenant:create']));
    expect(inDocument()).toBe(false);
    wrapper.unmount();
  });

  it('任一命中：保留元素', () => {
    const wrapper = mountWithAuthorities(['tenant:create'], ref(['tenant:create', 'x:y']));
    expect(inDocument()).toBe(true);
    wrapper.unmount();
  });

  it('PLATFORM 超级：保留元素（任意权限串）', () => {
    const wrapper = mountWithAuthorities(['PLATFORM'], ref(['whatever:code']));
    expect(inDocument()).toBe(true);
    wrapper.unmount();
  });

  it('绑定值变化时重评（updated 钩子）：命中→未命中即移除', async () => {
    const perm = ref<string | string[]>(['tenant:create']);
    const wrapper = mountWithAuthorities(['tenant:create'], perm);
    expect(inDocument()).toBe(true);

    perm.value = ['tenant:delete']; // 无权限
    await nextTick();
    expect(inDocument()).toBe(false);
    wrapper.unmount();
  });

  it('空数组绑定值不处理（防误杀未配置权限的调用点）', () => {
    const wrapper = mountWithAuthorities(['user:read'], ref([]));
    expect(inDocument()).toBe(true);
    wrapper.unmount();
  });
});
