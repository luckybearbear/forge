package io.github.luckybearbear.forge.service.authorization;

import io.github.luckybearbear.forge.contract.authorization.MenuNodeDTO;
import io.github.luckybearbear.forge.contract.authorization.MenuQueryService;
import io.github.luckybearbear.forge.service.authorization.app.PermissionService;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 契约 {@link MenuQueryService} 实现：可见菜单树查询入口——identity /menus（token 快照
 * authorities 直取）经本 bean 消费 authorization 侧字典。栖身模块根包（随
 * AuthorizationServiceAutoConfiguration 的 @ComponentScan 暴露为契约 bean，identity 经
 * ObjectProvider 可选装配——未装配时消费方缺省空菜单）。
 *
 * <p>auth_permission 为全局表——无租户上下文直查（平台视角即可，字典全租户共享）。
 * 可见性算法全码见 {@link PermissionService#visibleMenus(Set)}。
 */
@Service
public class MenuQueryServiceImpl implements MenuQueryService {

    private final PermissionService permissionService;

    public MenuQueryServiceImpl(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    public List<MenuNodeDTO> visibleMenus(Set<String> authorities) {
        return permissionService.visibleMenus(authorities);
    }
}
