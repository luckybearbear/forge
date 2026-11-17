package io.github.luckybearbear.forge.service.organization.app;

import io.github.luckybearbear.forge.contract.organization.OrgUnitChanged;
import io.github.luckybearbear.forge.contract.organization.OrgUnitDTO;
import io.github.luckybearbear.forge.contract.organization.OrganizationErrorCodes;
import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.platform.core.CurrentTenantSupplier;
import io.github.luckybearbear.forge.service.organization.domain.OrgUnitEntity;
import io.github.luckybearbear.forge.service.organization.domain.OrgUnitRepository;
import io.github.luckybearbear.forge.service.organization.domain.OrgUnitType;
import io.github.luckybearbear.forge.service.organization.domain.PositionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 组织单元应用服务：create/update/move/delete/tree/subTree。
 *
 * <p><b>环检测（move，ORG-0409/409）</b>：从新父沿 parentId 逐级上溯，若途中遇到被移动单元自身
 * （含 newParent = 自己）即环——拒绝并回滚；上溯超过 {@link #MAX_TREE_DEPTH} 层同样拒绝
 * （脏数据防线：库里若已有环，上溯永不终止，64 层上限熔断防死循环）。
 *
 * <p><b>删除保护（ORG-0409 码 + 特定文案，不新增枚举）</b>：存在子节点或挂靠岗位即拒绝
 * （"存在子节点或挂岗，无法删除"）——须先清空子树并撤岗才可删。
 *
 * <p>事件：create/update（含 move）/delete 三态发布 {@link OrgUnitChanged}（业务事务内，
 * spec §10）；payload 的 tenantId 为事件主体租户——加载态实体取实体字段，create 侧取当前租户
 * 上下文（@TenantId 插入时才绑定，见 {@link #publish}）。
 * 部分更新：type/name/sort 传 null 不改（sort 为 Integer 包装以区分"不改"）。
 */
@Service
public class OrgUnitService {

    /** 上溯深度上限（防脏数据环死循环；正常组织树远浅于此）。 */
    static final int MAX_TREE_DEPTH = 64;

    /** OrgUnitChanged.changeType 三态字面量（契约 javadoc：区分创建/更新/删除）。 */
    static final String CHANGE_TYPE_CREATE = "create";
    static final String CHANGE_TYPE_UPDATE = "update";
    static final String CHANGE_TYPE_DELETE = "delete";

    /** 树内稳定排序：sort 升序，并列按 id（UUIDv7 时间序 = 建立序）。 */
    private static final Comparator<OrgUnitEntity> TREE_ORDER =
            Comparator.comparingInt(OrgUnitEntity::getSort).thenComparing(OrgUnitEntity::getId);

    /** groupingBy 的根桶键（parentId 为 null 的根；UUIDv7 id 不可能为空串）。 */
    private static final String ROOT_BUCKET = "";

    private final OrgUnitRepository repository;
    private final PositionRepository positionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CurrentTenantSupplier currentTenantSupplier;

    public OrgUnitService(OrgUnitRepository repository, PositionRepository positionRepository,
            ApplicationEventPublisher eventPublisher, CurrentTenantSupplier currentTenantSupplier) {
        this.repository = repository;
        this.positionRepository = positionRepository;
        this.eventPublisher = eventPublisher;
        this.currentTenantSupplier = currentTenantSupplier;
    }

    /** 创建单元（parentId 为 null 即根；父须存在于本租户 → ORG-0404）。 */
    @Transactional
    public OrgUnitDTO create(String parentId, OrgUnitType type, String name, int sort) {
        if (parentId != null) {
            requireUnit(parentId);
        }
        OrgUnitEntity saved = repository.save(new OrgUnitEntity(parentId, type, name, sort));
        publish(saved, CHANGE_TYPE_CREATE);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public OrgUnitDTO findById(String id) {
        return toDto(requireUnit(id));
    }

    /** 部分更新：type/name/sort 传 null 即不改（父变更走 {@link #move}，不在 update 范围）。 */
    @Transactional
    public OrgUnitDTO update(String id, OrgUnitType type, String name, Integer sort) {
        OrgUnitEntity entity = requireUnit(id);
        if (type != null) {
            entity.setType(type);
        }
        if (name != null) {
            entity.setName(name);
        }
        if (sort != null) {
            entity.setSort(sort);
        }
        publish(entity, CHANGE_TYPE_UPDATE);
        return toDto(entity); // 托管态脏检查随事务提交落库
    }

    /**
     * 移动单元（newParentId 为 null 即提为根）：环检测——新父的祖先链含自身则 ORG-0409/409
     * （事务回滚，树形不变）。
     */
    @Transactional
    public OrgUnitDTO move(String id, String newParentId) {
        OrgUnitEntity entity = requireUnit(id);
        if (newParentId != null) {
            OrgUnitEntity parent = requireUnit(newParentId);
            assertNotDescendantOrSelf(entity, parent);
        }
        entity.setParentId(newParentId);
        publish(entity, CHANGE_TYPE_UPDATE);
        return toDto(entity);
    }

    /** 删除（须为叶子且无挂靠岗位；软删）。ORG-0409 码 + 文案"存在子节点或挂岗，无法删除"。 */
    @Transactional
    public void delete(String id) {
        OrgUnitEntity entity = requireUnit(id);
        if (repository.existsByParentId(id) || positionRepository.existsByOrgUnitId(id)) {
            throw new BusinessException(OrganizationErrorCodes.ORG_CYCLE_DETECTED,
                    "存在子节点或挂岗，无法删除: " + id).withHttpStatus(409);
        }
        repository.delete(entity);
        publish(entity, CHANGE_TYPE_DELETE);
    }

    /** 全林树形（本租户所有根及其后代，节点内兄弟按 (sort, id) 稳定排序）。 */
    @Transactional(readOnly = true)
    public List<OrgUnitNode> tree() {
        Map<String, List<OrgUnitEntity>> byParent = repository.findAll().stream()
                .collect(Collectors.groupingBy(e -> e.getParentId() == null ? ROOT_BUCKET : e.getParentId()));
        return byParent.getOrDefault(ROOT_BUCKET, List.of()).stream()
                .sorted(TREE_ORDER)
                .map(root -> toNode(root, byParent))
                .toList();
    }

    /**
     * 子树快照（根开头的先序扁平列表）。根不存在返回空集——快照语义：消费方（如授权数据域）
     * 对已删单元取子树即得空，据此失效缓存，无须区分 404。
     *
     * <p><b>脏环守卫</b>：下钻带 visited-id 集合，环成员各输出一次后终止——与 tree() 自根不可达
     * 环的安全性论证不同，本方法可从任意节点（含环成员）起钻，必须显式判重防无限递归。
     */
    @Transactional(readOnly = true)
    public List<OrgUnitDTO> subTree(String rootId) {
        return repository.findById(rootId).map(root -> {
            List<OrgUnitDTO> out = new ArrayList<>();
            collectPreOrder(root, out, new HashSet<>());
            return out;
        }).orElseGet(List::of);
    }

    // ---------------------------------------------------------------- 内部

    private OrgUnitNode toNode(OrgUnitEntity entity, Map<String, List<OrgUnitEntity>> byParent) {
        List<OrgUnitNode> children = byParent.getOrDefault(entity.getId(), List.of()).stream()
                .sorted(TREE_ORDER)
                .map(child -> toNode(child, byParent))
                .toList();
        return new OrgUnitNode(toDto(entity), children);
    }

    private void collectPreOrder(OrgUnitEntity entity, List<OrgUnitDTO> out, Set<String> visited) {
        if (!visited.add(entity.getId())) {
            return; // 脏环守卫：已访问节点不再下钻（环成员作 root 时含各成员一次并终止，不 StackOverflow）
        }
        out.add(toDto(entity));
        repository.findByParentId(entity.getId()).stream()
                .sorted(TREE_ORDER)
                .forEach(child -> collectPreOrder(child, out, visited));
    }

    /**
     * 环检测：自 parent 沿 parentId 上溯，遇被移动单元自身即环（ORG-0409/409）；
     * 超 {@link #MAX_TREE_DEPTH} 层视为脏数据环，同样 ORG-0409（熔断防死循环）。
     * 上溯途中父链断裂（悬空 parent_id）按 ORG-0404 上抛——诚实暴露脏数据。
     */
    private void assertNotDescendantOrSelf(OrgUnitEntity moving, OrgUnitEntity parent) {
        OrgUnitEntity current = parent;
        int depth = 0;
        while (current != null) {
            if (current.getId().equals(moving.getId())) {
                throw BusinessException.of(OrganizationErrorCodes.ORG_CYCLE_DETECTED, moving.getId()).withHttpStatus(409);
            }
            if (++depth > MAX_TREE_DEPTH) {
                throw new BusinessException(OrganizationErrorCodes.ORG_CYCLE_DETECTED,
                        "组织树上溯超过 " + MAX_TREE_DEPTH + " 层（疑似脏数据环），拒绝移动: " + moving.getId())
                        .withHttpStatus(409);
            }
            String parentId = current.getParentId();
            current = parentId == null ? null : requireUnit(parentId);
        }
    }

    private OrgUnitEntity requireUnit(String id) {
        return repository.findById(id)
                .orElseThrow(() -> BusinessException.of(OrganizationErrorCodes.ORG_UNIT_NOT_FOUND, id)
                        .withHttpStatus(404));
    }

    /**
     * 事件主体租户：加载/已落库实体直接取 {@code getTenantId()}；create 侧 @TenantId 插入时才绑定、
     * publish 时实体字段尚未回填（identity 同一实证）——取当前租户上下文
     * （{@link CurrentTenantSupplier}，platform-core 接口经 data-starter 装配 TTL 实现）。
     */
    private void publish(OrgUnitEntity entity, String changeType) {
        String tenantId = entity.getTenantId() != null ? entity.getTenantId()
                : currentTenantSupplier.currentTenantId()
                        .orElseThrow(() -> new IllegalStateException("publishing OrgUnitChanged without tenant context"));
        eventPublisher.publishEvent(new OrgUnitChanged(tenantId, entity.getId(), changeType, Instant.now()));
    }

    private OrgUnitDTO toDto(OrgUnitEntity entity) {
        return new OrgUnitDTO(entity.getId(), entity.getParentId(), entity.getType().name(),
                entity.getName(), entity.getSort());
    }
}
