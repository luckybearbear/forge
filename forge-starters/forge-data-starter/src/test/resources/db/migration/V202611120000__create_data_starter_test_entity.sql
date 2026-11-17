CREATE TABLE data_starter_test_entity (
    id           VARCHAR(36) PRIMARY KEY,
    tenant_id    VARCHAR(36) NOT NULL,
    name         VARCHAR(64) NOT NULL,
    recorded_at  TIMESTAMPTZ NOT NULL,
    version      BIGINT,
    created_by   VARCHAR(36),
    created_at   TIMESTAMPTZ,
    updated_by   VARCHAR(36),
    updated_at   TIMESTAMPTZ,
    deleted      BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_dstest_tenant ON data_starter_test_entity (tenant_id);
