package dev.axonia.access.internal;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import dev.axonia.ForbiddenException;
import dev.axonia.access.KnownPermissions;
import dev.axonia.access.Permission;
import dev.axonia.identity.IdentityProviderDirectory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@MockitoSettings
@ExtendWith(OutputCaptureExtension.class)
class DefaultAuthorizationGateTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RoleBindingRepository assignmentRepository;

    @Mock
    private CallerIdentity caller;

    private DefaultAuthorizationGate sut;

    @BeforeEach
    void setUp() {

        sut = new DefaultAuthorizationGate(roleRepository, assignmentRepository, caller,
                new KnownPermissions(List.of()));
    }

    // -------------------------------------------------------------------------
    // requirePermission - granted scenarios
    // -------------------------------------------------------------------------

    @Test
    void requirePermission_userBinding_granted() {

        // given
        givenCaller("u1", "default", List.of());
        when(assignmentRepository.findByOrganizationId("default")).thenReturn(List.of(assignment("USER", "u1", 100L)));
        when(roleRepository.findAllById(Set.of(100L))).thenReturn(List.of(roleWith(100L, "community_kind:write")));

        // then
        assertThatCode(() -> sut.requirePermission(Permission.COMMUNITY_KIND_WRITE)).doesNotThrowAnyException();
    }

    @Test
    void requirePermission_groupBinding_granted() {

        // given
        givenCaller("u1", "default", List.of("axonia-org-admins"));
        when(assignmentRepository.findByOrganizationId("default"))
                .thenReturn(List.of(assignment("GROUP", "axonia-org-admins", 1L)));
        when(roleRepository.findAllById(Set.of(1L))).thenReturn(List.of(roleWith(1L, "library_type:write")));

        // then
        assertThatCode(() -> sut.requirePermission(Permission.LIBRARY_TYPE_WRITE)).doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // requirePermission - denied scenarios
    // -------------------------------------------------------------------------

    @Test
    void requirePermission_noAssignment_deniedByDefault() {

        // given
        givenCaller("u1", "default", List.of());
        when(assignmentRepository.findByOrganizationId("default")).thenReturn(List.of());

        // then
        assertThatThrownBy(() -> sut.requirePermission(Permission.MEMBER_READ)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requirePermission_groupNotInCallerGroups_denied() {

        // given
        // The assignment is for a group the caller is NOT a member of
        givenCaller("u1", "default", List.of("other-group"));
        when(assignmentRepository.findByOrganizationId("default"))
                .thenReturn(List.of(assignment("GROUP", "axonia-org-admins", 1L)));
        // no roleRepository stub - caller's groups don't include the assignment's group, so roleIds is empty

        // then
        assertThatThrownBy(() -> sut.requirePermission(Permission.LIBRARY_TYPE_WRITE))
                .isInstanceOf(ForbiddenException.class);
    }

    // -------------------------------------------------------------------------
    // currentRoleIds
    // -------------------------------------------------------------------------

    @Test
    void currentRoleIds_returnsAllMatchingBindingRoleIds() {

        // given
        givenCaller("u1", "default", List.of());
        when(assignmentRepository.findByOrganizationId("default"))
                .thenReturn(List.of(assignment("USER", "u1", 100L), assignment("USER", "u1", 200L)));

        // then
        assertThat(sut.currentRoleIds()).containsExactlyInAnyOrder(100L, 200L);
    }

    @Test
    void currentRoleIds_noGroupsButGroupBindingsExist_logsTheSilentDenial(CapturedOutput output) {

        // given
        givenCaller("sub-1", "acme", List.of());
        when(assignmentRepository.findByOrganizationId("acme"))
                .thenReturn(List.of(assignment("GROUP", "group-guid-1", 9L)));

        // when
        var actual = sut.currentRoleIds();

        // then
        assertThat(actual).isEmpty();
        assertThat(output.getOut()).contains("resolved to no groups").contains("sub-1").contains("acme")
                .doesNotContain("acting as an agent token");
    }

    /**
     * An agent's empty {@code groups} is the intended shape, not a symptom of a missed SCIM sync, so the operator must
     * not be sent chasing a provisioning-scope problem that does not exist for this caller.
     */
    @Test
    void currentRoleIds_agentTokenNoGroupsButGroupBindingsExist_namesTheAgentNotTheProvisioningScope(
            CapturedOutput output) {

        // given
        givenCaller("sub-1", "acme", List.of());
        when(caller.isAgent()).thenReturn(true);
        when(assignmentRepository.findByOrganizationId("acme"))
                .thenReturn(List.of(assignment("GROUP", "group-guid-1", 9L)));

        // when
        var actual = sut.currentRoleIds();

        // then
        assertThat(actual).isEmpty();
        assertThat(output.getOut()).contains("acting as an agent token").contains("bound to it directly")
                .contains("sub-1").contains("acme").doesNotContain("provisioning scope");
    }

    @Test
    void currentRoleIds_noGroupsButUserBindingMatches_staysQuiet(CapturedOutput output) {

        // given
        // The caller is fully authorized through a USER binding; the Organization also happens to bind a role to a
        // group. Nothing is being denied, so nothing is silently denied.
        givenCaller("sub-1", "acme", List.of());
        when(assignmentRepository.findByOrganizationId("acme"))
                .thenReturn(List.of(assignment("USER", "sub-1", 9L), assignment("GROUP", "group-guid-1", 42L)));

        // when
        var actual = sut.currentRoleIds();

        // then
        assertThat(actual).containsExactly(9L);
        assertThat(output.getOut()).doesNotContain("resolved to no groups");
    }

    @Test
    void currentRoleIds_noGroupsAndNoGroupBindings_staysQuiet(CapturedOutput output) {

        // given
        givenCaller("sub-1", "acme", List.of());
        when(assignmentRepository.findByOrganizationId("acme")).thenReturn(List.of(assignment("USER", "sub-1", 9L)));

        // when
        var actual = sut.currentRoleIds();

        // then
        assertThat(actual).containsExactly(9L);
        assertThat(output.getOut()).doesNotContain("resolved to no groups");
    }

    @Test
    void currentRoleIds_hasGroupsAndGroupBindingsExist_staysQuiet(CapturedOutput output) {

        // given
        givenCaller("sub-1", "acme", List.of("group-guid-1"));
        when(assignmentRepository.findByOrganizationId("acme"))
                .thenReturn(List.of(assignment("GROUP", "group-guid-1", 9L)));

        // when
        var actual = sut.currentRoleIds();

        // then
        assertThat(actual).containsExactly(9L);
        assertThat(output.getOut()).doesNotContain("resolved to no groups");
    }

    // -------------------------------------------------------------------------
    // currentPermissions
    // -------------------------------------------------------------------------

    @Test
    void currentPermissions_aCodeThisEditionDoesNotKnowGrantsNothing() {

        // given - a role row carrying a code no module of this edition declares, beside one it does
        givenCaller("u1", "default", List.of());
        when(assignmentRepository.findByOrganizationId("default")).thenReturn(List.of(assignment("USER", "u1", 7L)));
        when(roleRepository.findAllById(Set.of(7L)))
                .thenReturn(List.of(roleWith(7L, "idp_connection:read", "member:read")));

        // then
        assertThat(sut.currentPermissions()).containsExactly(Permission.MEMBER_READ);
    }

    // -------------------------------------------------------------------------
    // Auth-path isolation invariant
    // -------------------------------------------------------------------------

    /**
     * Structural proof that {@link DefaultAuthorizationGate} has NO dependency on {@link IdentityProviderDirectory}.
     *
     * <p>
     * Group membership for access decisions is resolved from the JWT {@code groups} claim via
     * {@link CallerIdentity#groups()}, supplemented only from the local SCIM-synced tables when that claim is empty,
     * and never by an outbound IdP call at request time. Adding such a dependency would introduce a latency-sensitive
     * outbound call on every authorisation check and would break offline/cached-token scenarios.
     *
     * <p>
     * The constructor call in {@link #setUp()} (no {@code IdentityProviderDirectory} argument) already proves this
     * structurally at compile time. This test makes the invariant explicit and machine-verifiable so it cannot silently
     * regress.
     */
    @Test
    void authorizationGate_hasNoDependencyOnIdentityProviderDirectory() {

        // when
        var fieldTypeNames = Arrays.stream(DefaultAuthorizationGate.class.getDeclaredFields()).map(Field::getType)
                .map(Class::getName).toList();

        // then
        assertThat(fieldTypeNames)
                .as("DefaultAuthorizationGate must not depend on IdentityProviderDirectory - "
                        + "group resolution must stay in the JWT (CurrentToken.groups())")
                .doesNotContain(IdentityProviderDirectory.class.getName());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void givenCaller(String subject, String organizationId, List<String> groups) {
        when(caller.subject()).thenReturn(subject);
        when(caller.organizationId()).thenReturn(organizationId);
        when(caller.groups()).thenReturn(groups);
    }

    private RoleBindingEntity assignment(String subjectType, String subjectId, Long roleId) {
        return RoleBindingEntity.builder().subjectType(subjectType).subjectId(subjectId).roleId(roleId)
                .organizationId("default").build();
    }

    private RoleEntity roleWith(Long id, String... codes) {
        var permissions = new java.util.HashSet<RolePermissionEntity>();
        for (var code : codes) {
            permissions.add(RolePermissionEntity.builder().permission(code).build());
        }
        return RoleEntity.builder().id(id).organizationId("default").name("Role-" + id).permissions(permissions)
                .build();
    }
}
