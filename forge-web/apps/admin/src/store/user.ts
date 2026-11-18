/**
 * 用户会话 store：/me 快照（token 快照语义）只存 pinia 内存态（spec：菜单/用户不进
 * localStorage）；token 单独由 shared cache 托管。reset 供登出/守卫装配失败清态。
 */
import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import { getMe } from '@forge/web-shared';
import type { MeResponse } from '@forge/web-shared';

export const useUserStore = defineStore('user', () => {
  const me = ref<MeResponse | null>(null);
  /** 会话是否已装配（守卫据此短路装配流程）。 */
  const loaded = ref(false);
  const authorities = computed<string[]>(() => me.value?.authorities ?? []);

  async function loadMe(): Promise<void> {
    me.value = await getMe();
    loaded.value = true;
  }

  function reset(): void {
    me.value = null;
    loaded.value = false;
  }

  return { me, loaded, authorities, loadMe, reset };
});
