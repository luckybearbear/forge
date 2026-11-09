package io.github.luckybearbear.forge.demo.mold.app;

import io.github.luckybearbear.forge.contract.mold.MoldRepairRequested;
import io.github.luckybearbear.forge.contract.mold.MoldStatus;
import io.github.luckybearbear.forge.demo.mold.api.RegisterMoldRequest;
import io.github.luckybearbear.forge.demo.mold.api.RequestRepairRequest;
import io.github.luckybearbear.forge.demo.mold.domain.MoldRegistry;
import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.platform.core.IdGenerator;
import io.github.luckybearbear.forge.platform.core.UuidV7Generator;
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

class MoldServiceTest {

    private ApplicationEventPublisher publisher;
    private MoldService service;

    @BeforeEach
    void setUp() {
        publisher = mock(ApplicationEventPublisher.class);
        IdGenerator idGenerator = new UuidV7Generator();
        service = new MoldService(new MoldRegistry(), idGenerator, publisher);
    }

    @Test
    void registerCreatesInServiceMold() {
        var dto = service.register(new RegisterMoldRequest("测试模具", "两腔"));
        assertThat(dto.name()).isEqualTo("测试模具");
        assertThat(dto.status()).isEqualTo(MoldStatus.IN_SERVICE);
        assertThat(dto.id()).isNotBlank();
    }

    @Test
    void findByIdThrowsMoldNotFoundWith404() {
        assertThatThrownBy(() -> service.findById("missing"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> {
                            assertThat(ex.errorCode().code()).isEqualTo("MOLD-0404");
                            assertThat(ex.httpStatus()).isEqualTo(404);
                        });
    }

    @Test
    void requestRepairPublishesContractEvent() {
        var dto = service.register(new RegisterMoldRequest("测试模具", "两腔"));
        service.requestRepair(dto.id(), new RequestRepairRequest("导柱磨损"));

        ArgumentCaptor<MoldRepairRequested> captor = ArgumentCaptor.forClass(MoldRepairRequested.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().moldId()).isEqualTo(dto.id());
        assertThat(captor.getValue().description()).isEqualTo("导柱磨损");
        assertThat(captor.getValue().occurredAt()).isNotNull();
    }

    @Test
    void requestRepairOnMissingMoldPublishesNothing() {
        assertThatThrownBy(() -> service.requestRepair("missing", new RequestRepairRequest("x")))
                .isInstanceOf(BusinessException.class);
        verify(publisher, never()).publishEvent(any());
    }
}
