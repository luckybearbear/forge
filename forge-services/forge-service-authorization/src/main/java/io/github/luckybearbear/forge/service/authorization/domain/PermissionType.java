package io.github.luckybearbear.forge.service.authorization.domain;

/** 权限点类型：MENU（菜单可见性）/ API（接口调用门禁——@PreAuthorize hasAuthority 消费）。 */
public enum PermissionType {
    MENU, API
}
