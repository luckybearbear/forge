package io.github.luckybearbear.forge.contract.mold;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MoldErrorCodesTest {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9]{1,9}-\\d{4}$");

    @Test
    void allCodesFollowSegmentedPattern() {
        for (MoldErrorCodes code : MoldErrorCodes.values()) {
            assertThat(code.code()).matches(CODE_PATTERN);
        }
    }

    @Test
    void notFoundCarriesPlaceholderMessage() {
        assertThat(MoldErrorCodes.MOLD_NOT_FOUND.defaultMessage()).isEqualTo("模具不存在: {}");
    }
}
