package io.github.luckybearbear.forge.platform.web;

/**
 * 全底座 Filter 顺序段（借鉴 yudao WebFilterOrderEnum）。业务自定义 Filter 从
 * {@link #BUSINESS_FILTER_DEFAULT} 起排，禁止占用负数段。
 */
public final class FilterOrder {

    /** CORS 最前。 */
    public static final int CORS_FILTER = -200;
    /** 请求体可重复读包装（Phase 2）。 */
    public static final int REQUEST_BODY_WRAPPER = -150;
    /** 租户上下文解析（Phase 2 预留）。 */
    public static final int TENANT_FILTER = -120;
    /** 安全认证（Phase 2 预留，与 Spring Security 链衔接）。 */
    public static final int SECURITY_FILTER = -100;
    /** API 访问日志（Phase 2 预留）。 */
    public static final int ACCESS_LOG_FILTER = -80;
    /** 业务自定义 Filter 默认起点。 */
    public static final int BUSINESS_FILTER_DEFAULT = 0;

    private FilterOrder() {}
}
