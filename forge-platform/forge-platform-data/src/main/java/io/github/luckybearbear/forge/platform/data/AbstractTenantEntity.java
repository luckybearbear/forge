package io.github.luckybearbear.forge.platform.data;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.TenantId;

/**
 * 租户实体基线：@TenantId 由 Hibernate 自动过滤/注入（spec §6.1 入口 2）。
 *
 * <p><b>平台视角写守护（2b 前置加固）</b>：平台视角（ignore）下判别式解析为
 * {@link ForgeTenantIdentifierResolver#PLATFORM_TENANT_ID}（"0"，租户表恒空）——此时写入即
 * 数据黑洞（行落 tenant_id='0'，任何租户视角永不可见）。守护在 @PrePersist fail-fast 拒绝：
 * 由 forge-data-starter 装配时经 {@link #setWriteGuard(TenantWriteGuard)} 注入
 * （core 的 {@code CurrentTenantSupplier}，静态 supplier 注入避免 data→security 依赖）；
 * 未注入时跳过检查（独立单测场景）。
 */
@MappedSuperclass
public abstract class AbstractTenantEntity extends AbstractAuditableEntity {

    /** 由 forge-data-starter 在装配时注入（core 的 CurrentTenantSupplier）；未注入时跳过检查（独立单测场景）。 */
    public interface TenantWriteGuard {

        /** 平台视角（判别式将解析为平台租户）时拒绝落库。 */
        boolean disallowPlatformWrites();
    }

    private static volatile TenantWriteGuard writeGuard;

    public static void setWriteGuard(TenantWriteGuard guard) {
        writeGuard = guard;
    }

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    /**
     * 平台视角写守护：@PrePersist 声明保留在基类 {@link AbstractAuditableEntity#assignId()}——
     * JPA 回调经实例虚分派执行本覆写（单一回调声明，避免基类/子类双重触发），先补齐 id 赋值
     * 语义（super），再做守护 fail-fast。
     */
    @Override
    protected void assignId() {
        super.assignId();
        TenantWriteGuard guard = writeGuard;
        if (guard != null && guard.disallowPlatformWrites()) {
            throw new IllegalStateException(
                    "refusing to persist tenant entity under platform view (tenant_id would be '"
                            + ForgeTenantIdentifierResolver.PLATFORM_TENANT_ID + "'): " + getClass().getSimpleName());
        }
    }

    public String getTenantId() { return tenantId; }

    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
