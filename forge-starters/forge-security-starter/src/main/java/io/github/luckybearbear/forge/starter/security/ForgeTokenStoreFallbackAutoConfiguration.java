package io.github.luckybearbear.forge.starter.security;

import io.github.luckybearbear.forge.platform.security.token.InMemoryTokenStore;
import io.github.luckybearbear.forge.platform.security.token.TokenStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * TokenStore 兜底（dev/test）：Redis 不在类路径（或被排除）且应用未自定义 TokenStore 时，
 * 降级 InMemoryTokenStore 并启动 warn——重启即全员下线、多实例不共享，生产必须 Redis。
 *
 * <p>独立自动配置类 + {@code after} 定序的原因：与 Redis 分支同放一个类的内嵌类时，
 * 内嵌类加载顺序不受 @Order/声明序约束（Task 10 条件评估报告实证 fallback 会先于
 * redis 注册、redis 反向退避），Boot 可靠的 @ConditionalOnMissingBean 定序只在
 * 自动配置类之间成立。
 */
@AutoConfiguration(after = ForgeSecurityAutoConfiguration.class)
public class ForgeTokenStoreFallbackAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ForgeTokenStoreFallbackAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(TokenStore.class)
    TokenStore inMemoryTokenStore() {
        log.warn("Redis not on classpath or custom TokenStore absent - "
                + "falling back to InMemoryTokenStore (dev/test only; production requires Redis)");
        return new InMemoryTokenStore();
    }
}
