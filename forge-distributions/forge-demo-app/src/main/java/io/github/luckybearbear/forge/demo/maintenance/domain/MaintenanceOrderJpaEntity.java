package io.github.luckybearbear.forge.demo.maintenance.domain;

import io.github.luckybearbear.forge.platform.data.AbstractTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 维修单 JPA 实体（Task 11 连库升级）：moldName 为事件消费时经契约查询补全的快照；
 * status 先以字符串承载（OPEN 等），状态机演进随后续任务。
 */
@Entity
@Table(name = "maintenance_order")
public class MaintenanceOrderJpaEntity extends AbstractTenantEntity {

    @Column(name = "mold_id", nullable = false, length = 36)
    private String moldId;

    @Column(name = "mold_name", length = 128)
    private String moldName;

    @Column(nullable = false, length = 512)
    private String description;

    @Column(nullable = false, length = 32)
    private String status;

    protected MaintenanceOrderJpaEntity() {} // JPA

    public MaintenanceOrderJpaEntity(String moldId, String moldName, String description, String status) {
        this.moldId = moldId;
        this.moldName = moldName;
        this.description = description;
        this.status = status;
    }

    public String getMoldId() { return moldId; }

    public void setMoldId(String moldId) { this.moldId = moldId; }

    public String getMoldName() { return moldName; }

    public void setMoldName(String moldName) { this.moldName = moldName; }

    public String getDescription() { return description; }

    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }
}
