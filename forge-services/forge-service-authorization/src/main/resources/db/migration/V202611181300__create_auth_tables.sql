-- 授权域表（spec §6.1）：auth_role/auth_role_permission/auth_user_role 为租户表（tenant_id 判别式），
-- auth_permission 为全局表（权限字典——平台级领域语言，全租户共享）。
-- 唯一性一律 partial unique index（WHERE deleted = FALSE）：软删行不占码，编码可复用。
-- 单列查询索引（role_id/user_id，与 @Table(indexes=...) 声明一致——评审 Minor-1 补齐）：该迁移
-- 从未发布过任何环境（随本任务首提，demo/test 每次新库），按 Flyway 未发布脚本可直改的约定原位补齐。
CREATE TABLE auth_role (
    id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(36) NOT NULL,
    code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL, data_scope VARCHAR(16) NOT NULL,
    version BIGINT, created_by VARCHAR(36), created_at TIMESTAMPTZ, updated_by VARCHAR(36), updated_at TIMESTAMPTZ, deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uk_auth_role_tenant_code ON auth_role (tenant_id, code) WHERE deleted = FALSE;
CREATE INDEX idx_auth_role_tenant ON auth_role (tenant_id) WHERE deleted = FALSE;

CREATE TABLE auth_permission (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL, type VARCHAR(8) NOT NULL,
    version BIGINT, created_by VARCHAR(36), created_at TIMESTAMPTZ, updated_by VARCHAR(36), updated_at TIMESTAMPTZ, deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uk_auth_permission_code ON auth_permission (code) WHERE deleted = FALSE;

CREATE TABLE auth_role_permission (
    id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(36) NOT NULL,
    role_id VARCHAR(36) NOT NULL, permission_id VARCHAR(36) NOT NULL,
    version BIGINT, created_by VARCHAR(36), created_at TIMESTAMPTZ, updated_by VARCHAR(36), updated_at TIMESTAMPTZ, deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uk_auth_role_permission ON auth_role_permission (tenant_id, role_id, permission_id) WHERE deleted = FALSE;
CREATE INDEX idx_auth_role_permission_role ON auth_role_permission (role_id) WHERE deleted = FALSE;

CREATE TABLE auth_user_role (
    id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL, role_id VARCHAR(36) NOT NULL,
    version BIGINT, created_by VARCHAR(36), created_at TIMESTAMPTZ, updated_by VARCHAR(36), updated_at TIMESTAMPTZ, deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uk_auth_user_role ON auth_user_role (tenant_id, user_id, role_id) WHERE deleted = FALSE;
CREATE INDEX idx_auth_user_role_user ON auth_user_role (user_id) WHERE deleted = FALSE;
CREATE INDEX idx_auth_user_role_role ON auth_user_role (role_id) WHERE deleted = FALSE;

-- 权限点字典 seed（固定 id，TenantCreated 初始化按 id 全量授权）：通用管理点 + demo 业务点。
INSERT INTO auth_permission (id, code, name, type, version, created_by, created_at, deleted) VALUES
    ('0194b480-0000-7000-8000-000000000001', 'user:manage',  '用户管理', 'API', 0, 'system', now(), FALSE),
    ('0194b480-0000-7000-8000-000000000002', 'role:manage',  '角色管理', 'API', 0, 'system', now(), FALSE),
    ('0194b480-0000-7000-8000-000000000003', 'org:manage',   '组织管理', 'API', 0, 'system', now(), FALSE),
    ('0194b480-0000-7000-8000-000000000004', 'mold:create',  '模具创建', 'API', 0, 'system', now(), FALSE),
    ('0194b480-0000-7000-8000-000000000005', 'mold:read',    '模具查看', 'API', 0, 'system', now(), FALSE),
    ('0194b480-0000-7000-8000-000000000006', 'mold:repair',  '模具报修', 'API', 0, 'system', now(), FALSE),
    ('0194b480-0000-7000-8000-000000000007', 'maint:read',   '维保查看', 'API', 0, 'system', now(), FALSE);
