package io.github.luckybearbear.forge.service.identity;

import io.github.luckybearbear.forge.contract.identity.UserDTO;
import io.github.luckybearbear.forge.contract.identity.UserQueryService;
import io.github.luckybearbear.forge.service.identity.domain.IdentityUserEntity;
import io.github.luckybearbear.forge.service.identity.domain.IdentityUserRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 契约 {@link UserQueryService} 实现：跨模块同步查询入口（审计回显、组织挂人等）。
 * 栖身模块根包（随 IdentityServiceAutoConfiguration 的 @ComponentScan 暴露为契约 bean）。
 *
 * <p><b>租户视角契约</b>：identity_user 为租户表——@TenantId 判别式过滤总生效，本查询只在
 * 调用方租户上下文内命中（web 请求链自动；异步侧显式 set/finally clear）；无上下文/平台视角
 * 恒空（平台视角租户表恒空，spec §6.1）。userId 为 UUIDv7 全局唯一但<b>不做</b>跨租户解析——
 * 越租户查询按不可见处理（多租户隔离默认）。
 */
@Service
public class IdentityUserQueryServiceImpl implements UserQueryService {

    private final IdentityUserRepository repository;

    public IdentityUserQueryServiceImpl(IdentityUserRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserDTO> findById(String userId) {
        return repository.findById(userId).map(this::toDto);
    }

    private UserDTO toDto(IdentityUserEntity entity) {
        return new UserDTO(entity.getId(), entity.getTenantId(), entity.getUsername(),
                entity.getPhone(), entity.getStatus().name());
    }
}
