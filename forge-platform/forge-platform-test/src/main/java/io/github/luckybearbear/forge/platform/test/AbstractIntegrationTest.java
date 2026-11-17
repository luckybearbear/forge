package io.github.luckybearbear.forge.platform.test;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 集成测试基类：PG + Redis 真实容器；无 Docker 环境 skip（disabledWithoutDocker）。
 *
 * <p>Testcontainers 2.x 改名件（org.testcontainers:testcontainers-postgresql 等）类包名与 1.x 相同
 * （org.testcontainers.containers.PostgreSQLContainer，jar 内另有新包 org.testcontainers.postgresql 别名）。
 *
 * <p>Boot 4 实证：容器 ConnectionDetailsFactory 按技术模块分布（JDBC 的在 spring-boot-jdbc），
 * Redis 的工厂随 spring-boot-redis 模块——本基座不强制 Redis 在 classpath，故 REDIS 容器不带
 * {@code @ServiceConnection}（否则无 Redis 模块的测试直接 ConnectionDetailsNotFoundException）；
 * 需要 Redis 连接的下游测试在自身 classpath 含 spring-boot-redis 时另行注册。
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    /** 统一基座：data-starter 测试未消费 Redis，保留供 security-starter/demo 复用。 */
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);
}
