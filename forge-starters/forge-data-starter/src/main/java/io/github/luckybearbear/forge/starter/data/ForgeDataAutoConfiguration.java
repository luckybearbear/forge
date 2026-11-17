package io.github.luckybearbear.forge.starter.data;

import io.github.luckybearbear.forge.platform.core.CurrentTenantSupplier;
import io.github.luckybearbear.forge.platform.core.IdGenerator;
import io.github.luckybearbear.forge.platform.core.UuidV7Generator;
import io.github.luckybearbear.forge.platform.data.AbstractTenantEntity;
import io.github.luckybearbear.forge.platform.data.ForgeTenantIdentifierResolver;
import io.github.luckybearbear.forge.platform.security.CurrentUserProvider;
import io.github.luckybearbear.forge.platform.security.TenantContextHolderSupplier;
import java.time.Instant;
import java.util.Optional;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
 *
 * <p><b>覆盖治理（2b 前置加固 Step 4）</b>：用户自带 {@code CurrentTenantIdentifierResolver} 时——
 * forge resolver 按接口类型让位（@ConditionalOnMissingBean），customizer 随之不注册
 * （@ConditionalOnBean(ForgeTenantIdentifierResolver.class)），避免
 * {@code hibernate.tenant_identifier_resolver} 属性键竞争；多租户接线整体移交用户自管。
 *
 * <p><b>平台视角写守护装配（2b 前置加固 Step 1）</b>：customizer 注册时装配注入
 * {@code AbstractTenantEntity.setWriteGuard(supplier::ignore)}——平台视角（ignore）下的
 * 租户实体写入在 @PrePersist fail-fast 拒绝（tenant_id='0' 数据黑洞防护）；supplier 为
 * TenantContextHolderSupplier（无状态读 TTL），跨上下文无陈旧引用风险。
 */
@AutoConfiguration
@EnableJpaAuditing(dateTimeProviderRef = "forgeDateTimeProvider")
public class ForgeDataAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CurrentTenantSupplier.class)
    public CurrentTenantSupplier forgeTenantSupplier() {
        return new TenantContextHolderSupplier(); // TTL 上下文恒可用
    }

    /** 让位语义按接口类型：用户自带任意 CurrentTenantIdentifierResolver 实现即整体让出。 */
    @Bean
    @ConditionalOnMissingBean(CurrentTenantIdentifierResolver.class)
    public ForgeTenantIdentifierResolver forgeTenantIdentifierResolver(CurrentTenantSupplier supplier) {
        return new ForgeTenantIdentifierResolver(supplier);
    }

    @Bean
    @ConditionalOnBean(ForgeTenantIdentifierResolver.class)
    public HibernatePropertiesCustomizer forgeTenantHibernateCustomizer(ForgeTenantIdentifierResolver resolver,
            CurrentTenantSupplier supplier) {
        // 平台视角写守护：装配时注入（独立于 Hibernate 基建，仅随 forge resolver 注册）
        AbstractTenantEntity.setWriteGuard(supplier::ignore);
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
