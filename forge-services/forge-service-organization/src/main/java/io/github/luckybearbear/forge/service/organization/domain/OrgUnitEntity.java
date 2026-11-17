package io.github.luckybearbear.forge.service.organization.domain;

import io.github.luckybearbear.forge.platform.data.AbstractTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * 组织单元实体——<b>租户表</b>（spec §6.1）：继承 {@link AbstractTenantEntity}，
 * {@code @TenantId} 判别式自动过滤（查询只读本租户行）/自动注入（写入落当前租户）。
 *
 * <p>树形要点：{@code parentId 为 null 即根}（每租户可多根=森林）；无物理 FK/无 path 物化——
 * 环不变式由 {@code OrgUnitService.move} 的上溯检测保证（脏数据防线：上溯 64 层上限），
 * 业务键 {@code (tenant_id, parent_id)} 走查询索引。软删除/审计/乐观锁/UUIDv7 主键由基类机制承载。
 */
@Entity
@Table(name = "org_unit")
public class OrgUnitEntity extends AbstractTenantEntity {

    /** 逻辑引用同表 id；null = 根（不设物理 FK——先建子后调序等运维场景不因约束锁死）。 */
    @Column(name = "parent_id", length = 36)
    private String parentId;

    @Enumerated(EnumType.STRING) // 默认 ORDINAL 会在枚举重排时静默错位，明确按名存储
    @Column(nullable = false, length = 16)
    private OrgUnitType type;

    @Column(nullable = false, length = 128)
    private String name;

    /** 同级排序（小在前；并列按 id 稳定）。 */
    @Column(nullable = false)
    private int sort;

    protected OrgUnitEntity() {} // JPA

    public OrgUnitEntity(String parentId, OrgUnitType type, String name, int sort) {
        this.parentId = parentId;
        this.type = type;
        this.name = name;
        this.sort = sort;
    }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }
    public OrgUnitType getType() { return type; }
    public void setType(OrgUnitType type) { this.type = type; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getSort() { return sort; }
    public void setSort(int sort) { this.sort = sort; }
}
