package io.github.luckybearbear.forge.starter;

import io.github.luckybearbear.forge.platform.web.ForgeApiPrefixRequestMappingHandlerMapping;
import io.github.luckybearbear.forge.starter.web.ForgeWebAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebMvcRegistrations 覆盖治理（2b Step 3，上下文切片，无服务器）：
 *
 * <ul>
 *   <li>缺省：forge 的条件注册器在——双端前缀开箱即用（端到端见 StarterApiPrefixTest）</li>
 *   <li>应用自带 WebMvcRegistrations：forge 让位（@ConditionalOnMissingBean）——用户注册器
 *       独占生效；此前 @Import 无条件注册形成双候选，Boot 4 getIfUnique() 解析为 null，
 *       双方静默失效（终审 Important 遗留）</li>
 * </ul>
 */
class WebMvcRegistrationsOverrideTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ForgeWebAutoConfiguration.class));

    @Test
    void defaultRegistersForgePrefixRegistrations() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(WebMvcRegistrations.class);
            assertThat(context.getBean(WebMvcRegistrations.class).getRequestMappingHandlerMapping())
                    .isInstanceOf(ForgeApiPrefixRequestMappingHandlerMapping.class);
        });
    }

    @Test
    void userProvidedRegistrationTakesPrecedence() {
        runner.withBean("userWebMvcRegistrations", WebMvcRegistrations.class, () -> new WebMvcRegistrations() {
                    @Override
                    public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
                        return new RequestMappingHandlerMapping(); // 用户裸注册器（无 forge 前缀）
                    }
                })
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("forgeApiPrefixWebMvcRegistrations");
                    assertThat(context).hasSingleBean(WebMvcRegistrations.class); // 独占，无双候选
                    assertThat(context.getBean(WebMvcRegistrations.class).getRequestMappingHandlerMapping())
                            .isNotInstanceOf(ForgeApiPrefixRequestMappingHandlerMapping.class);
                });
    }
}
