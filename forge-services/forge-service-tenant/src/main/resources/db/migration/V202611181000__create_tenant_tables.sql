-- 租户/套餐全局表（spec §6.1：无 tenant_id——判别式多租户与平台写守护均不适用）
-- code 唯一性用 partial unique index（WHERE deleted = FALSE）：软删行不占码，编码可复用
CREATE TABLE tenant (
    id VARCHAR(36) PRIMARY KEY, code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL,
    package_id VARCHAR(36), status VARCHAR(16) NOT NULL, expired_at TIMESTAMPTZ,
    version BIGINT, created_by VARCHAR(36), created_at TIMESTAMPTZ, updated_by VARCHAR(36), updated_at TIMESTAMPTZ, deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uk_tenant_code ON tenant (code) WHERE deleted = FALSE;
CREATE TABLE tenant_package (
    id VARCHAR(36) PRIMARY KEY, code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL,
    max_users INT, remark VARCHAR(256),
    version BIGINT, created_by VARCHAR(36), created_at TIMESTAMPTZ, updated_by VARCHAR(36), updated_at TIMESTAMPTZ, deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uk_tenant_package_code ON tenant_package (code) WHERE deleted = FALSE;
