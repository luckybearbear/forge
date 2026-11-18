-- 菜单切片（Task 1）：菜单合一模型——auth_permission 直接携带菜单树与路由元数据（不建新表）。
-- 六列均可空（DIR 无路由字段）；visible NOT NULL DEFAULT TRUE（字典存量行回填 TRUE）。
-- code 唯一性仍是 partial unique index uk_auth_permission_code (code) WHERE deleted = FALSE，不受影响。
ALTER TABLE auth_permission
    ADD COLUMN parent_id VARCHAR(36),
    ADD COLUMN path VARCHAR(256),
    ADD COLUMN component VARCHAR(256),
    ADD COLUMN icon VARCHAR(64),
    ADD COLUMN sort INT,
    ADD COLUMN visible BOOLEAN NOT NULL DEFAULT TRUE;
-- 菜单树 seed（固定 id，目录在前）
INSERT INTO auth_permission (id, code, name, type, parent_id, path, component, icon, sort, visible, created_at, deleted)
VALUES ('perm-node-dir-sys',  'sys-dir',       '系统管理', 'DIR',  NULL,       NULL,             NULL,                    'Setting',  100, TRUE, now(), FALSE),
       ('perm-node-dashboard','dashboard',     '仪表盘',   'MENU', NULL,       '/dashboard',     'system/dashboard/index','Odometer', 10,  TRUE, now(), FALSE),
       ('perm-node-tenant',   'tenant:manage', '租户管理', 'MENU', 'perm-node-dir-sys', '/system/tenants', 'system/tenant/index', 'OfficeBuilding', 110, TRUE, now(), FALSE);
UPDATE auth_permission SET type='MENU', parent_id='perm-node-dir-sys', path='/system/permissions',
       component='system/permission/index', icon='Key', sort=120
WHERE code='role:manage' AND deleted=FALSE;
