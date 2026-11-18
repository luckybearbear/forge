package io.github.luckybearbear.forge.starter.security;

import io.github.luckybearbear.forge.contract.tenant.TenantDisabled;
import io.github.luckybearbear.forge.platform.security.token.TokenStore;
import org.springframework.context.event.EventListener;

/**
 * 租户停用会话失效监听（Phase 3 前置 Task 1）：消费 tenant 契约事件 {@link TenantDisabled}，
 * 吊销该租户名下全部存量 token——租户已停用，其用户的会话不再可信（即时全下线）。
 *
 * <p><b>同步 @EventListener（非 @TransactionalEventListener/@ApplicationModuleListener）</b>：
 * 吊销按 tenantId 全域（token 键空间无租户上下文语义）——不 set 租户上下文；同步执行保证停用
 * 调用返回时该租户存量 token 已全部不可用。事件在发布方业务事务内到达：若该事务随后回滚，
 * 吊销已生效（安全方向的偏差——重新启用后重登即可，无危害）；事务提交路径为常态。
 *
 * <p>listener 归属 security-starter（同 {@link UserPasswordChangedListener} 先例）：token 生命
 * 周期管理是安全能力，消费的是 tenant 契约（跨域事件）。
 */
public class TenantDisabledListener {

    private final TokenStore tokenStore;

    public TenantDisabledListener(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @EventListener
    public void on(TenantDisabled event) {
        tokenStore.revokeByTenantId(event.tenantId());
    }
}
