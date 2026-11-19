package io.github.luckybearbear.forge.starter.data;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/** 数据层低优先级默认值：应用自身配置可覆盖（与 forge-spring-boot-starter 的 ForgeDefaults 同模式）。 */
public class ForgeDataDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new HashMap<>();
        // Modulith 事件发布登记表（EPP）建表默认开启——starter 已带 Flyway 规范，登记表随库初始化
        defaults.put("spring.modulith.events.jdbc.schema-initialization.enabled", true);
        environment.getPropertySources().addLast(new MapPropertySource("forgeDataDefaults", defaults));
    }
}
