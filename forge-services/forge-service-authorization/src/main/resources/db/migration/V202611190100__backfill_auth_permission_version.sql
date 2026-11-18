-- 回填菜单 seed 行的乐观锁版本（Task 5 手检流 DEFECT-1）：
-- V202611190000 的三条菜单 INSERT 未含 version 列（auth_permission.version 可空且无默认），
-- NULL 使基类 @Version(Long) 乐观锁在 seed 行首次脏更新时增量 NPE（提交期失败 → 500，
-- 手检流 PUT /admin-api/permissions/{seed 行} HTTP 实证）。幂等回填 0——与基线 seed
-- V202611181300 显式 version=0 及 JPA 新建行（@Version 从 0 起自增）口径一致。
UPDATE auth_permission SET version = 0 WHERE version IS NULL;
