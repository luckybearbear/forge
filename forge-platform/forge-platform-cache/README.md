# forge-platform-cache

缓存抽象与 Redis 实现（Phase 2a Task 8）。

- 依赖：`spring-data-redis`（Boot BOM 管版本 4.1.2）、`forge-platform-core`（`CurrentTenantSupplier`，**不依赖** platform-security）
- 状态：✅ Phase 2a 落地
- 排期：starter 装配（forge-cache-starter）在 Phase 2b

## 组成

| 类 | 说明 |
| --- | --- |
| `CacheFacade` | 缓存抽象 SPI（Phase 1，put/get/evict + TTL） |
| `TenantCacheKeys` | 租户 key 规则（spec §6.1 入口 3）：`t:{tenantId}:{key}`；ignore / 无租户 → `p:{key}` |
| `RedisCacheFacade` | `CacheFacade` 的 Redis 实现（构造 `RedisTemplate<String,Object>` + supplier，委托 opsForValue） |
| `TenantRedisCacheManager` | 以租户前缀 cacheName 的 `RedisCacheManager` |

## TenantRedisCacheManager 覆写可行性（javap 实证，未降级）

针对 spring-data-redis **4.1.2**（Spring Framework 7.0.10）：

- `AbstractCacheManager.getCache(String)`：**public、非 final**，可覆写。字节码确认内部 `cacheMap`
  以传入名为键（miss → `getMissingCache` → `decorateCache` → 以同名存入），
  故 `super.getCache(TenantCacheKeys.cacheName(name, supplier))` 与内部 map 完全兼容，
  重复获取同一逻辑名命中同一实例。
- `lookupCache(String)` 为 `protected final`，但不在覆写路径上；`createCache` 方法不存在
  （brief 中的猜测），真实创建钩子是 `getMissingCache`/`createRedisCache`（均 protected 非 final）。
- 父类 `AbstractTransactionSupportingCacheManager` 在 Spring Framework 7 中位于
  **spring-context-support**（spring-data-redis 传递依赖，无需显式声明）。
- 结论：**无需降级**，全量交付 `TenantRedisCacheManager`；已知取舍：`getCacheNames()`
  返回前缀化后的名字（2b starter 集成时如需逻辑名可再处理）。
