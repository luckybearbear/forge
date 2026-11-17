package io.github.luckybearbear.forge.demo.mold.app;

import io.github.luckybearbear.forge.contract.mold.MoldRepairRequested;
import io.github.luckybearbear.forge.contract.mold.MoldStatus;
import io.github.luckybearbear.forge.demo.mold.api.RegisterMoldRequest;
import io.github.luckybearbear.forge.demo.mold.api.RequestRepairRequest;
import io.github.luckybearbear.forge.demo.mold.domain.MoldJpaEntity;
import io.github.luckybearbear.forge.demo.mold.domain.MoldRepository;
import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.platform.test.TenantTestSupport;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Task 11 改写：注册表（内存）→ mock repository 的服务单测，聚焦服务逻辑本身
 * （租户隔离/审计/异步投递由 DemoTenantIsolationIntegrationTest 在真实 PG 上验收）。
 * 取舍：mock 无 @PrePersist，主键赋值断言移至集成测试（register 仅断言业务字段）。
 */
class MoldServiceTest {

    private MoldRepository repository;
    private ApplicationEventPublisher publisher;
    private MoldService service;

    @BeforeEach
    void setUp() {
        repository = mock(MoldRepository.class);
        when(repository.save(any(MoldJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        publisher = mock(ApplicationEventPublisher.class);
        service = new MoldService(repository, publisher);
    }

    private static MoldJpaEntity existing(String id) {
        var entity = new MoldJpaEntity("测试模具", "两腔", MoldStatus.IN_SERVICE);
        entity.setId(id);
        return entity;
    }

    @Test
    void registerCreatesInServiceMold() {
        var dto = service.register(new RegisterMoldRequest("测试模具", "两腔"));
        assertThat(dto.name()).isEqualTo("测试模具");
        assertThat(dto.specification()).isEqualTo("两腔");
        assertThat(dto.status()).isEqualTo(MoldStatus.IN_SERVICE);
    }

    @Test
    void findByIdThrowsMoldNotFoundWith404() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById("missing"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> {
                            assertThat(ex.errorCode().code()).isEqualTo("MOLD-0404");
                            assertThat(ex.httpStatus()).isEqualTo(404);
                        });
    }

    @Test
    void requestRepairPublishesContractEventCarryingCurrentTenant() {
        when(repository.findById("m-1")).thenReturn(Optional.of(existing("m-1")));
        TenantTestSupport.runAs("t-unit",
                () -> service.requestRepair("m-1", new RequestRepairRequest("导柱磨损")));

        ArgumentCaptor<MoldRepairRequested> captor = ArgumentCaptor.forClass(MoldRepairRequested.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo("t-unit"); // 事件显式携带发布方租户
        assertThat(captor.getValue().moldId()).isEqualTo("m-1");
        assertThat(captor.getValue().description()).isEqualTo("导柱磨损");
        assertThat(captor.getValue().occurredAt()).isNotNull();
    }

    @Test
    void requestRepairOnMissingMoldPublishesNothing() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        TenantTestSupport.runAs("t-unit", () -> {
            assertThatThrownBy(() -> service.requestRepair("missing", new RequestRepairRequest("x")))
                    .isInstanceOf(BusinessException.class);
        });
        verify(publisher, never()).publishEvent(any());
    }
}
