package io.github.luckybearbear.forge.starter.security;

import io.github.luckybearbear.forge.platform.security.token.InMemoryTokenStore;
import io.github.luckybearbear.forge.platform.security.token.TokenStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TokenStore 内存降级路径（上下文切片，无 Redis 容器）：
 * Redis 不在类路径（FilteredClassLoader 隐藏 {@link RedisOperations}）时，
 * {@code RedisTokenStoreConfiguration} 的 @ConditionalOnClass 退避，
 * {@link ForgeTokenStoreFallbackAutoConfiguration} 兜底 InMemoryTokenStore + 启动 warn。
 *
 * <p>SecurityStarterIntegrationTest 只覆盖 Redis 主路径（RedisTokenStore 在场），
 * 本测试补齐 Task 10 条件评估报告实证过的降级分支。
 *
 * <p>同时隐藏 {@link HttpSecurity}：安全链切片（SecurityChainConfiguration）依赖
 * servlet 安全栈（HttpSecurityConfiguration 需 web 应用上下文 + ObjectPostProcessor
 * 等基建），与本测试无关——其 @ConditionalOnClass 按"类缺失即退避"设计语义生效，
 * 链路装配已由集成测试覆盖。@TenantIgnore 切面 bean 无基建依赖，正常注册不受影响。
 *
 * <p>FQN 实证（jar tf spring-boot-test 4.1.2，Boot 4 未迁包）：
 * ApplicationContextRunner/FilteredClassLoader/OutputCaptureExtension 分别位于
 * {@code o.s.b.test.context.runner} / {@code o.s.b.test.context} / {@code o.s.b.test.system}。
 */
@ExtendWith(OutputCaptureExtension.class)
class ForgeTokenStoreFallbackTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withClassLoader(new FilteredClassLoader(RedisOperations.class, HttpSecurity.class))
            .withConfiguration(AutoConfigurations.of(
                    ForgeSecurityAutoConfiguration.class,
                    ForgeTokenStoreFallbackAutoConfiguration.class));

    @Test
    void redisAbsentFallsBackToInMemoryTokenStoreWithWarn(CapturedOutput output) {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(TokenStore.class);
            assertThat(context.getBean(TokenStore.class)).isInstanceOf(InMemoryTokenStore.class);
            // 降级必须可观测：warn 文案取自实现（重启即全员下线、多实例不共享，生产必须 Redis）
            assertThat(output).contains("falling back to InMemoryTokenStore");
        });
    }
}
