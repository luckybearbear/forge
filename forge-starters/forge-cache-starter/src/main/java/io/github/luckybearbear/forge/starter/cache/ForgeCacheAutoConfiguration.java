package io.github.luckybearbear.forge.starter.cache;

import io.github.luckybearbear.forge.platform.cache.RedisCacheFacade;
import io.github.luckybearbear.forge.platform.cache.TenantRedisCacheManager;
import io.github.luckybearbear.forge.platform.core.CurrentTenantSupplier;
import java.time.Duration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

/**
 * 缓存层自动装配（Phase 1 SPI 的 starter 兑现）：RedisTemplate&lt;String,Object&gt; JSON 基线 +
 * {@link RedisCacheFacade}（程序式租户前缀缓存）+ {@link TenantRedisCacheManager}（Spring Cache
 * API 侧的租户前缀 CacheManager，@Primary）。
 *
 * <p><b>与 Boot 默认 redisTemplate 的让位关系（禁止双注册）</b>：Boot 的
 * {@code DataRedisAutoConfiguration.redisTemplate}（RedisTemplate&lt;Object,Object&gt;，JDK 序列化）
 * 与本类的 {@code redisTemplate}（RedisTemplate&lt;String,Object&gt;，String key + Jackson3 JSON）
 * 同名同 @ConditionalOnMissingBean(name="redisTemplate")——本类 {@code before} Boot 显式定序：
 * forge 模板先注册，Boot 默认按名让位，上下文恒恰一个 {@code redisTemplate}。2a 的
 * RedisTokenStore 用 {@code StringRedisTemplate}（不同 bean、String 值编码），互不冲突。
 * 用户自定义同名 bean 时双方都让位（用户 bean 恒先于自动配置处理）。
 *
 * <p><b>JSON 泛型序列化</b>：{@link GenericJacksonJsonRedisSerializer}（spring-data-redis 4.x 的
 * Jackson 3 实现，tools.jackson）+ default typing（白名单校验：本项目/集合/时间/JDK 基础类型）——
 * Object 值往返保留具体类型（Set/List/DTO）。JDK 序列化被有意弃用（跨版本/跨语言不可演化、
 * 反序列化 gadget 面大）。
 *
 * <p><b>CurrentTenantSupplier 来源</b>：本 starter 只依赖接口——生产装配中由 forge-data-starter
 * 提供（TenantContextHolderSupplier，@ConditionalOnMissingBean 让位治理）；只引 cache-starter
 * 的应用须自备 supplier，否则 facade/cacheManager bean 创建按缺依赖 fail-fast（README 约定）。
 */
@AutoConfiguration(before = org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration.class)
@ConditionalOnClass(RedisOperations.class)
@EnableCaching
public class ForgeCacheAutoConfiguration {

    /**
     * RedisTemplate 基线：String key + Jackson3 JSON value（含 hash key/value）。
     * bean 名固定 {@code redisTemplate}——覆盖 Boot 的 JDK 序列化默认（见类 javadoc 让位关系）。
     */
    @Bean(name = "redisTemplate")
    @ConditionalOnMissingBean(name = "redisTemplate")
    RedisTemplate<String, Object> forgeRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(RedisSerializer.string());
        template.setHashKeySerializer(RedisSerializer.string());
        GenericJacksonJsonRedisSerializer jsonSerializer = forgeJsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /** 程序式缓存门面：key 经 {@code t:{tenantId}:}/{@code p:} 前缀化（TTL 语义见 CacheFacade）。 */
    @Bean
    @ConditionalOnMissingBean(io.github.luckybearbear.forge.platform.cache.CacheFacade.class)
    RedisCacheFacade redisCacheFacade(RedisTemplate<String, Object> redisTemplate,
            CurrentTenantSupplier tenantSupplier) {
        return new RedisCacheFacade(redisTemplate, tenantSupplier);
    }

    /**
     * Spring Cache API（@Cacheable 等）侧的 CacheManager：逻辑 cacheName 映射
     * {@code t:{tenantId}:{name}}/{@code p:{name}}（TenantRedisCacheManager 覆写 getCache）。
     * @ConditionalOnMissingBean(CacheManager.class)：应用自定义任意 CacheManager 即整体让位。
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(CacheManager.class)
    TenantRedisCacheManager tenantRedisCacheManager(RedisConnectionFactory connectionFactory,
            CurrentTenantSupplier tenantSupplier) {
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(SerializationPair.fromSerializer(RedisSerializer.string()))
                .serializeValuesWith(SerializationPair.fromSerializer(forgeJsonRedisSerializer()))
                .entryTtl(Duration.ofMinutes(30));
        return new TenantRedisCacheManager(RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory),
                configuration, tenantSupplier);
    }

    /**
     * 泛型 JSON 序列化器：default typing + 白名单校验（防多态反序列化 gadget 面——
     * 仅放行本项目前缀与 JDK 集合/时间/基础类型）。
     */
    static GenericJacksonJsonRedisSerializer forgeJsonRedisSerializer() {
        PolymorphicTypeValidator validator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("io.github.luckybearbear")
                .allowIfSubType("java.util")
                .allowIfSubType("java.lang")
                .allowIfSubType("java.time")
                .build();
        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(validator)
                .build();
    }
}
