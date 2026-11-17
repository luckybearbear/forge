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
 * <p>Boot 4 实证：容器 ConnectionDetailsFactory 按技术模块分布（JDBC 的在 spring-boot-jdbc，
 * Redis 的在 spring-boot-data-redis，4.1.2 BOM 无 spring-boot-redis 之名）——本基座 compile 依赖
 * 两工件，两容器均带 {@code @ServiceConnection}：含对应技术栈的测试应用直接得到真连接；
 * 不含的不受影响（RedisAutoConfiguration/DataSourceAutoConfiguration 以技术类/连接池存在为条件，
 * Task 10 实证：data-starter 回归绿、security-starter 无 JDBC 栈不装配 DataSource）。
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    /** security-starter 等含 Redis 技术栈的测试经 @ServiceConnection 直连本容器。 */
    @Container
    @ServiceConnection
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);
}
