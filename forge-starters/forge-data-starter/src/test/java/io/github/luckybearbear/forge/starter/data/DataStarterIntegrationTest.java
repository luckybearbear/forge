package io.github.luckybearbear.forge.starter.data;

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
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * THE SPIKE（全计划最关键实证）：Testcontainers 真实 PG 上验证双租户隔离、
 * 平台视角恒空、审计自动填充与软删除过滤（spec §6.1/§6.3）。
 */
@SpringBootTest(classes = DataStarterIntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DataStarterIntegrationTest extends AbstractIntegrationTest {

    @SpringBootApplication
    static class TestApp {}

    @Autowired TestTenantRepository repository;
    @Autowired JdbcTemplate jdbcTemplate;

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
