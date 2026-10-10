package dev.axonia.access.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionTest {

    @Test
    void aPermissionIsItsCode() {

        assertThat(new Permission("community_kind:write")).isEqualTo(Permission.COMMUNITY_KIND_WRITE)
                .hasSameHashCodeAs(Permission.COMMUNITY_KIND_WRITE);
    }

    @Test
    void travelsTheWireAsItsCode() {

        assertThat(JsonMapper.builder().build().writeValueAsString(Permission.ROLE_ASSIGN))
                .isEqualTo("\"role:assign\"");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"role", "role:", ":write", "Role:write", "role:write:all", "role write"})
    void refusesACodeThatIsNotAResourceAndAnAction(String code) {

        assertThatThrownBy(() -> new Permission(code)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a resource and an action");
    }

    @Test
    void codes_areTheCanonicalWireStrings() {
        assertThat(Permission.SCIM_READ.code()).isEqualTo("scim:read");
        assertThat(Permission.SCIM_WRITE.code()).isEqualTo("scim:write");
        assertThat(Permission.ORGANIZATION_DELETE.code()).isEqualTo("organization:delete");
        assertThat(Permission.OWNERSHIP_TRANSFER.code()).isEqualTo("ownership:transfer");
        assertThat(Permission.COMMUNITY_CREATE.code()).isEqualTo("community:create");
        assertThat(Permission.COMMUNICATION_WRITE.code()).isEqualTo("communication:write");
        assertThat(Permission.REACTION_READ.code()).isEqualTo("reaction:read");
        assertThat(Permission.REACTION_WRITE.code()).isEqualTo("reaction:write");
    }

    @Test
    void retiredCodes_areGone() {
        var codes = Permission.OPEN.stream().map(Permission::code).toList();
        assertThat(codes).doesNotContain("organization:read", "idp_connection:read", "idp_connection:write");
    }

    @Test
    void catalog_hasExactlyTheTwentyOneOpenCodes() {
        var codes = Permission.OPEN.stream().map(Permission::code).toList();
        assertThat(codes).containsExactlyInAnyOrder("organization:write", "organization:delete", "ownership:transfer",
                "member:read", "member:write", "role:read", "role:assign", "role:write", "scim:read", "scim:write",
                "community_kind:read", "community_kind:write", "library_type:read", "library_type:write",
                "section_type:read", "section_type:write", "community:create", "organization:export",
                "communication:write", "reaction:read", "reaction:write");
    }
}
