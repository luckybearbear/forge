package io.github.luckybearbear.forge.service.organization.domain;

import io.github.luckybearbear.forge.platform.data.AbstractTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 用户挂岗实体——<b>租户表</b>（spec §6.1）：userId 逻辑引用 identity_user.id（存在性经契约
 * {@code UserQueryService} 校验，无物理 FK、不依赖 service-identity），positionId 逻辑引用
 * org_position.id（本域校验 ORG-0414）。
 *
 * <p>{@code (tenant_id, user_id, position_id)} 唯一性由 partial unique index
 * （{@code WHERE deleted = FALSE}）承载：软删（摘岗）行不占键，重挂可再插；service 层先查后插
 * 实现幂等挂岗，DB 索引兜底并发。
 */
@Entity
@Table(name = "user_position")
public class UserPositionEntity extends AbstractTenantEntity {

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "position_id", nullable = false, length = 36)
    private String positionId;

    protected UserPositionEntity() {} // JPA

    public UserPositionEntity(String userId, String positionId) {
        this.userId = userId;
        this.positionId = positionId;
    }

    public String getUserId() { return userId; }
    public String getPositionId() { return positionId; }
}
