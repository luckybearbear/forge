package io.github.luckybearbear.forge.starter.defaults;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/** 低优先级默认值：应用自身配置可覆盖。 */
public class ForgeDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("management.endpoints.web.exposure.include", "health,info");
        // spec §6.6：prod profile 下默认关闭 API 文档（低优先级，应用可显式开启覆盖）
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equals(profile)) {
                defaults.put("springdoc.api-docs.enabled", false);
                defaults.put("springdoc.swagger-ui.enabled", false);
                break;
            }
        }
        environment.getPropertySources().addLast(new MapPropertySource("forgeDefaults", defaults));
    }
}
