# forge-cache-starter

缓存层 starter：引入即得 **`RedisTemplate<String,Object>` JSON 基线**（String key + Jackson3 泛型 JSON value）、
**`RedisCacheFacade`**（程序式租户前缀缓存，权限缓存等业务侧装配点）与 **`TenantRedisCacheManager`**
（Spring Cache API 侧的租户前缀 CacheManager，`@Primary`）。

## 自动装配（`ForgeCacheAutoConfiguration`）

| bean | 条件 | 说明 |
|---|---|---|
| `redisTemplate`（`RedisTemplate<String,Object>`） | `@ConditionalOnMissingBean(name="redisTemplate")` | String key + Jackson3 JSON value（含 hash）；**before Boot 的 `DataRedisAutoConfiguration`** 显式定序——Boot 默认 JDK 序列化模板按名让位，上下文恒恰一个 `redisTemplate` |
| `redisCacheFacade`（`RedisCacheFacade`） | `@ConditionalOnMissingBean(CacheFacade.class)` | key 前缀化 `t:{tenantId}:{key}` / 平台视角 `p:{key}`；TTL 语义：null/zero/negative = 不过期 |
| `tenantRedisCacheManager`（`TenantRedisCacheManager`） | `@Primary` + `@ConditionalOnMissingBean(CacheManager.class)` | `getCache(name)` 逻辑名映射 `t:{tenantId}:{name}`；entry TTL 默认 30min |

- **与 2a RedisTokenStore 无冲突**：token 存储用 `StringRedisTemplate`（不同 bean、String 值编码），
  本 starter 的 `redisTemplate` 替换的是 Boot 的 JDK 序列化默认模板；两模板各司其职，无双注册。
- **JSON 泛型序列化**：spring-data-redis 4.x 的 `GenericJacksonJsonRedisSerializer`（Jackson 3，
  `tools.jackson`）+ default typing 白名单（本项目前缀 + JDK 集合/时间/基础类型）——Object 值往返保留
  具体类型；JDK 序列化被有意弃用（不可演化 + 反序列化 gadget 面）。
- **CurrentTenantSupplier 来源**：本 starter 只依赖接口，实现 bean 由 **forge-data-starter** 提供
  （`TenantContextHolderSupplier`，`@ConditionalOnMissingBean` 让位治理）。只引 cache-starter 的应用
  须自备 supplier，否则按缺依赖 fail-fast（启动即暴露，静默错路由不可接受）。
- 消费方示例：service-authorization 的权限快照缓存（key `auth:perm:{userId}`，TTL 5min，
  `UserPermissionsQueryImpl`）。

## 键空间约定

- 租户视角：`t:{tenantId}:{logicalKey}`（facade）/ `t:{tenantId}:{cacheName}::{entryKey}`（CacheManager）
- 平台视角（`ignore` 或无租户上下文）：`p:{logicalKey}` / `p:{cacheName}::{entryKey}`
- 跨连接可见性注记（实证）：写命令（facade 的 RedisTemplate / CacheWriter 连接）对其他连接的可见存在
  毫秒级窗口——紧邻读断言的测试用 Awaitility 有界等待兜底（见集成测试 javadoc）。

## 集成实证

`CacheStarterIntegrationTest`（继承 platform-test `AbstractIntegrationTest`，真实 redis:8-alpine 容器）：
facade put/get/evict 的原始 key 带 `t:` 前缀（经 StringRedisTemplate 读原始 key/值 JSON 断言）/
平台视角 `p:` 前缀、租户间互不可见/TTL 下发（原始键 `getExpire` > 0）/模板基线 String-key+JSON-value/
CacheManager 租户前缀 + @Primary——全绿（RED 复现：自动配置未注册时 4/4 `NoSuchBeanDefinitionException`）。
