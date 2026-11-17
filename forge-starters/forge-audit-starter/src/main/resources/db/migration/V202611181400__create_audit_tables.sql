-- 访问/错误审计表（spec §6.6/附录 A，Task 7）：全局表——tenant_id/user_id 为普通冗余列
-- （非 @TenantId 判别式，不参与租户过滤；跨租户检索/留存，未认证请求可空）。
-- append-only 流水：无唯一约束（软删行无码可复用语义，partial unique 不适用）。
-- 审计基类列随 AbstractAuditableEntity：version/created_by/created_at/updated_by/updated_at/deleted。
CREATE TABLE api_access_log (
    id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(36), user_id VARCHAR(36),
    method VARCHAR(8) NOT NULL, path VARCHAR(512) NOT NULL, result_code INT NOT NULL,
    duration_ms BIGINT NOT NULL, body_digest TEXT, occurred_at TIMESTAMPTZ NOT NULL,
    version BIGINT, created_by VARCHAR(36), created_at TIMESTAMPTZ, updated_by VARCHAR(36), updated_at TIMESTAMPTZ, deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_api_access_log_tenant_created ON api_access_log (tenant_id, created_at) WHERE deleted = FALSE;
CREATE INDEX idx_api_access_log_path_created ON api_access_log (path, created_at) WHERE deleted = FALSE;

CREATE TABLE api_error_log (
    id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(36), user_id VARCHAR(36),
    exception VARCHAR(512) NOT NULL, message TEXT, stack_digest TEXT, occurred_at TIMESTAMPTZ NOT NULL,
    version BIGINT, created_by VARCHAR(36), created_at TIMESTAMPTZ, updated_by VARCHAR(36), updated_at TIMESTAMPTZ, deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_api_error_log_tenant_created ON api_error_log (tenant_id, created_at) WHERE deleted = FALSE;
