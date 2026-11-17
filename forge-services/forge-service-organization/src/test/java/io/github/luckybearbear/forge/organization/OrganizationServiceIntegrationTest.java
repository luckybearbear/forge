package io.github.luckybearbear.forge.organization;

import io.github.luckybearbear.forge.contract.identity.IdentityErrorCodes;
import io.github.luckybearbear.forge.contract.identity.UserDTO;
import io.github.luckybearbear.forge.contract.identity.UserQueryService;
import io.github.luckybearbear.forge.contract.organization.OrgTreeQueryService;
import io.github.luckybearbear.forge.contract.organization.OrgUnitChanged;
import io.github.luckybearbear.forge.contract.organization.OrgUnitDTO;
import io.github.luckybearbear.forge.contract.organization.OrganizationErrorCodes;
import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.platform.test.AbstractIntegrationTest;
import io.github.luckybearbear.forge.platform.test.TenantTestSupport;
import io.github.luckybearbear.forge.service.organization.OrganizationServiceAutoConfiguration;
import io.github.luckybearbear.forge.service.organization.app.OrgUnitNode;
import io.github.luckybearbear.forge.service.organization.app.OrgUnitService;
import io.github.luckybearbear.forge.service.organization.app.PositionDTO;
import io.github.luckybearbear.forge.service.organization.app.PositionService;
import io.github.luckybearbear.forge.service.organization.app.UserPositionDTO;
import io.github.luckybearbear.forge.service.organization.app.UserPositionService;
import io.github.luckybearbear.forge.service.organization.domain.OrgUnitRepository;
import io.github.luckybearbear.forge.service.organization.domain.OrgUnitType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * service-organization 集成测试（真实 PG 容器，service 方法级——不经 HTTP）：组织树 CRUD/子树查询
 * （服务 + 契约 OrgTreeQueryService 双路）、**move 环检测必红路径**（移到自身/子孙下抛 ORG-0409；
 * 脏数据环经 64 层上溯上限熔断）、删除保护（有子节点或岗位/挂岗拒绝）、挂岗跨服务契约校验
 * （UserQueryService 桩：用户缺失 IDENT-0404 / 岗位缺失 ORG-0414）、幂等挂岗/摘岗、
 * 双租户隔离（org 三表判别式互不可见）、OrgUnitChanged 三态事件。
 *
 * <p>UserQueryService 为内存桩（organization 不依赖 service-identity，契约消费即可——同 identity
 * 模块的 TenantQueryService 桩范式）；测试包位于服务根包（io.github...service.organization）之外的
 * 独立分支（...forge.organization），防多上下文组件扫描交叉泄漏（Task 4 实证）。
 */
