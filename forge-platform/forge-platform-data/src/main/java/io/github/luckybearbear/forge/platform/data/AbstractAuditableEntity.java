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
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** JPA 审计基线（Phase 2 spec §6.3）：UUIDv7 主键自动赋值、乐观锁、审计、软删除。 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted = false")
@SQLDelete(sql = "UPDATE {h-table} SET deleted = true WHERE id = ?")
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

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

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
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
}
