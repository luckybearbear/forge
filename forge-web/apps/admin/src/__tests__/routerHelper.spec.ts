import { describe, expect, it } from 'vitest';
import { defineComponent } from 'vue';
import type { RouteRecordRaw } from 'vue-router';
import type { MenuNode } from '@forge/web-shared';

import Layout from '@/layouts/Layout.vue';
import ParentView from '@/components/ParentView.vue';
import { buildDynamicRoutes, resolveViewModule } from '@/router/routerHelper';
import type { ViewModule, ViewModules } from '@/router/routerHelper';

/** glob 依赖注入：stub 模块映射（key 约定 ../views/<component 串>.vue|.tsx）。 */
const stubView: ViewModule = () =>
  Promise.resolve({ default: defineComponent({ name: 'StubView' }) });

const modules: ViewModules = {
  '../views/system/tenant/index.vue': stubView,
  '../views/system/tsxonly/index.tsx': stubView,
};

const menu = (over: Partial<MenuNode>): MenuNode => ({
  id: 'm',
  parentId: null,
  type: 'MENU',
  code: 'c',
  name: 'n',
  path: null,
  component: null,
  icon: null,
  sort: null,
  visible: true,
  children: null,
  ...over,
});

type WithChildren = RouteRecordRaw & { children: RouteRecordRaw[] };

describe('resolveViewModule（component 串 → glob 模块）', () => {
  it('.vue 精确映射；同名 .vue 优先于 .tsx', () => {
    const both: ViewModules = {
      ...modules,
      '../views/system/tenant/index.tsx': stubView,
    };
    expect(resolveViewModule('system/tenant/index', both)).toBe(
      both['../views/system/tenant/index.vue'],
    );
  });

  it('仅 .tsx 命中亦可解析', () => {
    expect(resolveViewModule('system/tsxonly/index', modules)).toBe(
      modules['../views/system/tsxonly/index.tsx'],
    );
  });

  it('未建视图/空串返回 null', () => {
    expect(resolveViewModule('ghost/index', modules)).toBeNull();
    expect(resolveViewModule(null, modules)).toBeNull();
  });
});

describe('buildDynamicRoutes（菜单树 → 动态路由）', () => {
  it('顶级 MENU：挂 Layout，叶子 name=code、component=glob 加载器、meta 携 title/code；外层不带 title（面包屑去重）', () => {
    const routes = buildDynamicRoutes(
      [
        menu({
          code: 'dashboard',
          name: '仪表盘',
          path: '/dashboard',
          component: 'system/tenant/index',
        }),
      ],
      modules,
    );
    expect(routes).toHaveLength(2); // + 404 兜底
    const top = routes[0] as WithChildren;
    expect(top.component).toBe(Layout);
    expect(top.path).toBe('/dashboard');
    expect(top.meta?.title).toBeUndefined();
    const leaf = top.children[0];
    expect(leaf.path).toBe('');
    expect(leaf.name).toBe('dashboard');
    expect(leaf.component).toBe(stubView);
    expect(leaf.meta?.title).toBe('仪表盘');
    expect(leaf.meta?.code).toBe('dashboard');
  });

  it('顶级 DIR：以 Layout 承载（meta 携 DIR title），子 MENU 绝对路径直挂；DIR 无 path 以 /{code} 兜底', () => {
    const routes = buildDynamicRoutes(
      [
        menu({
          type: 'DIR',
          code: 'sys-dir',
          name: '系统管理',
          icon: 'Setting',
          children: [
            menu({
              code: 'tenant:manage',
              name: '租户管理',
              path: '/system/tenants',
              component: 'system/tenant/index',
            }),
            menu({
              code: 'role:manage',
              name: '权限字典',
              path: '/system/permissions',
              component: 'system/tsxonly/index',
            }),
          ],
        }),
      ],
      modules,
    );
    const top = routes[0] as WithChildren;
    expect(top.component).toBe(Layout);
    expect(top.path).toBe('/sys-dir');
    expect(top.meta?.title).toBe('系统管理');
    expect(top.children.map((child) => child.path)).toEqual([
      '/system/tenants',
      '/system/permissions',
    ]);
    expect(top.children[0].name).toBe('tenant:manage');
  });

  it('嵌套 DIR：中间层挂 ParentView（极简直通视图）', () => {
    const routes = buildDynamicRoutes(
      [
        menu({
          type: 'DIR',
          code: 'outer',
          name: '外层',
          path: '/outer',
          children: [
            menu({
              type: 'DIR',
              code: 'inner',
              name: '内层',
              children: [menu({ code: 'leaf', path: '/leaf', component: 'system/tenant/index' })],
            }),
          ],
        }),
      ],
      modules,
    );
    const top = routes[0] as WithChildren;
    expect(top.component).toBe(Layout);
    const middle = top.children[0] as WithChildren;
    expect(middle.component).toBe(ParentView);
    expect(middle.meta?.title).toBe('内层');
    expect(middle.children[0].path).toBe('/leaf');
  });

  it('视图未建/缺 path 的 MENU 跳过、BUTTON/API 不进路由、空 DIR 折叠（均不阻断兄弟节点）', () => {
    const routes = buildDynamicRoutes(
      [
        menu({ code: 'ghost', path: '/ghost', component: 'ghost/index' }), // 视图未建（Task 4 窗口期）
        menu({ code: 'btn', type: 'BUTTON' }),
        menu({ code: 'api', type: 'API' }),
        menu({ type: 'DIR', code: 'empty', name: '空目录', children: [] }),
        menu({ code: 'nopath', component: 'system/tenant/index' }), // 缺 path
      ],
      modules,
    );
    expect(routes).toHaveLength(1); // 仅 404 兜底
  });

  it('404 兜底恒为末位（catch-all 最后注册）', () => {
    const routes = buildDynamicRoutes(
      [menu({ code: 'dashboard', path: '/dashboard', component: 'system/tenant/index' })],
      modules,
    );
    expect(routes[routes.length - 1].path).toBe('/:pathMatch(.*)*');
  });
});
