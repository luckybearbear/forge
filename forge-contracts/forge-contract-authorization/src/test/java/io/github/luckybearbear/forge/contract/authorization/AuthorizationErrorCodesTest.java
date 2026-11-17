package io.github.luckybearbear.forge.contract.authorization;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationErrorCodesTest {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9]{1,9}-\\d{4}$");

    /** 其余契约模块的已登记错误码（contracts-first 冻结签名，防跨模块撞码）。 */
    private static final Set<String> FOREIGN_CODES = Set.of(
            "MOLD-0404",
            "TENANT-0404", "TENANT-0409", "TENANT-0423",
            "IDENT-0400", "IDENT-0401", "IDENT-0404", "IDENT-0409",
            "ORG-0404", "ORG-0409", "ORG-0414");

    @Test
    void allCodesFollowSegmentedPatternAndAuthPrefix() {
        for (AuthorizationErrorCodes code : AuthorizationErrorCodes.values()) {
            assertThat(code.code()).matches(CODE_PATTERN);
            assertThat(code.code()).startsWith("AUTH-");
        }
    }

    @Test
    void codesAreUniqueWithinAndAcrossContractModules() {
        Set<String> own = Arrays.stream(AuthorizationErrorCodes.values())
                .map(AuthorizationErrorCodes::code)
                .collect(Collectors.toSet());
        assertThat(own).hasSize(AuthorizationErrorCodes.values().length);
        assertThat(own).doesNotContainAnyElementsOf(FOREIGN_CODES);
    }

    @Test
    void defaultMessagesAreNonEmptyWithSingleTrailingPlaceholder() {
        for (AuthorizationErrorCodes code : AuthorizationErrorCodes.values()) {
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
        assertThat(AuthorizationErrorCodes.AUTH_ROLE_NOT_FOUND.code()).isEqualTo("AUTH-0404");
        assertThat(AuthorizationErrorCodes.AUTH_ROLE_NOT_FOUND.defaultMessage()).isEqualTo("角色不存在: {}");
        assertThat(AuthorizationErrorCodes.AUTH_PERMISSION_NOT_FOUND.code()).isEqualTo("AUTH-0414");
        assertThat(AuthorizationErrorCodes.AUTH_PERMISSION_NOT_FOUND.defaultMessage()).isEqualTo("权限点不存在: {}");
        assertThat(AuthorizationErrorCodes.AUTH_ROLE_CODE_DUPLICATED.code()).isEqualTo("AUTH-0409");
        assertThat(AuthorizationErrorCodes.AUTH_ROLE_CODE_DUPLICATED.defaultMessage()).isEqualTo("角色编码已存在: {}");
    }
}
