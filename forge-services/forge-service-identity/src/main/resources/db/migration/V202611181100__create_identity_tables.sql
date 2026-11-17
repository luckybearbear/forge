-- 身份域表（spec §6.1）：identity_user 为租户表（tenant_id 判别式），platform_admin 为全局表。
-- username 唯一性用 partial unique index（WHERE deleted = FALSE）：软删行不占名，名字可复用
-- （identity_user 为 (tenant_id, username) 复合唯一——租户内唯一，跨租户可重名）。
CREATE TABLE identity_user (
    id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(36) NOT NULL,
    username VARCHAR(64) NOT NULL, phone VARCHAR(32), password_hash VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT, created_by VARCHAR(36), created_at TIMESTAMPTZ, updated_by VARCHAR(36), updated_at TIMESTAMPTZ, deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uk_identity_user_tenant_username ON identity_user (tenant_id, username) WHERE deleted = FALSE;
CREATE INDEX idx_identity_user_tenant ON identity_user (tenant_id) WHERE deleted = FALSE;

CREATE TABLE platform_admin (
    id VARCHAR(36) PRIMARY KEY, username VARCHAR(64) NOT NULL, password_hash VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT, created_by VARCHAR(36), created_at TIMESTAMPTZ, updated_by VARCHAR(36), updated_at TIMESTAMPTZ, deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uk_platform_admin_username ON platform_admin (username) WHERE deleted = FALSE;

-- 平台管理员种子（README 记录：初始口令 admin123，首登后应尽快经改密通道更换）。
-- hash 由 forge-platform-security PasswordHasher（DelegatingPasswordEncoder，bcrypt）现场生成；
-- 带注册前缀 {bcrypt} —— matches() 按前缀路由编码器，勿手工剥离。
INSERT INTO platform_admin (id, username, password_hash, status, version, created_by, created_at, deleted)
VALUES ('01933580-0000-7000-8000-000000000001', 'admin',
        '{bcrypt}$2a$10$YyIto7YZtCwOzKhl5HKMfuYIbTr6qHifR.O6g25KpXBXaokEHHmRi',
        'ENABLED', 0, 'system', now(), FALSE);
