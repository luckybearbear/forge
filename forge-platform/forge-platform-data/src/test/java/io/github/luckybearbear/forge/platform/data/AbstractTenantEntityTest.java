package io.github.luckybearbear.forge.platform.data;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 平台视角写守护单元语义（2b 前置加固 Step 1）：
 * 守护注入且 disallowPlatformWrites()=true 时 @PrePersist（assignId 钩子）fail-fast 拒绝；
 * 守护未注入（独立单测场景）或租户上下文正常时放行。集成级（装配+真实落库）见
 * data-starter DataStarterIntegrationTest#platformViewWriteRejected。
 */
class AbstractTenantEntityTest {

    @AfterEach
    void resetGuard() {
        AbstractTenantEntity.setWriteGuard(null); // 静态状态：勿泄漏到其他测试
    }

    @Test
    void noGuardSkipsPlatformWriteCheck() {
        TestTenantEntity entity = new TestTenantEntity();
        assertThatCode(entity::assignId).doesNotThrowAnyException();
        assertThat(entity.getId()).contains("-");
    }

    @Test
    void guardRejectsPersistUnderPlatformView() {
        AbstractTenantEntity.setWriteGuard(() -> true);
        TestTenantEntity entity = new TestTenantEntity();
        assertThatThrownBy(entity::assignId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("platform view")
                .hasMessageContaining(ForgeTenantIdentifierResolver.PLATFORM_TENANT_ID)
                .hasMessageContaining("TestTenantEntity");
    }

    @Test
    void guardAllowsPersistUnderTenantContext() {
        AbstractTenantEntity.setWriteGuard(() -> false);
        TestTenantEntity entity = new TestTenantEntity();
        entity.assignId();
        assertThat(entity.getId()).isNotBlank();
    }

    @Test
    void setWriteGuardIsReplaceableAndClearable() {
        AbstractTenantEntity.setWriteGuard(() -> true);
        AbstractTenantEntity.setWriteGuard(null); // clear
        TestTenantEntity entity = new TestTenantEntity();
        assertThatCode(entity::assignId).doesNotThrowAnyException();
    }

    static class TestTenantEntity extends AbstractTenantEntity {}
}
