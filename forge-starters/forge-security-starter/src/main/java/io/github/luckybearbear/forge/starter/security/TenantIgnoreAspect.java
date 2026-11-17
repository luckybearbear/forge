package io.github.luckybearbear.forge.starter.security;

import io.github.luckybearbear.forge.platform.security.TenantContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * @TenantIgnore AOP（spec §6.1 平台视角）：进入标注方法/类时切换 ignore=true，方法体（含其
 * ORM 查询）以平台视角执行——TenantResolver 将解析为 PLATFORM_TENANT_ID（"0"，租户表恒空，
 * 非跨租户全量）；finally 恢复进入前的原值（嵌套标注可正确还原外层状态）。
 *
 * <p>注意与 TokenAuthFilter 的协作：filter 在链尾 finally 调 TenantContextHolder.clear()，
 * 本切面在 controller 方法粒度先行还原，不依赖 clear 兜底。
 */
@Aspect
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
