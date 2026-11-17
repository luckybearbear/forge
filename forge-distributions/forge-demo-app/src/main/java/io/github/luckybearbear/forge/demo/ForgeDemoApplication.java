package io.github.luckybearbear.forge.demo;

import io.github.luckybearbear.forge.platform.web.ForgeApiPrefixConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Modulith 根包：io.github.luckybearbear.forge.demo。
 *
 * <p>显式接入 platform-web 双端前缀映射（controller.app/admin 包 → /app-api、/admin-api，spec §6.5）：
 * 机制位于 platform-web 模块内、不由 starter 自动装配，应用侧 @Import 生效（Task 11 记录，starter 全局装配留待后续）。
 */
@SpringBootApplication
@Import(ForgeApiPrefixConfiguration.class)
public class ForgeDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForgeDemoApplication.class, args);
    }
}
