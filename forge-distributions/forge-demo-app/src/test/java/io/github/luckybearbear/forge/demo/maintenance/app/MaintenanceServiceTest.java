package io.github.luckybearbear.forge.demo.maintenance.app;

import io.github.luckybearbear.forge.contract.mold.MoldDTO;
import io.github.luckybearbear.forge.contract.mold.MoldQueryService;
import io.github.luckybearbear.forge.contract.mold.MoldStatus;
import io.github.luckybearbear.forge.demo.maintenance.domain.MaintenanceOrderJpaEntity;
import io.github.luckybearbear.forge.demo.maintenance.domain.MaintenanceOrderRepository;
import io.github.luckybearbear.forge.platform.core.BusinessException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Task 11 改写：注册表（内存）→ mock repository 的服务单测。
 * 取舍：occurredAt 不再进 DTO（createdAt 由 JPA 审计落库填充，集成测试覆盖），
 * createFromEvent 签名相应收窄为 (moldId, description)。
 */
class MaintenanceServiceTest {

    private MaintenanceOrderRepository repository;
    private MaintenanceService service;

    @BeforeEach
    void setUp() {
        repository = mock(MaintenanceOrderRepository.class);
        when(repository.save(any(MaintenanceOrderJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        MoldQueryService moldQueryService = moldId ->
                new MoldDTO(moldId, "测试模具", "两腔", MoldStatus.IN_SERVICE);
        service = new MaintenanceService(repository, moldQueryService);
    }

    private static MaintenanceOrderJpaEntity order(String id, String moldId) {
        return new MaintenanceOrderJpaEntity(moldId, "测试模具", "desc-" + id, "OPEN");
    }

    @Test
    void createsOrderFromEventEnrichedByContractQuery() {
        var dto = service.createFromEvent("m-1", "导柱磨损");
        assertThat(dto.moldId()).isEqualTo("m-1");
        assertThat(dto.moldName()).isEqualTo("测试模具");  // 由 MoldQueryService 补全
        assertThat(dto.description()).isEqualTo("导柱磨损");
        assertThat(dto.status()).isEqualTo("OPEN");
    }

    @Test
    void listsOrdersByMold() {
        when(repository.findByMoldId("m-1")).thenReturn(List.of(order("o-1", "m-1"), order("o-2", "m-1")));
        when(repository.findByMoldId("m-2")).thenReturn(List.of(order("o-3", "m-2")));
        assertThat(service.findByMoldId("m-1")).hasSize(2);
        assertThat(service.findByMoldId("m-2")).hasSize(1);
    }

    @Test
    void findByIdThrowsMaintNotFoundWith404() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById("missing"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> {
                            assertThat(ex.errorCode().code()).isEqualTo("MAINT-0404");
                            assertThat(ex.httpStatus()).isEqualTo(404);
                        });
    }
}
