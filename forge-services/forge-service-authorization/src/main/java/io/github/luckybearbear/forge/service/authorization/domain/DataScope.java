package io.github.luckybearbear.forge.service.authorization.domain;

/** 数据权限范围（spec §6.1：角色携带，查询侧据此收敛可见行集；CUSTOM 留给自定义行级策略）。 */
public enum DataScope {
    ALL, TENANT, DEPARTMENT, DEPT_TREE, SELF, CUSTOM
}
