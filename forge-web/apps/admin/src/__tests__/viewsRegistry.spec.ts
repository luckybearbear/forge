import { describe, expect, it } from 'vitest';

import { VIEW_MODULES, resolveViewModule } from '@/router/routerHelper';

/**
 * seed component 串 ↔ views 文件一一对应断言（spec §8 风险项落地，Task 4 验收项）。
 *
 * 常量清单=service-authorization 迁移 V202611190000 菜单 seed 携带的 component 串：
 * dashboard / tenant:manage / role:manage 三个 MENU 节点。若字典引用的视图缺文件，
 * routerHelper 会静默跳过注册（warn 不阻断）——本钉子让"菜单点了没路由"在测试期显形。
 */
const SEED_COMPONENTS = [
  'system/dashboard/index',
  'system/tenant/index',
  'system/permission/index',
] as const;

const componentOf = (globKey: string): string =>
  globKey.replace('../views/', '').replace(/\.(vue|tsx)$/, '');

describe('seed component 串 ↔ views 文件一一对应（import.meta.glob 实文件对照）', () => {
  it('每个 seed component 串恰有一个视图文件（.vue/.tsx 不并存）', () => {
    const keys = Object.keys(VIEW_MODULES);
    for (const component of SEED_COMPONENTS) {
      const matches = keys.filter((key) => componentOf(key) === component);
      expect(matches, `${component} 应恰有一个视图文件，实得 ${matches.join(',')}`).toHaveLength(1);
    }
  });

  it('resolveViewModule 对全部 seed component 串命中（动态路由不会被静默跳过）', () => {
    for (const component of SEED_COMPONENTS) {
      expect(resolveViewModule(component, VIEW_MODULES), component).not.toBeNull();
    }
  });

  it('system/ 子树无孤儿视图（glob 集合=常量清单——拼错路径的死页面显形）', () => {
    const systemComponents = Object.keys(VIEW_MODULES)
      .filter((key) => key.startsWith('../views/system/'))
      .map(componentOf);
    expect([...systemComponents].sort()).toEqual([...SEED_COMPONENTS].sort());
  });
});
