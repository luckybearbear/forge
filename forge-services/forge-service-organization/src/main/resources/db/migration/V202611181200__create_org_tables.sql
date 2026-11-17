-- 组织域表（spec §6.1）：三表均为租户表（tenant_id 判别式过滤/自动注入）。
-- org_unit：树形（parent_id 为 null 即根，每租户可多根=森林）；环不变式由 service 上溯检测保证
-- （无物理 FK/无 path 物化），(tenant_id, parent_id) 走子节点查询索引。
-- 岗位表名 org_position（POSITION 为 SQL 保留字，避开标识符引用麻烦）。
-- user_position 唯一 (tenant_id, user_id, position_id) 用 partial unique index（WHERE deleted = FALSE）：
-- 摘岗（软删）行不占键，重挂可再插；service 先查后插实现幂等挂岗，索引兜底并发。
CREATE TABLE org_unit (
    id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(36) NOT NULL,
    parent_id VARCHAR(36), type VARCHAR(16) NOT NULL, name VARCHAR(128) NOT NULL, sort INT NOT NULL,
    version BIGINT, created_by VARCHAR(36), created_at TIMESTAMPTZ, updated_by VARCHAR(36), updated_at TIMESTAMPTZ, deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_org_unit_tenant_parent ON org_unit (tenant_id, parent_id) WHERE deleted = FALSE;

CREATE TABLE org_position (
    id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(36) NOT NULL,
    org_unit_id VARCHAR(36) NOT NULL, name VARCHAR(128) NOT NULL, sort INT NOT NULL,
    version BIGINT, created_by VARCHAR(36), created_at TIMESTAMPTZ, updated_by VARCHAR(36), updated_at TIMESTAMPTZ, deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_org_position_tenant_org_unit ON org_position (tenant_id, org_unit_id) WHERE deleted = FALSE;

CREATE TABLE user_position (
    id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL, position_id VARCHAR(36) NOT NULL,
    version BIGINT, created_by VARCHAR(36), created_at TIMESTAMPTZ, updated_by VARCHAR(36), updated_at TIMESTAMPTZ, deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uk_user_position_tenant_user_position ON user_position (tenant_id, user_id, position_id) WHERE deleted = FALSE;
CREATE INDEX idx_user_position_tenant_position ON user_position (tenant_id, position_id) WHERE deleted = FALSE;
