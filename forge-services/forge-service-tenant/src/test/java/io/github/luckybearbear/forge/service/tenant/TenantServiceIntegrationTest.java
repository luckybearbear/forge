package io.github.luckybearbear.forge.service.tenant;

import io.github.luckybearbear.forge.contract.tenant.TenantCreated;
import io.github.luckybearbear.forge.contract.tenant.TenantDisabled;
import io.github.luckybearbear.forge.contract.tenant.TenantDTO;
import io.github.luckybearbear.forge.contract.tenant.TenantErrorCodes;
import io.github.luckybearbear.forge.contract.tenant.TenantQueryService;
import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.platform.test.AbstractIntegrationTest;
import io.github.luckybearbear.forge.platform.test.TenantTestSupport;
import io.github.luckybearbear.forge.service.tenant.app.TenantPackageService;
import io.github.luckybearbear.forge.service.tenant.app.TenantService;
import io.github.luckybearbear.forge.service.tenant.domain.TenantEntity;
import io.github.luckybearbear.forge.service.tenant.domain.TenantRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * service-tenant 范式样板集成测试（真实 PG 容器）：create→findByCode/Id、重复编码 TENANT-0409、
 * disable→状态+TenantDisabled 事件、幂等停用/重新启用、**全局表不适用租户判别式与平台写守护**、
 * 契约 TenantQueryService bean 暴露、套餐 CRUD 与软删除后编码复用。
 *
 * <p>事件断言：服务 jar 无监听者（PublishedEvents 是 modulith-test 的设施）——测试配置类内
 * 注册普通 @EventListener bean 同步捕获（事件经 ApplicationEventPublisher 于业务事务内发布）。
 * recorder 为全类共享（同一 Spring 上下文），事件计数断言一律按 tenant code/id 过滤。
 */
@SpringBootTest(classes = TenantServiceIntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TenantServiceAutoConfiguration.class)
class TenantServiceIntegrationTest extends AbstractIntegrationTest {

    /** @SpringBootApplication 扫描本测试包（= 服务包根）——AutoConfiguration 类经 imports 文件由
     *  AutoConfigurationExcludeFilter 排除、不被重复注册；显式 @Import 装配样板双路径（imports 文件
     *  与 @Import 同趋一类，ConfigurationClassParser 合并不重复处理）。 */
    @SpringBootApplication
    static class TestApp {
    }

    /** 契约事件记录器：普通 @EventListener（同步、无 modulith 依赖）。 */
    @Component
    static class EventRecorder {

        final List<TenantCreated> created = new CopyOnWriteArrayList<>();
        final List<TenantDisabled> disabled = new CopyOnWriteArrayList<>();

        @EventListener
        void on(TenantCreated event) {
            created.add(event);
        }

        @EventListener
        void on(TenantDisabled event) {
            disabled.add(event);
        }
    }

    @Autowired TenantService tenantService;
    @Autowired TenantPackageService tenantPackageService;
    @Autowired TenantQueryService tenantQueryService; // 契约 bean（模块根包 TenantQueryServiceImpl）
    @Autowired TenantRepository tenantRepository;
    @Autowired EventRecorder recorder;

    private List<TenantCreated> createdEvents(String code) {
        return recorder.created.stream().filter(e -> e.code().equals(code)).toList();
    }

    private List<TenantDisabled> disabledEvents(String tenantId) {
        return recorder.disabled.stream().filter(e -> e.tenantId().equals(tenantId)).toList();
    }

    @Test
    void createThenFindByCodeAndIdViaServiceAndContract() {
        Instant expiredAt = Instant.now().plus(Duration.ofDays(365)).truncatedTo(ChronoUnit.MICROS);
        TenantDTO created = tenantService.create("acme-corp", "Acme 公司", null, expiredAt);

        assertThat(created.id()).isNotBlank();
        assertThat(created.code()).isEqualTo("acme-corp");
        assertThat(created.name()).isEqualTo("Acme 公司");
        assertThat(created.status()).isEqualTo("INIT"); // 创建即 INIT（enable 显式激活）
        assertThat(created.expiredAt()).isEqualTo(expiredAt);

        // 服务查询（readOnly 事务，DB 往返后字段一致）
        assertThat(tenantService.findByCode("acme-corp")).isEqualTo(created);
        assertThat(tenantService.findById(created.id()).code()).isEqualTo("acme-corp");

        // 契约跨模块查询：模块根包 TenantQueryServiceImpl 暴露为 TenantQueryService bean
        Optional<TenantDTO> byId = tenantQueryService.findById(created.id());
        Optional<TenantDTO> byCode = tenantQueryService.findByCode("acme-corp");
        assertThat(byId).contains(created);
        assertThat(byCode).contains(created);

        // TenantCreated 事件（业务事务内发布，测试 listener 同步捕获）
        assertThat(createdEvents("acme-corp")).hasSize(1);
        TenantCreated event = createdEvents("acme-corp").getFirst();
        assertThat(event.tenantId()).isEqualTo(created.id());
        assertThat(event.tenantName()).isEqualTo("Acme 公司");
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void duplicateCodeRejectedWithTenant0409() {
        tenantService.create("dupe-corp", "第一家", null, null);

        assertThatThrownBy(() -> tenantService.create("dupe-corp", "第二家", null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.errorCode()).isEqualTo(TenantErrorCodes.TENANT_CODE_DUPLICATED);
                    assertThat(be.errorCode().code()).isEqualTo("TENANT-0409");
                    assertThat(be.httpStatus()).isEqualTo(409);
                })
                .hasMessageContaining("dupe-corp");
        // 第二次 create 未成功：仅一条 TenantCreated（第一家）
        assertThat(createdEvents("dupe-corp")).hasSize(1);
        assertThat(createdEvents("dupe-corp").getFirst().tenantName()).isEqualTo("第一家");
    }

