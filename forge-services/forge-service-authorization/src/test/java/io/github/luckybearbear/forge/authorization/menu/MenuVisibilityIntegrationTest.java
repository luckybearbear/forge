package io.github.luckybearbear.forge.authorization.menu;

import io.github.luckybearbear.forge.contract.authorization.MenuNodeDTO;
import io.github.luckybearbear.forge.contract.authorization.MenuQueryService;
import io.github.luckybearbear.forge.platform.test.AbstractIntegrationTest;
import io.github.luckybearbear.forge.service.authorization.AuthorizationServiceAutoConfiguration;
import io.github.luckybearbear.forge.service.authorization.MenuQueryServiceImpl;
import io.github.luckybearbear.forge.service.authorization.app.PermissionDTO;
import io.github.luckybearbear.forge.service.authorization.app.PermissionService;
import io.github.luckybearbear.forge.service.authorization.domain.PermissionEntity;
import io.github.luckybearbear.forge.service.authorization.domain.PermissionRepository;
import io.github.luckybearbear.forge.service.authorization.domain.PermissionType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 菜单切片（Task 1）容器测试：visibleMenus <b>五语义</b>——①按 authorities 过滤（无权菜单不返回）
 * ②PLATFORM 超级全量 ③空 DIR 折叠 ④祖先 DIR 链补全 ⑤sort 升序排序；外加 admin 字典树
 * {@code tree()} 全量含 BUTTON/API + 六新列读写往返 + 契约 {@link MenuQueryService} bean 暴露。
 * seed（V202611190000：dashboard/sys-dir/tenant:manage/role:manage 转 MENU）作全局共享字典在场
 * ——断言一律 contains/doesNotContain（其他行不构成干扰）。
 *
 * <p><b>包位置在服务根包之外</b>（...forge.authorization.menu，flow 测试类同范式）：服务自动配置以
 * 服务根包做 @ComponentScan，测试类栖身该包会令 TestApp 被扫描注册并交叉泄漏；服务 bean 经
 * {@code @Import(AuthorizationServiceAutoConfiguration)} 装配。auth_permission 为全局表——无需租户
 * 上下文，字典行直接落库（code 唯一，各用例独立前缀防互相干扰）。
 */
