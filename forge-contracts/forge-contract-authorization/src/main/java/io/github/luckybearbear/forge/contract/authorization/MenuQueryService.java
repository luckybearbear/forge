package io.github.luckybearbear.forge.contract.authorization;

import java.util.List;
import java.util.Set;

/**
 * 跨模块同步查询契约：service-authorization 实现，identity /menus 等侧消费——按权限集返回
 * <b>可见菜单树</b>（MENU 按 code∈authorities 过滤、PLATFORM 超级全通过；DIR 由后代可见性补全，
 * 空 DIR 折叠；BUTTON/API 不进树）。
 *
 * <p>authorities 取 token 快照（identity 侧直读 ForgeAuthentication，不经 UserPermissionsQuery
 * 重查）——菜单面与权限门禁同一快照口径，授权变更后重登生效。
 */
public interface MenuQueryService {

    List<MenuNodeDTO> visibleMenus(Set<String> authorities);
}