@SpringBootTest(classes = OrganizationServiceIntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(OrganizationServiceAutoConfiguration.class)
class OrganizationServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String TENANT_CRUD = "t-org-crud";
    private static final String TENANT_MOVE = "t-org-move";
    private static final String TENANT_DELETE = "t-org-del";
    private static final String TENANT_ASSIGN = "t-org-assign";
    private static final String TENANT_ISO_A = "t-iso-a";
    private static final String TENANT_ISO_B = "t-iso-b";

    /** 独立分支包内的测试装配：@SpringBootApplication 只扫本包；桩经 @Bean 注册（不扫服务根包，
     *  服务 bean 全由 @Import(OrganizationServiceAutoConfiguration) 提供——imports 文件双路径同范式）。 */
    @SpringBootApplication
    static class TestApp {

        @Bean
        StubUserQueryService stubUserQueryService() {
            return new StubUserQueryService();
        }

        @Bean
        EventRecorder eventRecorder() {
            return new EventRecorder();
        }
    }

    /** 身份契约桩：状态可控（注册即在/未注册即缺失 → IDENT-0404）。 */
    static class StubUserQueryService implements UserQueryService {

        final Map<String, UserDTO> users = new ConcurrentHashMap<>();

        void register(String userId, String tenantId) {
            users.put(userId, new UserDTO(userId, tenantId, "user-" + userId, null, "ENABLED"));
        }

        @Override
        public Optional<UserDTO> findById(String userId) {
            return Optional.ofNullable(users.get(userId));
        }
    }

    /** 契约事件记录器（同步 @EventListener，同 tenant/identity 范式；@Bean 注册见 TestApp）。 */
    static class EventRecorder {

        final List<OrgUnitChanged> events = new CopyOnWriteArrayList<>();

        @EventListener
        void on(OrgUnitChanged event) {
            events.add(event);
        }

        List<OrgUnitChanged> by(String tenantId, String orgUnitId, String changeType) {
            return events.stream()
                    .filter(e -> tenantId.equals(e.tenantId()) && orgUnitId.equals(e.orgUnitId())
                            && changeType.equals(e.changeType()))
                    .toList();
        }

        long countBy(String tenantId, String changeType) {
            return events.stream()
                    .filter(e -> tenantId.equals(e.tenantId()) && changeType.equals(e.changeType()))
                    .count();
        }
    }

    @Autowired OrgUnitService orgUnitService;
    @Autowired PositionService positionService;
    @Autowired UserPositionService userPositionService;
    @Autowired OrgTreeQueryService orgTreeQueryService; // 契约 bean（app/OrgTreeQueryServiceImpl）
    @Autowired OrgUnitRepository orgUnitRepository;
    @Autowired StubUserQueryService users;
    @Autowired EventRecorder recorder;

    // ---------------------------------------------------------------- 树 CRUD/子树/事件

    @Test
    void orgTreeCrudSubTreeAndContractEvents() {
        OrgUnitDTO hq = TenantTestSupport.runAsResult(TENANT_CRUD,
                () -> orgUnitService.create(null, OrgUnitType.DEPT, "总部", 0));
        OrgUnitDTO rd = TenantTestSupport.runAsResult(TENANT_CRUD,
                () -> orgUnitService.create(hq.id(), OrgUnitType.DEPT, "研发部", 1));
        OrgUnitDTO expert = TenantTestSupport.runAsResult(TENANT_CRUD,
                () -> orgUnitService.create(hq.id(), OrgUnitType.GROUP, "专家组", 2));
        OrgUnitDTO backend = TenantTestSupport.runAsResult(TENANT_CRUD,
                () -> orgUnitService.create(rd.id(), OrgUnitType.DEPT, "后端组", 0));

        assertThat(hq.parentId()).isNull();
        assertThat(hq.type()).isEqualTo("DEPT");
        assertThat(rd.parentId()).isEqualTo(hq.id());

        // 单元查询：服务路 + DTO 快照等值
        assertThat(TenantTestSupport.runAsResult(TENANT_CRUD, () -> orgUnitService.findById(hq.id())))
                .isEqualTo(hq);

        // 未知单元：ORG-0404 / 404
        assertThatThrownBy(() -> TenantTestSupport.runAsResult(TENANT_CRUD,
                () -> orgUnitService.findById("no-such-unit")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.errorCode()).isEqualTo(OrganizationErrorCodes.ORG_UNIT_NOT_FOUND);
                    assertThat(be.errorCode().code()).isEqualTo("ORG-0404");
                    assertThat(be.httpStatus()).isEqualTo(404);
                })
                .hasMessageContaining("no-such-unit");

        // 全林树形：根唯一；兄弟按 (sort, id) 排序（研发部 sort=1 先于专家组 sort=2）
        List<OrgUnitNode> tree = TenantTestSupport.runAsResult(TENANT_CRUD, () -> orgUnitService.tree());
        assertThat(tree).hasSize(1);
        OrgUnitNode root = tree.getFirst();
        assertThat(root.unit().id()).isEqualTo(hq.id());
        assertThat(root.children()).extracting(n -> n.unit().name()).containsExactly("研发部", "专家组");
        assertThat(root.children().getFirst().children())
                .extracting(n -> n.unit().name()).containsExactly("后端组");

        // 子树（服务）：根开头的先序扁平快照
        List<OrgUnitDTO> sub = TenantTestSupport.runAsResult(TENANT_CRUD, () -> orgUnitService.subTree(hq.id()));
        assertThat(sub).extracting(OrgUnitDTO::id).containsExactly(hq.id(), rd.id(), backend.id(), expert.id());

        // 子树（契约跨模块双路）与未知根快照语义（空集——消费方据此失效缓存）
        assertThat(TenantTestSupport.runAsResult(TENANT_CRUD,
                () -> orgTreeQueryService.findSubTree(hq.id()))).isEqualTo(sub);
        assertThat(TenantTestSupport.runAsResult(TENANT_CRUD,
                () -> orgTreeQueryService.findSubTree("no-such-unit"))).isEmpty();

        // 部分更新：name 改、type/sort 传 null 不改；发 OrgUnitChanged(update)
        OrgUnitDTO renamed = TenantTestSupport.runAsResult(TENANT_CRUD,
                () -> orgUnitService.update(hq.id(), null, "集团总部", null));
        assertThat(renamed.name()).isEqualTo("集团总部");
        assertThat(renamed.type()).isEqualTo("DEPT");
        assertThat(renamed.sort()).isZero();

        // 事件：4 create + 1 update，tenantId 打标为事件主体租户
        assertThat(recorder.countBy(TENANT_CRUD, "create")).isEqualTo(4);
        assertThat(recorder.by(TENANT_CRUD, hq.id(), "create")).hasSize(1);
        assertThat(recorder.by(TENANT_CRUD, hq.id(), "update")).hasSize(1);
        assertThat(recorder.by(TENANT_CRUD, hq.id(), "update").getFirst().occurredAt()).isNotNull();
    }

    // ---------------------------------------------------------------- 环检测（必红路径）+ 深度上限

    @Test
    void moveDetectsCycleAndDepthCapWithOrg0409() {
        // A → B → C 链（B、C 均为 A 的子孙）
        OrgUnitDTO a = TenantTestSupport.runAsResult(TENANT_MOVE,
                () -> orgUnitService.create(null, OrgUnitType.DEPT, "A", 0));
        OrgUnitDTO b = TenantTestSupport.runAsResult(TENANT_MOVE,
                () -> orgUnitService.create(a.id(), OrgUnitType.DEPT, "B", 0));
        OrgUnitDTO c = TenantTestSupport.runAsResult(TENANT_MOVE,
                () -> orgUnitService.create(b.id(), OrgUnitType.DEPT, "C", 0));

        // 必红路径：把 A 移到其子孙 C 之下 → ORG-0409 / 409，且树形不变（事务回滚）
        assertThatThrownBy(() -> TenantTestSupport.runAsResult(TENANT_MOVE,
                () -> orgUnitService.move(a.id(), c.id())))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.errorCode()).isEqualTo(OrganizationErrorCodes.ORG_CYCLE_DETECTED);
                    assertThat(be.errorCode().code()).isEqualTo("ORG-0409");
                    assertThat(be.httpStatus()).isEqualTo(409);
                })
                .hasMessageContaining(a.id());
        OrgUnitDTO unchanged = TenantTestSupport.runAsResult(TENANT_MOVE, () -> orgUnitService.findById(a.id()));
        assertThat(unchanged.parentId()).isNull();

        // 移到自身之下：同为环（上溯首步即遇自身）
        assertThatThrownBy(() -> TenantTestSupport.runAsResult(TENANT_MOVE,
                () -> orgUnitService.move(a.id(), a.id())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(a.id());

        // 合法移动：C 提为根 → 再挂回 A（与 B 平级），发 update 事件
        OrgUnitDTO cAsRoot = TenantTestSupport.runAsResult(TENANT_MOVE,
                () -> orgUnitService.move(c.id(), null));
        assertThat(cAsRoot.parentId()).isNull();
        OrgUnitDTO cMoved = TenantTestSupport.runAsResult(TENANT_MOVE,
                () -> orgUnitService.move(c.id(), a.id()));
        assertThat(cMoved.parentId()).isEqualTo(a.id());
        List<OrgUnitNode> tree = TenantTestSupport.runAsResult(TENANT_MOVE, () -> orgUnitService.tree());
        assertThat(tree.getFirst().children()).extracting(n -> n.unit().name()).containsExactly("B", "C");

        // 移到不存在的父：ORG-0404
        assertThatThrownBy(() -> TenantTestSupport.runAsResult(TENANT_MOVE,
                () -> orgUnitService.move(c.id(), "no-such-parent")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no-such-parent");

        // 深度上限熔断（脏数据环防线）：绕过 service 直写 x1↔x2 环，上溯永不遇目标 → 64 层上限抛 ORG-0409
        OrgUnitDTO x1 = TenantTestSupport.runAsResult(TENANT_MOVE,
                () -> orgUnitService.create(null, OrgUnitType.DEPT, "x1", 0));
        OrgUnitDTO x2 = TenantTestSupport.runAsResult(TENANT_MOVE,
                () -> orgUnitService.create(x1.id(), OrgUnitType.DEPT, "x2", 0));
        TenantTestSupport.runAs(TENANT_MOVE, () -> {
            var dirty = orgUnitRepository.findById(x1.id()).orElseThrow();
            dirty.setParentId(x2.id());
            orgUnitRepository.save(dirty); // 绕过 service 校验直写环（模拟脏数据）
        });
        OrgUnitDTO target = TenantTestSupport.runAsResult(TENANT_MOVE,
                () -> orgUnitService.create(null, OrgUnitType.DEPT, "target", 0));
        assertThatThrownBy(() -> TenantTestSupport.runAsResult(TENANT_MOVE,
                () -> orgUnitService.move(target.id(), x1.id())))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    assertThat(((BusinessException) ex).errorCode())
                            .isEqualTo(OrganizationErrorCodes.ORG_CYCLE_DETECTED);
                    assertThat(((BusinessException) ex).httpStatus()).isEqualTo(409);
                })
                .hasMessageContaining("64");
        // 脏环不拖垮树查询：环上节点不可达自根，tree() 不悬挂、不含 x1/x2
        List<OrgUnitNode> after = TenantTestSupport.runAsResult(TENANT_MOVE, () -> orgUnitService.tree());
        assertThat(after).extracting(n -> n.unit().name()).containsExactly("A", "target");
    }

    // ---------------------------------------------------------------- 删除保护 + delete 事件

    @Test
    void deleteProtectionAndDeleteEvent() {
        OrgUnitDTO parent = TenantTestSupport.runAsResult(TENANT_DELETE,
                () -> orgUnitService.create(null, OrgUnitType.DEPT, "parent", 0));
        OrgUnitDTO child = TenantTestSupport.runAsResult(TENANT_DELETE,
                () -> orgUnitService.create(parent.id(), OrgUnitType.DEPT, "child", 0));
        OrgUnitDTO leaf = TenantTestSupport.runAsResult(TENANT_DELETE,
                () -> orgUnitService.create(parent.id(), OrgUnitType.GROUP, "leaf", 0));

        // 有子节点：ORG-0409 码 + 特定文案（不新增枚举）
        assertThatThrownBy(() -> TenantTestSupport.runAs(TENANT_DELETE,
                () -> orgUnitService.delete(parent.id())))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.errorCode()).isEqualTo(OrganizationErrorCodes.ORG_CYCLE_DETECTED);
                    assertThat(be.httpStatus()).isEqualTo(409);
                })
                .hasMessageContaining("存在子节点或挂岗");

        // 有岗位挂靠：同样拒绝
        PositionDTO pos = TenantTestSupport.runAsResult(TENANT_DELETE,
                () -> positionService.create(parent.id(), "经理", 0));
        assertThatThrownBy(() -> TenantTestSupport.runAs(TENANT_DELETE,
                () -> orgUnitService.delete(parent.id())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在子节点或挂岗");

        // 无子无岗：叶子可删，发 delete 事件，随后查询 404、契约子树空
        TenantTestSupport.runAs(TENANT_DELETE, () -> orgUnitService.delete(leaf.id()));
        assertThat(recorder.by(TENANT_DELETE, leaf.id(), "delete")).hasSize(1);
        assertThat(recorder.by(TENANT_DELETE, leaf.id(), "delete").getFirst().tenantId())
                .isEqualTo(TENANT_DELETE);
        assertThatThrownBy(() -> TenantTestSupport.runAs(TENANT_DELETE,
                () -> orgUnitService.findById(leaf.id())))
                .isInstanceOf(BusinessException.class);
        assertThat(TenantTestSupport.runAsResult(TENANT_DELETE,
                () -> orgTreeQueryService.findSubTree(leaf.id()))).isEmpty();

        // 岗位删除保护：有挂岗拒绝，摘岗后可删
        users.register("u-del", TENANT_DELETE);
        TenantTestSupport.runAs(TENANT_DELETE, () -> userPositionService.assign("u-del", pos.id()));
        assertThatThrownBy(() -> TenantTestSupport.runAs(TENANT_DELETE,
                () -> positionService.delete(pos.id())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("挂岗");
        TenantTestSupport.runAs(TENANT_DELETE, () -> userPositionService.unassign("u-del", pos.id()));
        TenantTestSupport.runAs(TENANT_DELETE, () -> positionService.delete(pos.id()));
        assertThatThrownBy(() -> TenantTestSupport.runAs(TENANT_DELETE,
                () -> positionService.findById(pos.id())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(pos.id());
    }

    // ---------------------------------------------------------------- 岗位 CRUD

    @Test
    void positionCrudWithOrgUnitValidation() {
        OrgUnitDTO unit = TenantTestSupport.runAsResult(TENANT_ASSIGN,
                () -> orgUnitService.create(null, OrgUnitType.DEPT, "crud-unit", 0));

        // 挂靠单元不存在：ORG-0404
        assertThatThrownBy(() -> TenantTestSupport.runAsResult(TENANT_ASSIGN,
                () -> positionService.create("no-such-unit", "经理", 0)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    assertThat(((BusinessException) ex).errorCode())
                            .isEqualTo(OrganizationErrorCodes.ORG_UNIT_NOT_FOUND);
                    assertThat(((BusinessException) ex).httpStatus()).isEqualTo(404);
                })
                .hasMessageContaining("no-such-unit");

        PositionDTO created = TenantTestSupport.runAsResult(TENANT_ASSIGN,
                () -> positionService.create(unit.id(), "经理", 1));
        assertThat(created.id()).isNotBlank();
        assertThat(created.orgUnitId()).isEqualTo(unit.id());
        assertThat(created.name()).isEqualTo("经理");
        assertThat(created.sort()).isEqualTo(1);

        assertThat(TenantTestSupport.runAsResult(TENANT_ASSIGN,
                () -> positionService.findById(created.id())).name()).isEqualTo("经理");

        // 部分更新：name 改、sort 改、orgUnitId 传 null 不改
        OrgUnitDTO other = TenantTestSupport.runAsResult(TENANT_ASSIGN,
                () -> orgUnitService.create(null, OrgUnitType.DEPT, "other-unit", 0));
        PositionDTO updated = TenantTestSupport.runAsResult(TENANT_ASSIGN,
                () -> positionService.update(created.id(), null, "高级经理", 2));
        assertThat(updated.name()).isEqualTo("高级经理");
        assertThat(updated.sort()).isEqualTo(2);
        assertThat(updated.orgUnitId()).isEqualTo(unit.id());

        // 更新换挂靠单元 + 按单元列表
        PositionDTO moved = TenantTestSupport.runAsResult(TENANT_ASSIGN,
                () -> positionService.update(created.id(), other.id(), null, null));
        assertThat(moved.orgUnitId()).isEqualTo(other.id());
        assertThat(TenantTestSupport.runAsResult(TENANT_ASSIGN,
                () -> positionService.findByOrgUnitId(unit.id()))).isEmpty();
        assertThat(TenantTestSupport.runAsResult(TENANT_ASSIGN,
                () -> positionService.findByOrgUnitId(other.id()))).containsExactly(moved);

        // 未知岗位：ORG-0414 / 404
        assertThatThrownBy(() -> TenantTestSupport.runAsResult(TENANT_ASSIGN,
                () -> positionService.findById("no-such-position")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.errorCode()).isEqualTo(OrganizationErrorCodes.ORG_POSITION_NOT_FOUND);
                    assertThat(be.errorCode().code()).isEqualTo("ORG-0414");
                    assertThat(be.httpStatus()).isEqualTo(404);
                })
                .hasMessageContaining("no-such-position");
    }

    // ---------------------------------------------------------------- 挂岗（跨服务契约校验 + 幂等）

    @Test
    void userAssignmentValidatesContractsAndIsIdempotent() {
        users.register("u-1", TENANT_ASSIGN);
        OrgUnitDTO unit = TenantTestSupport.runAsResult(TENANT_ASSIGN,
                () -> orgUnitService.create(null, OrgUnitType.DEPT, "assign-unit", 0));
        PositionDTO pos = TenantTestSupport.runAsResult(TENANT_ASSIGN,
                () -> positionService.create(unit.id(), "工程师", 0));

        // 用户不存在（跨服务契约，桩模拟 service-identity）：IDENT-0404 / 404
        assertThatThrownBy(() -> TenantTestSupport.runAsResult(TENANT_ASSIGN,
                () -> userPositionService.assign("u-404", pos.id())))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.errorCode()).isEqualTo(IdentityErrorCodes.IDENT_USER_NOT_FOUND);
                    assertThat(be.errorCode().code()).isEqualTo("IDENT-0404");
                    assertThat(be.httpStatus()).isEqualTo(404);
                })
                .hasMessageContaining("u-404");

        // 岗位不存在（本域）：ORG-0414 / 404
        assertThatThrownBy(() -> TenantTestSupport.runAsResult(TENANT_ASSIGN,
                () -> userPositionService.assign("u-1", "no-such-position")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                        .isEqualTo(OrganizationErrorCodes.ORG_POSITION_NOT_FOUND))
                .hasMessageContaining("no-such-position");

        // 挂岗成功 + 按用户查询
        UserPositionDTO assigned = TenantTestSupport.runAsResult(TENANT_ASSIGN,
                () -> userPositionService.assign("u-1", pos.id()));
        assertThat(assigned.userId()).isEqualTo("u-1");
        assertThat(assigned.positionId()).isEqualTo(pos.id());
        assertThat(TenantTestSupport.runAsResult(TENANT_ASSIGN,
                () -> userPositionService.findByUserId("u-1"))).containsExactly(assigned);

        // 幂等挂岗：同 (user, position) 再挂返回既有记录（partial unique 兜底并发）
        UserPositionDTO again = TenantTestSupport.runAsResult(TENANT_ASSIGN,
                () -> userPositionService.assign("u-1", pos.id()));
        assertThat(again.id()).isEqualTo(assigned.id());
        assertThat(TenantTestSupport.runAsResult(TENANT_ASSIGN,
                () -> userPositionService.findByUserId("u-1"))).hasSize(1);

        // 摘岗：幂等（不存在不抛）；摘后可重挂（软删行不占唯一键）
        TenantTestSupport.runAs(TENANT_ASSIGN, () -> userPositionService.unassign("u-1", pos.id()));
        assertThat(TenantTestSupport.runAsResult(TENANT_ASSIGN,
                () -> userPositionService.findByUserId("u-1"))).isEmpty();
        TenantTestSupport.runAs(TENANT_ASSIGN, () -> userPositionService.unassign("u-1", pos.id()));
        UserPositionDTO reassigned = TenantTestSupport.runAsResult(TENANT_ASSIGN,
                () -> userPositionService.assign("u-1", pos.id()));
        assertThat(reassigned.id()).isNotEqualTo(assigned.id());
        assertThat(TenantTestSupport.runAsResult(TENANT_ASSIGN,
                () -> userPositionService.findByUserId("u-1"))).hasSize(1);
    }

    // ---------------------------------------------------------------- 双租户隔离

    @Test
    void tenantIsolationForOrgData() {
        users.register("u-iso", TENANT_ISO_A);
        OrgUnitDTO rootA = TenantTestSupport.runAsResult(TENANT_ISO_A,
                () -> orgUnitService.create(null, OrgUnitType.DEPT, "A 集团", 0));
        OrgUnitDTO childA = TenantTestSupport.runAsResult(TENANT_ISO_A,
                () -> orgUnitService.create(rootA.id(), OrgUnitType.DEPT, "A 子部门", 0));
        PositionDTO posA = TenantTestSupport.runAsResult(TENANT_ISO_A,
                () -> positionService.create(rootA.id(), "A 岗位", 0));
        TenantTestSupport.runAs(TENANT_ISO_A, () -> userPositionService.assign("u-iso", posA.id()));

        // 租户 B：org 三表全空（判别式过滤）
        assertThat(TenantTestSupport.runAsResult(TENANT_ISO_B, () -> orgUnitService.tree())).isEmpty();
        assertThatThrownBy(() -> TenantTestSupport.runAsResult(TENANT_ISO_B,
                () -> orgUnitService.findById(rootA.id())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(rootA.id());
        assertThat(TenantTestSupport.runAsResult(TENANT_ISO_B,
                () -> orgTreeQueryService.findSubTree(rootA.id()))).isEmpty();
        assertThatThrownBy(() -> TenantTestSupport.runAsResult(TENANT_ISO_B,
                () -> positionService.findById(posA.id())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(posA.id());
        assertThat(TenantTestSupport.runAsResult(TENANT_ISO_B,
                () -> userPositionService.findByUserId("u-iso"))).isEmpty();

        // B 建同名根：各见各树、各数各数
        OrgUnitDTO rootB = TenantTestSupport.runAsResult(TENANT_ISO_B,
                () -> orgUnitService.create(null, OrgUnitType.DEPT, "A 集团", 0));
        assertThat(TenantTestSupport.runAsResult(TENANT_ISO_B, () -> orgUnitService.tree()))
                .extracting(n -> n.unit().id()).containsExactly(rootB.id());
        assertThat(TenantTestSupport.runAsResult(TENANT_ISO_A, () -> orgUnitService.subTree(rootA.id())))
                .extracting(OrgUnitDTO::id).containsExactly(rootA.id(), childA.id());

        // 事件租户打标隔离：A 的 create 事件（rootA + childA，岗位/挂岗不发组织事件）只落 TENANT_ISO_A
        assertThat(recorder.countBy(TENANT_ISO_A, "create")).isEqualTo(2);
        assertThat(recorder.countBy(TENANT_ISO_B, "create")).isEqualTo(1);
    }
}
