package io.github.luckybearbear.forge.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Modulith 根包：io.github.luckybearbear.forge.demo。
 *
 * <p>双端前缀路由（controller.app/admin 包 → /app-api、/admin-api，spec §6.5）由
 * forge-spring-boot-starter 的 ForgeWebAutoConfiguration 自动装配（Task 11 修复轮起），
 * 应用侧无需显式接线；demo 集成测试的 /app-api 断言即对该装配路径的判别性验证。
 */
@SpringBootApplication
public class ForgeDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForgeDemoApplication.class, args);
    }
}
