package io.github.luckybearbear.forge.demo;

import io.github.luckybearbear.forge.demo.maintenance.app.MaintenanceService;
import io.github.luckybearbear.forge.demo.maintenance.domain.MaintenanceOrderJpaEntity;
import io.github.luckybearbear.forge.demo.maintenance.domain.MaintenanceOrderRepository;
import io.github.luckybearbear.forge.demo.mold.api.RegisterMoldRequest;
import io.github.luckybearbear.forge.demo.mold.api.RequestRepairRequest;
import io.github.luckybearbear.forge.demo.mold.app.MoldService;
import io.github.luckybearbear.forge.demo.mold.domain.MoldJpaEntity;
import io.github.luckybearbear.forge.demo.mold.domain.MoldRepository;
import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.platform.test.AbstractIntegrationTest;
import io.github.luckybearbear.forge.platform.test.TenantTestSupport;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task 11 验收：Testcontainers 真实 PG 上——
 * A/B 双租户隔离（@TenantId 判别式过滤）、平台视角恒空、报修 → 异步维修单
 * （@ApplicationModuleListener 事务提交后异步投递，Awaitility 等待）落库且租户隔离、
 * event_publication 登记表非空且 COMPLETED（spec §6.1/§10）。
 *
 * <p>认证接入前的租户载体：TenantTestSupport 显式 set/clear（brief 允许；TokenAuthFilter 填充为 2b 范围）。
 * 与 DemoFlowIntegrationTest（RANDOM_PORT，HTTP 流）分属不同 Spring 上下文键，互不共享缓存上下文。
 */
@SpringBootTest(classes = ForgeDemoApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DemoTenantIsolationIntegrationTest extends AbstractIntegrationTest {

    @Autowired MoldService moldService;
    @Autowired MaintenanceService maintenanceService;
    @Autowired MoldRepository moldRepository;
    @Autowired MaintenanceOrderRepository maintenanceOrderRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void dualTenantIsolationAndEmptyPlatformViewOnMold() {
        var moldA = TenantTestSupport.runAsResult("tenant-a",
                () -> moldService.register(new RegisterMoldRequest("A-模具", "两腔")));
        var moldB = TenantTestSupport.runAsResult("tenant-b",
                () -> moldService.register(new RegisterMoldRequest("B-模具", "四腔")));

        // A/B 各自只见自己的模具（Hibernate 判别式多租户自动过滤）
        assertThat(TenantTestSupport.runAsResult("tenant-a", () -> moldRepository.findAll()))
                .extracting(MoldJpaEntity::getName)
                .containsExactly("A-模具");
        assertThat(TenantTestSupport.runAsResult("tenant-b", () -> moldRepository.findAll()))
                .extracting(MoldJpaEntity::getName)
                .containsExactly("B-模具");

        // A 按 id 查 B 的模具 → 同库跨租户不可见（MOLD-0404 + 404）
        assertThatThrownBy(() -> TenantTestSupport.runAsResult("tenant-a",
                () -> moldService.findById(moldB.id())))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode().code()).isEqualTo("MOLD-0404");
                    assertThat(ex.httpStatus()).isEqualTo(404);
                });

        // 平台视角恒空（spec §6.1：ignore/无租户 → "0"，租户表恒空）
        assertThat(TenantTestSupport.runAsPlatform(() -> moldRepository.findAll())).isEmpty();

        // 物理双行均落库：隔离靠过滤而非丢数据（绕过 Hibernate 过滤的裸 SQL）
        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mold", Integer.class);
        assertThat(rows).isEqualTo(2);
        assertThat(moldA.id()).isNotBlank();
        assertThat(moldB.id()).isNotBlank();
    }

    @Test
    void repairFlowsIntoTenantIsolatedAsyncOrderAndCompletedEventPublication() {
        String moldId = TenantTestSupport.runAsResult("tenant-a",
                () -> moldService.register(new RegisterMoldRequest("A-模具", "两腔"))).id();
        TenantTestSupport.runAs("tenant-a",
                () -> moldService.requestRepair(moldId, new RequestRepairRequest("导柱磨损")));

        // 异步维修单落库（@ApplicationModuleListener：事务提交后异步投递），契约查询在监听线程
        // 恢复的租户上下文内补全模具名
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var orders = TenantTestSupport.runAsResult("tenant-a",
                    () -> maintenanceService.findByMoldId(moldId));
            assertThat(orders).hasSize(1);
            assertThat(orders.get(0).moldName()).isEqualTo("A-模具");
            assertThat(orders.get(0).description()).isEqualTo("导柱磨损");
            assertThat(orders.get(0).status()).isEqualTo("OPEN");
        });

        // B 视角不可见 A 的维修单；平台视角恒空
        assertThat(TenantTestSupport.runAsResult("tenant-b",
                () -> maintenanceService.findByMoldId(moldId))).isEmpty();
        assertThat(TenantTestSupport.runAsPlatform(
                () -> maintenanceOrderRepository.findAll())).isEmpty();

        // 物理行 tenant_id 归属 tenant-a（事件 payload 租户在监听器入口恢复）
        String tenantId = jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM maintenance_order WHERE mold_id = ?", String.class, moldId);
        assertThat(tenantId).isEqualTo("tenant-a");

        // event_publication 登记表非空且 COMPLETED（投递完成标记 completion_date 非空）
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Integer total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM event_publication", Integer.class);
            Integer completed = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM event_publication WHERE completion_date IS NOT NULL",
                    Integer.class);
            assertThat(total).isGreaterThan(0);
            assertThat(completed).isEqualTo(total);
        });
    }
}
