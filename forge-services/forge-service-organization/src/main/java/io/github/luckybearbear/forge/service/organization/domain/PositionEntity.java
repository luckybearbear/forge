package io.github.luckybearbear.forge.service.organization.domain;

import io.github.luckybearbear.forge.platform.data.AbstractTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 岗位实体——<b>租户表</b>（spec §6.1）：挂靠于组织单元（{@code orgUnitId} 逻辑引用 org_unit.id，
 * 无物理 FK），用户经 {@link UserPositionEntity} 挂到岗位上。判别式/软删/审计/乐观锁/UUIDv7
 * 主键由基类机制承载。表名 {@code org_position}（POSITION 为 SQL 保留字，避开引用麻烦）。
 */
@Entity
@Table(name = "org_position")
public class PositionEntity extends AbstractTenantEntity {

    /** 逻辑引用 org_unit.id（创建/换挂靠时 service 校验存在性 → ORG-0404）。 */
    @Column(name = "org_unit_id", nullable = false, length = 36)
    private String orgUnitId;

    @Column(nullable = false, length = 128)
    private String name;

    /** 同单元内排序（小在前；并列按 id 稳定）。 */
    @Column(nullable = false)
    private int sort;

    protected PositionEntity() {} // JPA

    public PositionEntity(String orgUnitId, String name, int sort) {
        this.orgUnitId = orgUnitId;
        this.name = name;
        this.sort = sort;
    }

    public String getOrgUnitId() { return orgUnitId; }
    public void setOrgUnitId(String orgUnitId) { this.orgUnitId = orgUnitId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getSort() { return sort; }
    public void setSort(int sort) { this.sort = sort; }
}
