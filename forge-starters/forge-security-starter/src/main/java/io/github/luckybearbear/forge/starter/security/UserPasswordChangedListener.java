package io.github.luckybearbear.forge.starter.security;

import io.github.luckybearbear.forge.contract.identity.UserPasswordChanged;
import io.github.luckybearbear.forge.platform.security.token.TokenStore;
import org.springframework.context.event.EventListener;

/**
 * 改密会话失效监听（Task 4 安全输入 2）：消费 identity 契约事件 {@link UserPasswordChanged}，
 * 吊销该用户名下全部 token——密码已变更，旧凭据签发的会话不再可信。
 *
 * <p><b>同步 @EventListener（非 @TransactionalEventListener/@ApplicationModuleListener）</b>：
 * 吊销按 userId 全域（token 键空间无租户语义）——不 set 租户上下文；同步执行保证改密调用返回时
 * 旧 token 已不可用（identity 集成测试即断言此语义）。事件在发布方业务事务内到达：若该事务随后
 * 回滚，吊销已生效（安全方向的偏差——用户重登即可，无危害）；事务提交路径为常态。
 *
 * <p>listener 归属 security-starter 而非 authorization：token 生命周期管理是安全能力，
 * 消费的是 identity 契约（跨域事件），与权限缓存失效（authorization 域内）无关。
 */
public class UserPasswordChangedListener {

    private final TokenStore tokenStore;

    public UserPasswordChangedListener(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @EventListener
    public void on(UserPasswordChanged event) {
        tokenStore.revokeByUserId(event.userId());
    }
}
