package dev.axonia.access.adapter.in.web;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.access.api.AuthorizationGate;
import dev.axonia.access.api.Permission;
import dev.axonia.access.application.MyRole;
import dev.axonia.access.application.RoleBindingService;
import dev.axonia.access.application.RoleService;
import dev.axonia.shared.api.security.CurrentToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@CoreWebMvcTest(controllers = AccessController.class)
class AccessControllerMeTest extends ControllerTestSupport {

    @MockitoBean
    private AuthorizationGate gate;

    @MockitoBean
    private RoleService roleService;

    @MockitoBean
    private RoleBindingService bindingService;

    @Test
    void me_returnsPermissions() throws Exception {

        // given
        when(gate.currentPermissions()).thenReturn(Set.of(Permission.MEMBER_READ));

        // when
        var actual = mvc.get().uri("/axonia/access/me");

        // then
        actual.assertThat().hasStatusOk().bodyJson().extractingPath("$.permissions[0]").isEqualTo("member:read");
    }

    @Test
    void me_returnsTheCallersRolesByIdAndNameAndNothingElse() throws Exception {

        // given
        when(gate.currentPermissions()).thenReturn(Set.of(Permission.MEMBER_READ));
        when(roleService.myRoles()).thenReturn(List.of(new MyRole(7L, "Admin"), new MyRole(9L, "Auditor")));

        // when
        var actual = mvc.get().uri("/axonia/access/me");

        // then - the id travels, so the profile chip tints a role exactly as the admin roster does, and
        // nothing else does: what a role grants is already answered once, by permissions
        actual.assertThat().hasStatusOk().bodyJson().extractingPath("$.roles")
                .isEqualTo(List.of(Map.of("id", 7, "name", "Admin"), Map.of("id", 9, "name", "Auditor")));
    }

    @Test
    void me_returnsAnEmptyRolesArrayForAnEnterpriseMemberWithNoBinding() throws Exception {

        // given - Member is implicit in an enterprise organization, so no binding rows exist
        when(gate.currentPermissions()).thenReturn(Set.of());
        when(roleService.myRoles()).thenReturn(List.of());

        // when
        var actual = mvc.get().uri("/axonia/access/me");

        // then
        actual.assertThat().hasStatusOk().bodyJson().extractingPath("$.roles").isEqualTo(List.of());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void me_answersAnEmptyCallerForATokenThatNamesNoOrganization(boolean defaultOrganizationFallback) {

        // given
        CurrentToken.setAllowDefaultFallback(defaultOrganizationFallback);
        try {
            // when
            var actual = mvc.get().uri("/axonia/access/me").with(jwt().jwt(token -> token.subject("nomad"))).exchange();

            // then
            assertThat(actual).hasStatusOk().bodyJson().isEqualTo("{\"permissions\":[],\"roles\":[]}");
            verifyNoInteractions(gate, roleService);
        } finally {
            CurrentToken.setAllowDefaultFallback(true);
        }
    }
}
