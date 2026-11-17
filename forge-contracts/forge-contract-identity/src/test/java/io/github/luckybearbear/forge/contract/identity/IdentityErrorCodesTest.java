package io.github.luckybearbear.forge.contract.identity;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class IdentityErrorCodesTest {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9]{1,9}-\\d{4}$");

    /** 其余契约模块的已登记错误码（contracts-first 冻结签名，防跨模块撞码）。 */
    private static final Set<String> FOREIGN_CODES = Set.of(
            "MOLD-0404",
            "TENANT-0404", "TENANT-0409", "TENANT-0423",
            "ORG-0404", "ORG-0409", "ORG-0414",
            "AUTH-0404", "AUTH-0409", "AUTH-0414");

    @Test
    void allCodesFollowSegmentedPatternAndIdentPrefix() {
        for (IdentityErrorCodes code : IdentityErrorCodes.values()) {
            assertThat(code.code()).matches(CODE_PATTERN);
            assertThat(code.code()).startsWith("IDENT-");
        }
    }

    @Test
    void codesAreUniqueWithinAndAcrossContractModules() {
        Set<String> own = Arrays.stream(IdentityErrorCodes.values())
                .map(IdentityErrorCodes::code)
                .collect(Collectors.toSet());
        assertThat(own).hasSize(IdentityErrorCodes.values().length);
        assertThat(own).doesNotContainAnyElementsOf(FOREIGN_CODES);
    }

    @Test
    void defaultMessagesAreNonEmptyWithSingleTrailingPlaceholder() {
        for (IdentityErrorCodes code : IdentityErrorCodes.values()) {
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
        assertThat(IdentityErrorCodes.IDENT_USER_NOT_FOUND.code()).isEqualTo("IDENT-0404");
        assertThat(IdentityErrorCodes.IDENT_USER_NOT_FOUND.defaultMessage()).isEqualTo("用户不存在: {}");
        assertThat(IdentityErrorCodes.IDENT_USERNAME_DUPLICATED.code()).isEqualTo("IDENT-0409");
        assertThat(IdentityErrorCodes.IDENT_USERNAME_DUPLICATED.defaultMessage()).isEqualTo("用户名已存在: {}");
        assertThat(IdentityErrorCodes.IDENT_BAD_CREDENTIALS.code()).isEqualTo("IDENT-0401");
        assertThat(IdentityErrorCodes.IDENT_BAD_CREDENTIALS.defaultMessage()).isEqualTo("用户名或密码错误");
        assertThat(IdentityErrorCodes.IDENT_PASSWORD_TOO_WEAK.code()).isEqualTo("IDENT-0400");
        assertThat(IdentityErrorCodes.IDENT_PASSWORD_TOO_WEAK.defaultMessage()).isEqualTo("密码强度不足");
    }
}