@SpringBootTest(classes = MenuVisibilityIntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(AuthorizationServiceAutoConfiguration.class)
class MenuVisibilityIntegrationTest extends AbstractIntegrationTest {

    @SpringBootApplication
    static class TestApp {
    }

    @Autowired PermissionService permissionService;
    @Autowired MenuQueryService menuQueryService; // 契约 bean（模块根包 MenuQueryServiceImpl）
    @Autowired PermissionRepository permissionRepository;

    // ---- 语义 ①：按 authorities 过滤（无权菜单不返回）----

    @Test
    void visibleMenusFilterMenusByAuthorityCodes() {
        PermissionDTO alpha = menu("m-alpha-a", null, null);
        menu("m-alpha-b", null, null);

        List<MenuNodeDTO> visible = permissionService.visibleMenus(Set.of("m-alpha-a"));

        assertThat(visible).extracting(MenuNodeDTO::code).contains(alpha.code());
        assertThat(flattenCodes(visible))
                .contains("m-alpha-a")
                .doesNotContain("m-alpha-b", "dashboard", "tenant:manage", "role:manage");
        // 空权限集：全不可见（DIR 无可见后代亦折叠）
        assertThat(permissionService.visibleMenus(Set.of())).isEmpty();
    }

    // ---- 语义 ②：PLATFORM 超级全量 ----

    @Test
    void platformAuthorityGrantsFullMenuVisibility() {
        menu("m-beta-a", null, 5);

        List<MenuNodeDTO> visible = permissionService.visibleMenus(Set.of(PermissionService.PLATFORM_AUTHORITY));

        // 新建 + seed 全部 MENU 可见；DIR sys-dir 因后代（tenant:manage/role:manage）可见而补全
        assertThat(flattenCodes(visible))
                .contains("m-beta-a", "dashboard", "sys-dir", "tenant:manage", "role:manage");

        // seed 行结构核对（V202611181300 + V202611190000）：role:manage 经 UPDATE 转 MENU 挂 sys-dir
        assertThat(permissionRepository.findByCodeIn(List.of("dashboard", "sys-dir", "role:manage")))
                .hasSize(3)
                .allSatisfy(row -> assertThat(row.getVisible()).isTrue());
        PermissionEntity roleManage = permissionRepository.findByCode("role:manage").orElseThrow();
        assertThat(roleManage.getType()).isEqualTo(PermissionType.MENU);
        assertThat(roleManage.getParentId()).isEqualTo("perm-node-dir-sys");
        assertThat(roleManage.getPath()).isEqualTo("/system/permissions");
        assertThat(roleManage.getComponent()).isEqualTo("system/permission/index");
        assertThat(roleManage.getSort()).isEqualTo(120);
        PermissionEntity tenantManage = permissionRepository.findByCode("tenant:manage").orElseThrow();
        assertThat(tenantManage.getType()).isEqualTo(PermissionType.MENU);
        assertThat(tenantManage.getParentId()).isEqualTo("perm-node-dir-sys");
    }

    // ---- 语义 ③④：空 DIR 折叠 + 祖先 DIR 链补全 ----

    @Test
    void emptyDirCollapsesWhileAncestorDirChainIsCompleted() {
        dir("d-gamma-lonely", null); // 空目录：无可见后代 → 折叠不返回
        PermissionDTO outer = dir("d-gamma-outer", null);
        PermissionDTO inner = dir("d-gamma-inner", outer.id());
        menu("m-gamma-nested", inner.id(), null);

        List<MenuNodeDTO> visible = permissionService.visibleMenus(Set.of("m-gamma-nested"));

        assertThat(flattenCodes(visible))
                .contains("d-gamma-outer", "d-gamma-inner", "m-gamma-nested")
                .doesNotContain("d-gamma-lonely", "dashboard");

        // 树形链路：outer（根 DIR）→ inner（DIR）→ nested（MENU 叶）
        MenuNodeDTO root = find(visible, "d-gamma-outer");
        assertThat(root.type()).isEqualTo("DIR");
        assertThat(root.parentId()).isNull();
        assertThat(root.children()).extracting(MenuNodeDTO::code).containsExactly("d-gamma-inner");
        assertThat(root.children().getFirst().children())
                .extracting(MenuNodeDTO::code).containsExactly("m-gamma-nested");
        // MENU 不在根级（挂于 DIR 之下）
        assertThat(visible).extracting(MenuNodeDTO::code).doesNotContain("m-gamma-nested");

        // DIR 不参与鉴权：孤立 DIR 的 code 命中 authorities 也不产生节点（只有 MENU 型按 code 过滤）
        assertThat(flattenCodes(permissionService.visibleMenus(Set.of("d-gamma-lonely"))))
                .doesNotContain("d-gamma-lonely");
    }

    // ---- 语义 ⑤：sort 升序稳定排序（null 最后）----

    @Test
    void siblingsAreSortedBySortAscendingWithNullsLast() {
        PermissionDTO parent = dir("d-delta", null);
        menu("m-delta-30", parent.id(), 30);
        menu("m-delta-10", parent.id(), 10);
        menu("m-delta-null", parent.id(), null);
        menu("m-delta-20", parent.id(), 20);

        List<MenuNodeDTO> visible = permissionService.visibleMenus(
                Set.of("m-delta-30", "m-delta-10", "m-delta-null", "m-delta-20"));

        assertThat(find(visible, "d-delta").children())
                .extracting(MenuNodeDTO::code)
                .containsExactly("m-delta-10", "m-delta-20", "m-delta-30", "m-delta-null");
    }

    // ---- admin 字典树：全量含 BUTTON/API + 新列读写 ----

    @Test
    void adminTreeIncludesAllTypesAndCarriesRouteMetadata() {
        PermissionDTO parent = dir("d-epsilon", null);
        PermissionDTO menuRow = permissionService.create("m-epsilon", "Epsilon 菜单", PermissionType.MENU,
                parent.id(), "/epsilon", "system/epsilon/index", "Star", 42, false);
        permissionService.create("b-epsilon", "Epsilon 按钮", PermissionType.BUTTON, menuRow.id(),
                null, null, null, null, null);
        permissionService.create("a-epsilon", "Epsilon 接口", PermissionType.API, null,
                null, null, null, null, null);

        // 字典树全量：DIR/MENU/BUTTON/API 均在场（字典管理视角，含空 DIR 与 visible=false）
        List<MenuNodeDTO> tree = permissionService.tree();
        assertThat(flattenCodes(tree)).contains("d-epsilon", "m-epsilon", "b-epsilon", "a-epsilon");

        MenuNodeDTO menuNode = find(tree, "m-epsilon");
        assertThat(menuNode.parentId()).isEqualTo(parent.id());
        assertThat(menuNode.type()).isEqualTo("MENU");
        assertThat(menuNode.path()).isEqualTo("/epsilon");
        assertThat(menuNode.component()).isEqualTo("system/epsilon/index");
        assertThat(menuNode.icon()).isEqualTo("Star");
        assertThat(menuNode.sort()).isEqualTo(42);
        assertThat(menuNode.visible()).isFalse();
        assertThat(flattenCodes(menuNode.children())).containsExactly("b-epsilon"); // BUTTON 挂 MENU 下

        // 可见菜单树：BUTTON/API 永不进树；visible=false 的 MENU 仍返回（展示开关，后端不据此过滤）
        List<MenuNodeDTO> visible = permissionService.visibleMenus(
                Set.of("m-epsilon", "b-epsilon", "a-epsilon"));
        assertThat(flattenCodes(visible))
                .contains("d-epsilon", "m-epsilon")
                .doesNotContain("b-epsilon", "a-epsilon");
        assertThat(find(visible, "m-epsilon").visible()).isFalse();

        // update 新列（部分更新：path/sort 改写，其余 null 不动）
        permissionService.update(menuRow.id(), null, null, null, "/epsilon2", null, null, 7, null);
        MenuNodeDTO updated = find(permissionService.tree(), "m-epsilon");
        assertThat(updated.path()).isEqualTo("/epsilon2");
        assertThat(updated.sort()).isEqualTo(7);
        assertThat(updated.component()).isEqualTo("system/epsilon/index"); // null 未触碰
    }

    // ---- 契约暴露 ----

    @Test
    void menuQueryContractBeanIsExposedAndDelegates() {
        assertThat(menuQueryService).isInstanceOf(MenuQueryServiceImpl.class);
        menu("m-zeta", null, null);
        assertThat(flattenCodes(menuQueryService.visibleMenus(Set.of("m-zeta")))).contains("m-zeta");
    }

    // ---- helpers ----

    private PermissionDTO menu(String code, String parentId, Integer sort) {
        return permissionService.create(code, code + " 菜单", PermissionType.MENU, parentId,
                "/" + code, "system/" + code + "/index", "MenuIcon", sort, null);
    }

    private PermissionDTO dir(String code, String parentId) {
        return permissionService.create(code, code + " 目录", PermissionType.DIR, parentId,
                null, null, "DirIcon", null, null); // DIR 路由字段可不填——service 不强校验
    }

    private static List<String> flattenCodes(List<MenuNodeDTO> nodes) {
        List<String> codes = new ArrayList<>();
        appendCodes(nodes, codes);
        return codes;
    }

    private static void appendCodes(List<MenuNodeDTO> nodes, List<String> sink) {
        for (MenuNodeDTO node : nodes) {
            sink.add(node.code());
            appendCodes(node.children(), sink);
        }
    }

    private static MenuNodeDTO find(List<MenuNodeDTO> nodes, String code) {
        MenuNodeDTO found = findNested(nodes, code);
        if (found == null) {
            throw new AssertionError("menu node not found: " + code);
        }
        return found;
    }

    private static MenuNodeDTO findNested(List<MenuNodeDTO> nodes, String code) {
        for (MenuNodeDTO node : nodes) {
            if (code.equals(node.code())) {
                return node;
            }
            MenuNodeDTO nested = findNested(node.children(), code);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }
}
