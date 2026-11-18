package io.github.luckybearbear.forge.contract.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 菜单契约签名钉子（Task 1 契约先行）：MenuNodeDTO 的 11 个组件（路由元数据 + children 递归）与
 * MenuQueryService.visibleMenus(Set&lt;String&gt;) → List&lt;MenuNodeDTO&gt; 的泛型签名——前端
 * MenuNode 同构约定（forge-web shared/types.ts）的反序列化锚点，签名漂移在此失败而非页面侧。
 */
class MenuQueryContractTest {

    @Test
    void menuNodeDtoCarriesRouteMetadataAndRecursiveChildren() {
        RecordComponent[] components = MenuNodeDTO.class.getRecordComponents();
        assertThat(components).extracting(RecordComponent::getName).containsExactly(
                "id", "parentId", "type", "code", "name", "path", "component", "icon", "sort", "visible",
                "children");
        // 路由元数据类型：parentId/type/code/name 字符串族，sort/visible 数值开关族，children 递归
        assertThat(MenuNodeDTO.class).hasDeclaredFields("parentId", "path", "component", "icon", "sort",
                "visible");
        assertThat(componentType("children")).isEqualTo(List.class);
    }

    @Test
    void menuQueryServiceExposesVisibleMenusOverAuthoritySet() throws NoSuchMethodException {
        Method visibleMenus = MenuQueryService.class.getMethod("visibleMenus", Set.class);
        assertThat(visibleMenus.getReturnType()).isEqualTo(List.class);
        assertThat(visibleMenus.getGenericReturnType()).isInstanceOf(ParameterizedType.class);
        assertThat(((ParameterizedType) visibleMenus.getGenericReturnType()).getActualTypeArguments()[0])
                .isEqualTo(MenuNodeDTO.class);
    }

    private static Class<?> componentType(String name) {
        try {
            return MenuNodeDTO.class.getDeclaredField(name).getType();
        } catch (NoSuchFieldException ex) {
            throw new AssertionError("missing record component: " + name, ex);
        }
    }
}
