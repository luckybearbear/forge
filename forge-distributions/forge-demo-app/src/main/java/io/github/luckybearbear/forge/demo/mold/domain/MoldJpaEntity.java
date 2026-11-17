package io.github.luckybearbear.forge.demo.mold.domain;

import io.github.luckybearbear.forge.contract.mold.MoldStatus;
import io.github.luckybearbear.forge.platform.data.AbstractTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * 模具 JPA 实体（Task 11 连库升级）：租户隔离（@TenantId 判别式）+ 审计 + 乐观锁 + 软删除
 * 均由基类机制承载（软删除为 Hibernate 7 类级 @SoftDelete，实体与 SQL 无需 deleted 之外的任何处理）。
 */
@Entity
@Table(name = "mold")
public class MoldJpaEntity extends AbstractTenantEntity {

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 256)
    private String specification;

    @Enumerated(EnumType.STRING) // 默认 ORDINAL 会在枚举重排时静默错位，明确按名存储
    @Column(nullable = false, length = 32)
    private MoldStatus status;

    protected MoldJpaEntity() {} // JPA

    public MoldJpaEntity(String name, String specification, MoldStatus status) {
        this.name = name;
        this.specification = specification;
        this.status = status;
    }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public String getSpecification() { return specification; }

    public void setSpecification(String specification) { this.specification = specification; }

    public MoldStatus getStatus() { return status; }

    public void setStatus(MoldStatus status) { this.status = status; }
}
