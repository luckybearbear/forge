package io.github.luckybearbear.forge.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Modulith 根包：io.github.luckybearbear.forge.demo。
 *
 * <p>双端前缀路由（controller.app/admin 包 → /app-api、/admin-api，spec §6.5）由
 * forge-spring-boot-starter 的 ForgeWebAutoConfiguration 自动装配（Task 11 修复轮起），
 * 应用侧无需显式接线；demo 集成测试的 /app-api 断言即对该装配路径的判别性验证。
 *
 * <p><b>显式 JPA 扫描（Task 8 组装裁定）</b>：四服务自动配置各自声明 {@code @EntityScan}/
 * {@code @EnableJpaRepositories}（限自身 domain 包）后，Boot 的 JpaRepositoriesAutoConfiguration
 * 按 {@code @ConditionalOnMissingBean} 整体退避——默认"扫应用根包注册仓储/实体"不再发生，demo
 * 自己的 MoldRepository/MaintenanceOrderRepository 与实体会丢失（组装冒烟实证：
 * NoSuchBeanDefinitionException）。各处声明经 Spring Data 的注册器<b>累加</b>共存，故消费方
 * 引入任一带 JPA 的服务时须同时声明自身扫描范围（与服务的 domain 限定互不覆盖）。
 */
@SpringBootApplication
@EntityScan(basePackages = "io.github.luckybearbear.forge.demo")
@EnableJpaRepositories(basePackages = "io.github.luckybearbear.forge.demo")
public class ForgeDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForgeDemoApplication.class, args);
    }
}
