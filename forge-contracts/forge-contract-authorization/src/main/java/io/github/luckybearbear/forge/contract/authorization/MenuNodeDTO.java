package io.github.luckybearbear.forge.contract.authorization;

import java.util.List;

/**
 * 菜单树节点（菜单合一模型：菜单=带路由元数据的权限点，auth_permission 单表承载）。
 *
 * <p><b>可见性语义（service-authorization visibleMenus）</b>：MENU 型按 code∈authorities 过滤
 * （{@code PLATFORM} 视为超级全通过）；DIR 型不参与鉴权，由后代可见性决定（自底向上补全祖先链，
 * 空 DIR 折叠不返回）；BUTTON/API 型永不进可见菜单树（admin 字典树 {@code tree()} 全量含）。
 * {@code visible} 是前端展示开关（隐藏但路由可注册），后端不据此过滤。
 *
 * <p>前端 MenuNode（forge-web packages/shared types.ts）与本 record 同构——签名钉子见
 * contract-authorization 测试 MenuQueryContractTest。
 */
public record MenuNodeDTO(
        String id,
        String parentId,
        String type,
        String code,
        String name,
        String path,
        String component,
        String icon,
        Integer sort,
        Boolean visible,
        List<MenuNodeDTO> children) {}
