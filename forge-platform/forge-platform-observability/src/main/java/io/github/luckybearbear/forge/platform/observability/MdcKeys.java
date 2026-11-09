package io.github.luckybearbear.forge.platform.observability;

/** MDC key 约定，logback pattern 与链路追踪共用。 */
public final class MdcKeys {

    public static final String TRACE_ID = "traceId";
    public static final String TENANT_ID = "tenantId";
    public static final String USER_ID = "userId";

    private MdcKeys() {}
}