    @Test
    void disablePublishesEventAndIsIdempotentThenEnableRestores() {
        TenantDTO created = tenantService.create("lifecycle-corp", "生命周期公司", null, null);
        assertThat(disabledEvents(created.id())).isEmpty();

        TenantDTO disabled = tenantService.disable(created.id());
        assertThat(disabled.status()).isEqualTo("DISABLED");
        assertThat(disabledEvents(created.id())).hasSize(1);
        assertThat(disabledEvents(created.id()).getFirst().occurredAt()).isNotNull();

        // 幂等停用：再次 disable 返回当前状态，不重复发事件（README 语义约定）
        TenantDTO again = tenantService.disable(created.id());
        assertThat(again.status()).isEqualTo("DISABLED");
        assertThat(disabledEvents(created.id())).hasSize(1);

        // 重新启用
        TenantDTO enabled = tenantService.enable(created.id());
        assertThat(enabled.status()).isEqualTo("ENABLED");
        assertThat(tenantService.findByCode("lifecycle-corp").status()).isEqualTo("ENABLED");
    }

    @Test
    void unknownTenantRejectedWithTenant0404AndContractReturnsEmpty() {
        assertThatThrownBy(() -> tenantService.findById("no-such-tenant"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    assertThat(((BusinessException) ex).errorCode())
                            .isEqualTo(TenantErrorCodes.TENANT_NOT_FOUND);
                    assertThat(((BusinessException) ex).httpStatus()).isEqualTo(404);
                })
                .hasMessageContaining("no-such-tenant");
        assertThatThrownBy(() -> tenantService.findByCode("no-such-code"))
                .isInstanceOf(BusinessException.class);
        assertThat(tenantQueryService.findById("no-such-tenant")).isEmpty();
        assertThat(tenantQueryService.findByCode("no-such-code")).isEmpty();
    }

