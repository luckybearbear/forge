package io.github.luckybearbear.forge.service.authorization.domain;

import io.github.luckybearbear.forge.platform.data.AbstractTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * 角色实体——<b>租户表</b>（extends AbstractTenantEntity）：@TenantId 判别式过滤/注入，
 * 租户内 code 唯一（partial unique index {@code WHERE deleted = FALSE}——软删行不占码）。
 * dataScope 为数据权限语义字段（持久化，查询侧拼装在后续任务兑现）。
 */
@Entity
@Table(name = "auth_role")
public class RoleEntity extends AbstractTenantEntity {

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    /** 数据权限范围（枚举按名存储——默认 ORDINAL 会在枚举重排时静默错位）。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "data_scope", nullable = false, length = 16)
    private DataScope dataScope;

    protected RoleEntity() {} // JPA

    public RoleEntity(String code, String name, DataScope dataScope) {
        this.code = code;
        this.name = name;
        this.dataScope = dataScope;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public DataScope getDataScope() { return dataScope; }
    public void setDataScope(DataScope dataScope) { this.dataScope = dataScope; }
}
