-- demo 连库基线（brief Task 11 Step 1 全文）：租户实体（AbstractTenantEntity + @SoftDelete）映射。
-- deleted 列由 Hibernate 7 类级 @SoftDelete 独占管理（remove → UPDATE deleted=true，查询自动过滤）。
CREATE TABLE mold (
    id            VARCHAR(36) PRIMARY KEY,
    tenant_id     VARCHAR(36) NOT NULL,
    name          VARCHAR(128) NOT NULL,
    specification VARCHAR(256),
    status        VARCHAR(32) NOT NULL,
    created_at    TIMESTAMPTZ,
    created_by    VARCHAR(36),
    updated_at    TIMESTAMPTZ,
    updated_by    VARCHAR(36),
    version       BIGINT,
    deleted       BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_mold_tenant ON mold (tenant_id);

CREATE TABLE maintenance_order (
    id          VARCHAR(36) PRIMARY KEY,
    tenant_id   VARCHAR(36) NOT NULL,
    mold_id     VARCHAR(36) NOT NULL,
    mold_name   VARCHAR(128),
    description VARCHAR(512) NOT NULL,
    status      VARCHAR(32) NOT NULL,
    created_at  TIMESTAMPTZ,
    created_by  VARCHAR(36),
    updated_at  TIMESTAMPTZ,
    updated_by  VARCHAR(36),
    version     BIGINT,
    deleted     BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_maint_tenant ON maintenance_order (tenant_id);
