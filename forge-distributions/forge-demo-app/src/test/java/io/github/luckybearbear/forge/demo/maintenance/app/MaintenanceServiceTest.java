package io.github.luckybearbear.forge.demo.maintenance.app;

import io.github.luckybearbear.forge.contract.mold.MoldDTO;
import io.github.luckybearbear.forge.contract.mold.MoldQueryService;
import io.github.luckybearbear.forge.contract.mold.MoldStatus;
import io.github.luckybearbear.forge.demo.maintenance.domain.MaintenanceRegistry;
import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.platform.core.IdGenerator;
import io.github.luckybearbear.forge.platform.core.UuidV7Generator;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaintenanceServiceTest {

    private MaintenanceService service;

    @BeforeEach
    void setUp() {
        MoldQueryService moldQueryService = moldId ->
                new MoldDTO(moldId, "测试模具", "两腔", MoldStatus.IN_SERVICE);
        IdGenerator idGenerator = new UuidV7Generator();
        service = new MaintenanceService(new MaintenanceRegistry(), idGenerator, moldQueryService);
    }

    @Test
    void createsOrderFromEventEnrichedByContractQuery() {
        var dto = service.createFromEvent("m-1", "导柱磨损", Instant.now());
        assertThat(dto.moldId()).isEqualTo("m-1");
        assertThat(dto.moldName()).isEqualTo("测试模具");  // 由 MoldQueryService 补全
        assertThat(dto.description()).isEqualTo("导柱磨损");
        assertThat(dto.status()).isEqualTo("OPEN");
    }

    @Test
    void listsOrdersByMold() {
        service.createFromEvent("m-1", "a", Instant.now());
        service.createFromEvent("m-1", "b", Instant.now());
        service.createFromEvent("m-2", "c", Instant.now());
        assertThat(service.findByMoldId("m-1")).hasSize(2);
        assertThat(service.findByMoldId("m-2")).hasSize(1);
    }

    @Test
    void findByIdThrowsMaintNotFoundWith404() {
        assertThatThrownBy(() -> service.findById("missing"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> {
                            assertThat(ex.errorCode()).isEqualTo(MaintenanceErrorCodes.MAINT_NOT_FOUND);
                            assertThat(ex.errorCode().code()).isEqualTo("MAINT-0404");
                            assertThat(ex.httpStatus()).isEqualTo(404);
                        });
    }
}
