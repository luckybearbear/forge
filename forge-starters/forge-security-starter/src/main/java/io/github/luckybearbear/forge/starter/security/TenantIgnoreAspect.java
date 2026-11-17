package io.github.luckybearbear.forge.starter.security;

import io.github.luckybearbear.forge.platform.security.TenantContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

/**
 * @TenantIgnore AOP（spec §6.1 平台视角）：进入标注方法/类时切换 ignore=true，方法体（含其
 * ORM 查询）以平台视角执行——TenantResolver 将解析为 PLATFORM_TENANT_ID（"0"，租户表恒空，
 * 非跨租户全量）；finally 恢复进入前的原值（嵌套标注可正确还原外层状态）。
 *
 * <p><b>排序契约（终审 Important #3）</b>：{@code @Order(0)} 确保本切面先于事务拦截器
 * （事务 advice 缺省 LOWEST_PRECEDENCE）执行——事务开启即打开 Hibernate 会话，判别式租户在
 * <b>会话开启时</b>解析；若切面晚于事务，ignore 的设置发生在会话开启之后，方法体内首次查询
 * 的租户判别式已按进入前状态固化，平台视角失效。与事件入口（监听器委托 REQUIRES_NEW
 * service）同源的"会话先于方法体开启，租户须早于会话"契约，见
 * docs/architecture/dependency-rules.md 多租户四入口。
 *
 * <p>注意与 TokenAuthFilter 的协作：filter 在链尾 finally 调 TenantContextHolder.clear()，
 * 本切面在 controller 方法粒度先行还原，不依赖 clear 兜底。
 */
@Aspect
@Order(0)
public class TenantIgnoreAspect {

    @Around("@annotation(io.github.luckybearbear.forge.platform.security.TenantIgnore)"
            + " || @within(io.github.luckybearbear.forge.platform.security.TenantIgnore)")
    public Object aroundTenantIgnore(ProceedingJoinPoint joinPoint) throws Throwable {
        boolean previous = TenantContextHolder.isIgnore();
        TenantContextHolder.setIgnore(true);
        try {
            return joinPoint.proceed();
        } finally {
            TenantContextHolder.setIgnore(previous); // 还原进入前状态（null ≡ false，见 isIgnore）
        }
    }
}