    /**
     * 全局表范式实证：tenant/tenant_package 无 @TenantId（extends AbstractAuditableEntity）——
     * 平台视角（ignore）写入不受写守护约束（守护只挂 AbstractTenantEntity，防的是 tenant_id='0' 黑洞）；
     * 任意租户视角读到同一行（判别式过滤只作用于租户表）；审计基建（createdBy/createdAt）照常生效。
     */
    @Test
    void globalTableIgnoresTenantDiscriminatorAndPlatformWriteGuard() {
        TenantDTO created = TenantTestSupport.runAsPlatform(() ->
                tenantService.create("global-corp", "平台视角建租户", null, null));
        assertThat(created.status()).isEqualTo("INIT");

        TenantTestSupport.runAs("t-view-a", () ->
                assertThat(tenantService.findByCode("global-corp").name()).isEqualTo("平台视角建租户"));
        TenantTestSupport.runAs("t-view-b", () ->
                assertThat(tenantService.findById(created.id()).code()).isEqualTo("global-corp"));

        // 审计自动填充对全局表同样生效（无认证上下文 → system）
        Optional<TenantEntity> reloaded = tenantRepository.findById(created.id());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getCreatedBy()).isEqualTo("system");
        assertThat(reloaded.get().getCreatedAt()).isNotNull();
    }

    /**
     * 全量列表（GET /admin-api/tenants，Task 4 后端补齐）：全局表无租户语义——任意视角同一行集；
     * 按 code 升序稳定输出（前端全量列表前端过滤，顺序稳定=分页/快照可比）。
     * 本测试以 demo seed 同款两租户（t-mfg/t-mold）作 contained 断言（类内共享库，只验在场+有序）。
     */
    @Test
    void listReturnsAllTenantsStablySortedByCode() {
        TenantDTO mfg = tenantService.create("t-mfg", "精密制造", null, null);
        TenantDTO mold = tenantService.create("t-mold", "模具车间", null, null);

        List<TenantDTO> all = TenantTestSupport.runAsResult("list-viewer", tenantService::list);

        assertThat(all).extracting(TenantDTO::id).contains(mfg.id(), mold.id());
        assertThat(all).extracting(TenantDTO::id).contains(mold.id(), mfg.id());
        // 整表按 code 升序（稳定序：排序键唯一——code 活跃行唯一）
        List<String> codes = all.stream().map(TenantDTO::code).toList();
        assertThat(codes).isSorted();
        assertThat(codes.stream().distinct().count()).isEqualTo(codes.size());
    }

    /**
     * 软删除（DELETE /admin-api/tenants/{id}，Task 4 后端补齐）：删后 findById/findByCode 均
     * TENANT-0404（软删行被 Hibernate @SoftDelete 过滤）；编码可复用（partial unique index 语义）；
     * 删除发布 TenantDisabled（复用停用事件——语义="租户不可用"，存量 token 即时下线，README 登记）；
     * 不存在（含已删）→ TENANT-0404（幂等静默会掩盖管理端误操作，取显式 404）。
     */
    @Test
    void deleteSoftDeletesPublishesOfflineEventAndCodeBecomesReusable() {
        TenantDTO created = tenantService.create("doomed-corp", "待删公司", null, null);
        assertThat(disabledEvents(created.id())).isEmpty();

        tenantService.delete(created.id());

        assertThatThrownBy(() -> tenantService.findById(created.id()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    assertThat(((BusinessException) ex).errorCode())
                            .isEqualTo(TenantErrorCodes.TENANT_NOT_FOUND);
                    assertThat(((BusinessException) ex).httpStatus()).isEqualTo(404);
                });
        assertThatThrownBy(() -> tenantService.findByCode("doomed-corp"))
                .isInstanceOf(BusinessException.class);
        assertThat(tenantQueryService.findByCode("doomed-corp")).isEmpty();

        // 删除即下线：TenantDisabled 事件恰好一次（security-starter TenantDisabledListener
        // 吊销该租户全部存量 token——删除的租户其会话不可信，与停用同向）
        assertThat(disabledEvents(created.id())).hasSize(1);

        // 编码可复用（软删行不占码）：同码重建得新 id；再次删除旧 id → 404（已删即不存在）
        TenantDTO reborn = tenantService.create("doomed-corp", "重生公司", null, null);
        assertThat(reborn.id()).isNotEqualTo(created.id());
        assertThat(tenantService.findByCode("doomed-corp").name()).isEqualTo("重生公司");
        assertThatThrownBy(() -> tenantService.delete(created.id()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(created.id());
        // 复活后的新行未受旧 id 删除影响；事件计数不变（针对旧 tenantId）
        assertThat(disabledEvents(created.id())).hasSize(1);
        assertThat(disabledEvents(reborn.id())).isEmpty();
    }

    @Test
    void updateAssignsPackageAndPackageCrudSoftDeletes() {
        var pkg = tenantPackageService.create("starter-pkg", "初创套餐", 10, "基础套餐");
        assertThat(pkg.id()).isNotBlank();
        assertThat(tenantPackageService.findByCode("starter-pkg")).contains(pkg);

        var updated = tenantPackageService.update(pkg.id(), "成长套餐", 50, null);
        assertThat(updated.name()).isEqualTo("成长套餐");
        assertThat(updated.maxUsers()).isEqualTo(50);
        assertThat(updated.remark()).isEqualTo("基础套餐"); // null 字段不改（部分更新）

        // 租户绑定套餐 + 名称/有效期更新
        Instant expiredAt = Instant.now().plus(Duration.ofDays(30)).truncatedTo(ChronoUnit.MICROS);
        TenantDTO created = tenantService.create("bound-corp", "绑定套餐公司", null, null);
        TenantDTO bound = tenantService.update(created.id(), "绑定套餐公司2.0", pkg.id(), expiredAt);
        assertThat(bound.packageId()).isEqualTo(pkg.id());
        assertThat(bound.name()).isEqualTo("绑定套餐公司2.0");
        assertThat(bound.expiredAt()).isEqualTo(expiredAt);

        // 套餐软删除：查询过滤（Hibernate 类级 @SoftDelete），编码可复用（partial unique index WHERE deleted = FALSE）
        tenantPackageService.delete(pkg.id());
        assertThat(tenantPackageService.findByCode("starter-pkg")).isEmpty();
        var reborn = tenantPackageService.create("starter-pkg", "编码复用套餐", null, null);
        assertThat(reborn.id()).isNotEqualTo(pkg.id());

        // 对外按编码查询（控制器路径）：缺失语义在 service 层（FORGE-0404/404）——控制器薄层范式
        assertThatThrownBy(() -> tenantPackageService.findByCodeRequired("no-such-pkg"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no-such-pkg");
    }
}
