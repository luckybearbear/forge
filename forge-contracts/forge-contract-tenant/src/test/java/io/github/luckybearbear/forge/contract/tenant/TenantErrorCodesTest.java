package io.github.luckybearbear.forge.contract.tenant;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TenantErrorCodesTest {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9]{1,9}-\\d{4}$");

    /** 其余契约模块的已登记错误码（contracts-first 冻结签名，防跨模块撞码）。 */
    private static final Set<String> FOREIGN_CODES = Set.of(
            "MOLD-0404",
            "IDENT-0400", "IDENT-0401", "IDENT-0404", "IDENT-0409",
            "ORG-0404", "ORG-0409", "ORG-0414",
            "AUTH-0404", "AUTH-0409", "AUTH-0414");

    @Test
    void allCodesFollowSegmentedPatternAndTenantPrefix() {
        for (TenantErrorCodes code : TenantErrorCodes.values()) {
            assertThat(code.code()).matches(CODE_PATTERN);
            assertThat(code.code()).startsWith("TENANT-");
        }
    }

    @Test
    void codesAreUniqueWithinAndAcrossContractModules() {
        Set<String> own = Arrays.stream(TenantErrorCodes.values())
                .map(TenantErrorCodes::code)
                .collect(Collectors.toSet());
        assertThat(own).hasSize(TenantErrorCodes.values().length);
        assertThat(own).doesNotContainAnyElementsOf(FOREIGN_CODES);
    }

    @Test
    void defaultMessagesAreNonEmptyWithSingleTrailingPlaceholder() {
        for (TenantErrorCodes code : TenantErrorCodes.values()) {
            String message = code.defaultMessage();
            assertThat(message).isNotBlank();
            int placeholders = (message.length() - message.replace("{}", "").length()) / 2;
            if (placeholders > 0) {
                assertThat(placeholders).as("%s 占位符个数", code).isEqualTo(1);
                assertThat(message).as("%s 占位符位置", code).endsWith(": {}");
            }
        }
    }

    @Test
    void pinnedContractCodesAndMessages() {
        assertThat(TenantErrorCodes.TENANT_NOT_FOUND.code()).isEqualTo("TENANT-0404");
        assertThat(TenantErrorCodes.TENANT_NOT_FOUND.defaultMessage()).isEqualTo("租户不存在: {}");
        assertThat(TenantErrorCodes.TENANT_CODE_DUPLICATED.code()).isEqualTo("TENANT-0409");
        assertThat(TenantErrorCodes.TENANT_CODE_DUPLICATED.defaultMessage()).isEqualTo("租户编码已存在: {}");
        assertThat(TenantErrorCodes.TENANT_DISABLED.code()).isEqualTo("TENANT-0423");
        assertThat(TenantErrorCodes.TENANT_DISABLED.defaultMessage()).isEqualTo("租户已停用");
    }
}
