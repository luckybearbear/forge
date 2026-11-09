package io.github.luckybearbear.forge.platform.data;

import java.time.Instant;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;

/**
 * Spring Data JDBC 审计基类：ID（String/UUIDv7 策略）+ 乐观锁 + 审计字段。
 * 应用侧需 @EnableJdbcAuditing + AuditorAware Bean（Phase 2 随 identity 服务提供）。
 * 本阶段为半成品：无实体、Demo 不连库、不进 forge-spring-boot-starter。
 */
public abstract class AbstractAuditableEntity {

    @Id
    private String id;

    @Version
    private long version;

    @CreatedBy
    private String createdBy;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedBy
    private String updatedBy;

    @LastModifiedDate
    private Instant updatedAt;

    public String getId() { return id; }

    public void setId(String id) { this.id = id; }

    public long getVersion() { return version; }

    public void setVersion(long version) { this.version = version; }

    public String getCreatedBy() { return createdBy; }

    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }

    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getUpdatedBy() { return updatedBy; }

    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public Instant getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
