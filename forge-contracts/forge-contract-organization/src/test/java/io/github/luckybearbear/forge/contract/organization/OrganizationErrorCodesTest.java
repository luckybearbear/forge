package io.github.luckybearbear.forge.contract.organization;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OrganizationErrorCodesTest {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9]{1,9}-\\d{4}$");

    /** 其余契约模块的已登记错误码（contracts-first 冻结签名，防跨模块撞码）。 */
    private static final Set<String> FOREIGN_CODES = Set.of(
            "MOLD-0404",
            "TENANT-0404", "TENANT-0409", "TENANT-0423",
            "IDENT-0400", "IDENT-0401", "IDENT-0404", "IDENT-0409",
            "AUTH-0404", "AUTH-0409", "AUTH-0414");

    @Test
    void allCodesFollowSegmentedPatternAndOrgPrefix() {
        for (OrganizationErrorCodes code : OrganizationErrorCodes.values()) {
            assertThat(code.code()).matches(CODE_PATTERN);
            assertThat(code.code()).startsWith("ORG-");
        }
    }

    @Test
    void codesAreUniqueWithinAndAcrossContractModules() {
        Set<String> own = Arrays.stream(OrganizationErrorCodes.values())
                .map(OrganizationErrorCodes::code)
                .collect(Collectors.toSet());
        assertThat(own).hasSize(OrganizationErrorCodes.values().length);
        assertThat(own).doesNotContainAnyElementsOf(FOREIGN_CODES);
    }

    @Test
    void defaultMessagesAreNonEmptyWithSingleTrailingPlaceholder() {
        for (OrganizationErrorCodes code : OrganizationErrorCodes.values()) {
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
        assertThat(OrganizationErrorCodes.ORG_UNIT_NOT_FOUND.code()).isEqualTo("ORG-0404");
        assertThat(OrganizationErrorCodes.ORG_UNIT_NOT_FOUND.defaultMessage()).isEqualTo("组织单元不存在: {}");
        assertThat(OrganizationErrorCodes.ORG_CYCLE_DETECTED.code()).isEqualTo("ORG-0409");
        assertThat(OrganizationErrorCodes.ORG_CYCLE_DETECTED.defaultMessage()).isEqualTo("组织树检测到环: {}");
        // 岗位与组织单元不得撞码：ORG-0414（brief 自纠，早期计划正文误写 ORG-0404）
        assertThat(OrganizationErrorCodes.ORG_POSITION_NOT_FOUND.code()).isEqualTo("ORG-0414");
        assertThat(OrganizationErrorCodes.ORG_POSITION_NOT_FOUND.defaultMessage()).isEqualTo("岗位不存在: {}");
    }
}
