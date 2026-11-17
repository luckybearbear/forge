package io.github.luckybearbear.forge.starter.data;

import io.github.luckybearbear.forge.platform.security.TenantIgnore;
import io.github.luckybearbear.forge.platform.test.AbstractIntegrationTest;
import io.github.luckybearbear.forge.platform.test.TenantTestSupport;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * THE SPIKE（全计划最关键实证）：Testcontainers 真实 PG 上验证双租户隔离、
 * 平台视角恒空、审计自动填充与软删除过滤（spec §6.1/§6.3）；
 * 2b 前置加固追加：平台视角写守护（@PrePersist fail-fast）、@TenantIgnore+@Transactional
 * 组合（@Order(0) 切面先于事务）、SecurityChain 非 Servlet 环境优雅退避。
 */
@SpringBootTest(classes = DataStarterIntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DataStarterIntegrationTest extends AbstractIntegrationTest {

    @SpringBootApplication
    static class TestApp {

        /** @TenantIgnore + @Transactional 组合实证载体（2b Step 5）：切面 @Order(0) 须先于事务拦截器。 */
        @Service
        static class TenantViewService {

            private final TestTenantRepository repository;

            TenantViewService(TestTenantRepository repository) {
                this.repository = repository;
            }

            @Transactional
            @TenantIgnore
            List<TestTenantEntity> platformViewUnderTransaction() {
                return repository.findAll(); // 会话开启时 ignore 已置位 → 判别式 "0" → 恒空
            }

            @Transactional
            List<TestTenantEntity> tenantViewUnderTransaction() {
                return repository.findAll(); // 对照组：正常租户判别式
            }
        }
    }

    @Autowired TestTenantRepository repository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TestApp.TenantViewService tenantViewService;
    @Autowired ApplicationContext applicationContext;

    @Test
    void tenantIsolationAutoApplied() {
        TestTenantEntity a = TenantTestSupport.runAsResult("t-a",
                () -> repository.save(new TestTenantEntity("A-data")));
        TenantTestSupport.runAs("t-b", () -> repository.save(new TestTenantEntity("B-data")));

        List<TestTenantEntity> seenByA = TenantTestSupport.runAsResult("t-a", () -> repository.findAll());
        List<TestTenantEntity> seenByB = TenantTestSupport.runAsResult("t-b", () -> repository.findAll());
        List<TestTenantEntity> platformView = TenantTestSupport.runAsPlatform(() -> repository.findAll());

        assertThat(seenByA).extracting(TestTenantEntity::getName).containsExactly("A-data");
        assertThat(seenByB).extracting(TestTenantEntity::getName).containsExactly("B-data");
        assertThat(platformView).isEmpty(); // 平台视角恒空（spec §6.1）
        assertThat(a.getCreatedAt()).isNotNull();      // 审计自动填充
        assertThat(a.getCreatedBy()).isEqualTo("system"); // 无认证上下文 → system
        assertThat(a.getTenantId()).isEqualTo("t-a");  // @TenantId 注入
    }

    @Test
    void softDeleteFiltersQueries() {
        TestTenantEntity saved = TenantTestSupport.runAsResult("t-a",
                () -> repository.save(new TestTenantEntity("to-delete")));
        TenantTestSupport.runAs("t-a", () -> repository.deleteById(saved.getId()));
        List<TestTenantEntity> rest = TenantTestSupport.runAsResult("t-a", () -> repository.findAll());
        assertThat(rest).isEmpty(); // 查询过滤：软删除行不可见
        // 语义实证（防物理 DELETE 假绿）：DB 里行仍在，仅 deleted=true——
        // @SQLDelete 在 @MappedSuperclass 上不被 Hibernate 7 读取（EntityBinder.bindCustomSql 不遍历基类），
        // {h-table} 占位符亦不存在（7.4.13 jar 内 0 命中）；最终机制为 Hibernate 原生 @SoftDelete。
        Boolean deletedInDb = TenantTestSupport.runAsResult("t-a", () -> jdbcTemplate.queryForObject(
                "SELECT deleted FROM data_starter_test_entity WHERE id = ?", Boolean.class, saved.getId()));
        assertThat(deletedInDb).isTrue();
    }

    @Test
    void platformViewWriteRejectedWhileTenantContextWriteSucceeds() {
        // 平台视角写 = 数据黑洞（行落 tenant_id='0'，任何租户视角永不可见）：装配的守护在
        // @PrePersist fail-fast 拒绝（forge-data-starter 装配处注入 supplier::ignore）；
        // 租户 id 用独占值（t-guard）：tenantIsolationAutoApplied 的 containsExactly 不受执行顺序影响
        assertThatThrownBy(() -> TenantTestSupport.runAsPlatform(
                () -> repository.save(new TestTenantEntity("platform-write"))))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause().hasMessageContaining("platform view");
        // 对照组：租户上下文下正常写入（守护只拦平台视角，不拦正常多租户写入）
        TestTenantEntity saved = TenantTestSupport.runAsResult("t-guard",
                () -> repository.save(new TestTenantEntity("tenant-write")));
        assertThat(saved.getTenantId()).isEqualTo("t-guard");
    }

    @Test
    void tenantIgnoreOnTransactionalMethodYieldsPlatformView() {
        TenantTestSupport.runAs("t-combo", () -> repository.save(new TestTenantEntity("combo-row")));

        // @Order(0) 切面先于事务拦截器：事务开启→会话开启时 ignore 已置位，方法内查询
        // 判别式解析 "0"（租户表恒空）→ 恒空；若切面晚于事务，会话按进入前状态固化 → 返回本租户行
        List<TestTenantEntity> platformView = TenantTestSupport.runAsResult("t-combo",
                () -> tenantViewService.platformViewUnderTransaction());
        assertThat(platformView).isEmpty();

        // 对照组：同租户普通事务方法可见（恒空来自平台视角，而非数据缺失）
        List<TestTenantEntity> tenantView = TenantTestSupport.runAsResult("t-combo",
                () -> tenantViewService.tenantViewUnderTransaction());
        assertThat(tenantView).extracting(TestTenantEntity::getName).contains("combo-row");
    }

    @Test
    void securityChainGracefullyRetreatsInNonServletApp() {
        // forge-security-starter 在测试类路径 + NONE 环境（2b Step 5 SERVLET 守卫）：
        // 无链可装是优雅退避而非启动失败——本上下文成功启动即证明，再显式断言无链
        assertThat(applicationContext.getBeansOfType(SecurityFilterChain.class)).isEmpty();
    }
}

@Entity
@Table(name = "data_starter_test_entity")
class TestTenantEntity extends io.github.luckybearbear.forge.platform.data.AbstractTenantEntity {
    @jakarta.persistence.Column(name = "name") private String name;
    @jakarta.persistence.Column(name = "recorded_at") private Instant recordedAt = Instant.now();
    TestTenantEntity(String name) { this.name = name; }
    protected TestTenantEntity() {} // JPA
    public String getName() { return name; }
}

interface TestTenantRepository extends JpaRepository<TestTenantEntity, String> {}
