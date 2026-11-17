package io.github.luckybearbear.forge.starter.data;

import io.github.luckybearbear.forge.platform.data.ForgeTenantIdentifierResolver;
import jakarta.persistence.metamodel.Metamodel;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 覆盖治理（2b Step 4）：用户自带 {@code CurrentTenantIdentifierResolver} 时——
 * forge 的 resolver 让位（@ConditionalOnMissingBean 按接口类型），customizer 随之不注册
 * （@ConditionalOnBean(ForgeTenantIdentifierResolver.class)），避免
 * {@code hibernate.tenant_identifier_resolver} 属性键竞争（用户自管其接线）。
 * 上下文切片（无容器），装配级多租户行为由 DataStarterIntegrationTest 覆盖。
 */
class ForgeDataAutoConfigurationOverrideTest {

    // jpaMappingContext：@EnableJpaAuditing 的 jpaAuditingHandler 引用之——真实 Boot 应用由
    // JPA repos 基建提供；切片上下文给最小件（mock Metamodel——构造器断言非空，不再做推导）
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean("jpaMappingContext", JpaMetamodelMappingContext.class,
                    () -> new JpaMetamodelMappingContext(java.util.Set.of(mock(Metamodel.class))))
            .withConfiguration(AutoConfigurations.of(ForgeDataAutoConfiguration.class));

    @Test
    void customizerRegisteredWithForgeResolverByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ForgeTenantIdentifierResolver.class);
            assertThat(context).hasSingleBean(HibernatePropertiesCustomizer.class);
        });
    }

    @Test
    void userProvidedResolverForcesCustomizerToYield() {
        runner.withBean("userTenantResolver", CurrentTenantIdentifierResolver.class,
                    () -> new CurrentTenantIdentifierResolver<>() {
                        @Override
                        public String resolveCurrentTenantIdentifier() {
                            return "user-managed";
                        }

                        @Override
                        public boolean validateExistingCurrentSessions() {
                            return false;
                        }
                    })
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ForgeTenantIdentifierResolver.class);
                    assertThat(context).doesNotHaveBean("forgeTenantHibernateCustomizer");
                    assertThat(context).doesNotHaveBean(HibernatePropertiesCustomizer.class);
                });
    }
}
