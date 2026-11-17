package io.github.luckybearbear.forge.platform.data;

import io.github.luckybearbear.forge.platform.core.IdGenerator;
import io.github.luckybearbear.forge.platform.core.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;
import java.time.Instant;
import org.hibernate.annotations.SoftDelete;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * JPA 审计基线（Phase 2 spec §6.3）：UUIDv7 主键自动赋值、乐观锁、审计、软删除。
 *
 * <p>软删除最终机制（Task 9 spike 实证后定稿）：Hibernate 7 原生**类级**
 * {@code @SoftDelete(columnName = "deleted")}——remove 自动改写为 {@code UPDATE ... SET deleted = true}，
 * 所有加载/查询自动过滤 {@code deleted = false}。类级注解经 EntityBinder.extract 遍历
 * {@code @MappedSuperclass} 链被子类实体继承；原 {@code @SQLDelete}/{@code @SQLRestriction} 方案废弃：
 * {@code @SQLDelete} 在基类上不被读取（bindCustomSql 不遍历基类，spike 实证为物理 DELETE 假绿），
 * 且 {@code {h-table}} 占位符在 hibernate-core 7.4.13 全 jar 0 命中；字段级 @SoftDelete 在
 * 7.4.13 绑定侧无处理（仅类级/集合级），故软删除列由 Hibernate 独占管理、不映射为实体属性
 * （{@code deleted} 字段与访问器随之移除）。
 */
@MappedSuperclass
@SoftDelete(columnName = "deleted")
@EntityListeners(AuditingEntityListener.class)
public abstract class AbstractAuditableEntity {

    private static final IdGenerator ID_GENERATOR = new UuidV7Generator();

    @Id
    @Column(length = 36)
    private String id;

    @Version
    private Long version;

    @CreatedBy
    @Column(name = "created_by", length = 36)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at")
    private Instant createdAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 36)
    private String updatedBy;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void assignId() {
        if (id == null) {
            id = ID_GENERATOR.nextId();
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
