package io.github.luckybearbear.forge.platform.web;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * normalizePrefix 斜杠归一单测（终审 Important #2 补齐）：自定义前缀值
 * （{@code app-api}、{@code /app-api/}、空串等）不产出畸形 pattern。
 */
class ForgeApiPrefixRequestMappingHandlerMappingTest {

    @Test
    void missingLeadingSlashIsAdded() {
        assertThat(ForgeApiPrefixRequestMappingHandlerMapping.normalizePrefix("app-api"))
                .isEqualTo("/app-api");
    }

    @Test
    void trailingSlashesAreStripped() {
        assertThat(ForgeApiPrefixRequestMappingHandlerMapping.normalizePrefix("/app-api/"))
                .isEqualTo("/app-api");
        assertThat(ForgeApiPrefixRequestMappingHandlerMapping.normalizePrefix("/app-api//"))
                .isEqualTo("/app-api");
    }

    @Test
    void blankStaysEmptyAsNoPrefix() {
        assertThat(ForgeApiPrefixRequestMappingHandlerMapping.normalizePrefix("")).isEmpty();
        assertThat(ForgeApiPrefixRequestMappingHandlerMapping.normalizePrefix("   ")).isEmpty();
    }
}
