package io.github.luckybearbear.forge.organization;

import io.github.luckybearbear.forge.service.organization.controller.app.OrgUnitAppController;
import io.github.luckybearbear.forge.service.organization.controller.app.PositionAppController;
import io.github.luckybearbear.forge.service.organization.controller.app.UserPositionAppController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * org 面三类控制器权限门禁注解的存在性断言（最终评审 Important-1 防回归）：类级
 * {@code @PreAuthorize("hasAuthority('org:manage')")}——Phase 2b 曾整体遗失（控制器裸奔，
 * 任何登录用户可写组织树），补齐后以反射断言钉住，防止后续重构静默抹掉。写法与 identity 的
 * UserAppController 类级（user:manage）同构。
 *
 * <p>本模块集成测试为 service 方法级（NONE 环境，无安全链），HTTP 级 403 实证（FORGE-0403）
 * 由 demo 的 DemoAuthFlowIntegrationTest 承载（mold-viewer 仅 mold:read 调
 * GET /app-api/org-units/tree）。
 */
class OrgControllersPreAuthorizeAnnotationTest {

    @Test
    void orgFaceControllersCarryClassLevelOrgManagePreAuthorize() {
        for (Class<?> controller : new Class<?>[] {
                OrgUnitAppController.class, PositionAppController.class, UserPositionAppController.class}) {
            PreAuthorize annotation = controller.getAnnotation(PreAuthorize.class);
            assertThat(annotation)
                    .as("%s 类级 @PreAuthorize（org 面权限门禁，遗失即回归）", controller.getSimpleName())
                    .isNotNull();
            assertThat(annotation.value())
                    .as("%s 门禁表达式", controller.getSimpleName())
                    .isEqualTo("hasAuthority('org:manage')");
        }
    }
}
