package io.github.luckybearbear.forge.starter.data;

import io.github.luckybearbear.forge.platform.core.CurrentTenantSupplier;
import io.github.luckybearbear.forge.platform.core.IdGenerator;
import io.github.luckybearbear.forge.platform.core.UuidV7Generator;
import io.github.luckybearbear.forge.platform.data.ForgeTenantIdentifierResolver;
import io.github.luckybearbear.forge.platform.security.CurrentUserProvider;
import io.github.luckybearbear.forge.platform.security.TenantContextHolderSupplier;
import java.time.Instant;
import java.util.Optional;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 数据层自动装配（Phase 2 spec §6.1/§6.3）：
 * 租户解析器（Hibernate 判别式多租户）、JPA 审计（AuditorAware/DateTimeProvider）、UUIDv7 IdGenerator。
 *
 * <p>resolver 注册机制（Boot 4.1.2 + Hibernate 7.4.13 实证）：Boot 的 HibernateJpaConfiguration
 * 不自动探测 CurrentTenantIdentifierResolver bean（字节码零引用），唯一注册点是
 * {@link HibernatePropertiesCustomizer} 写入 {@code hibernate.tenant_identifier_resolver}
 * （常量位于 MultiTenancySettings，经 AvailableSettings 继承引用）；@TenantId 实体本身即启用
 * SessionFactory 判别式多租户——无 resolver 时会话打开即失败（Task 9 RED 实证）。
 */
@AutoConfiguration
@EnableJpaAuditing(dateTimeProviderRef = "forgeDateTimeProvider")
public class ForgeDataAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CurrentTenantSupplier.class)
    public CurrentTenantSupplier forgeTenantSupplier() {
        return new TenantContextHolderSupplier(); // TTL 上下文恒可用
    }

    @Bean
    @ConditionalOnMissingBean
    public ForgeTenantIdentifierResolver forgeTenantIdentifierResolver(CurrentTenantSupplier supplier) {
        return new ForgeTenantIdentifierResolver(supplier);
    }

    @Bean
    public HibernatePropertiesCustomizer forgeTenantHibernateCustomizer(ForgeTenantIdentifierResolver resolver) {
        return properties -> properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditorAware<String> forgeAuditorAware(Optional<CurrentUserProvider> userProvider) {
        return () -> userProvider.map(CurrentUserProvider::currentUser)
                .orElse(Optional.empty())
                .map(io.github.luckybearbear.forge.platform.security.CurrentUser::userId)
                .map(Optional::of)
                .orElseGet(() -> Optional.of("system")); // 无认证上下文 → system
    }

    @Bean
    public DateTimeProvider forgeDateTimeProvider() {
        return () -> Optional.of(Instant.now());
    }

    @Bean
    @ConditionalOnMissingBean(IdGenerator.class)
    public IdGenerator forgeIdGenerator() {
        return new UuidV7Generator(); // Phase 1 终审遗留项兑现
    }
}
