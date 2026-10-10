package dev.axonia.access.application;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import dev.axonia.access.api.AuthorizationGate;
import dev.axonia.access.api.Permission;
import dev.axonia.access.application.port.out.OrganizationContext;
import dev.axonia.access.application.port.out.RoleBindingStore;
import dev.axonia.access.application.port.out.RoleStore;
import dev.axonia.access.domain.RoleDefinition;
import dev.axonia.access.domain.SubjectBinding;
import dev.axonia.identity.api.DirectoryGroups;
import dev.axonia.identity.api.UserDirectory;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@MockitoSettings
class AdminUserServiceTest {

    @Mock
    private AuthorizationGate gate;

    @Mock
    private OrganizationContext organization;

    @Mock
    private RoleBindingStore bindingStore;

    @Mock
    private RoleStore roleStore;

    @Mock
    private UserDirectory userDirectory;

    @Mock
    private DirectoryGroups directoryGroups;

    @InjectMocks
    private AdminUserService sut;

    private static final String ORGANIZATION = "acme";
    private static final Pageable PAGE = PageRequest.of(0, 20);
    private static final java.time.Instant JOINED = java.time.Instant.parse("2026-01-02T00:00:00Z");
    private static final java.time.Instant SEEN = java.time.Instant.parse("2026-06-18T09:30:00Z");

    @BeforeEach
    void setUp() {
        lenient().when(organization.organizationId()).thenReturn(ORGANIZATION);
        lenient().when(bindingStore.listIn(ORGANIZATION)).thenReturn(List.of());
        lenient().when(roleStore.listIn(ORGANIZATION)).thenReturn(List.of());
        lenient().when(userDirectory.searchByOrganization(any(UserDirectory.DirectorySearch.class)))
                .thenReturn(List.of());
        lenient().when(directoryGroups.reconciliationKeysForUser(anyString(), anyString())).thenReturn(List.of());
        lenient().when(directoryGroups.groupDisplayNamesForUser(anyString(), anyString())).thenReturn(List.of());
    }

    /**
     * Matches the {@link UserDirectory.DirectorySearch} {@link AdminUserService} always builds: this organization,
     * {@code includeDeparted = true}, any query and page/size - the admin Users console is a roster, not a picker.
     */
    private static UserDirectory.DirectorySearch rosterSearch() {

        return argThat(search -> ORGANIZATION.equals(search.organizationId()) && search.includeDeparted());
    }

    /** {@link #rosterSearch()}, narrowed to the query {@link AdminUserService} was asked to filter by. */
    private static UserDirectory.DirectorySearch rosterSearch(String query) {

        return argThat(search -> ORGANIZATION.equals(search.organizationId()) && query.equals(search.query())
                && search.includeDeparted());
    }

    @Test
    void list_returnsAllDirectoryMembersOverlayingRolesWhereTheyExist() {

        // given — alice has a direct USER binding; bob is a plain member with no binding (implicit Member)
        when(bindingStore.listIn(ORGANIZATION)).thenReturn(
                List.of(SubjectBinding.builder().subjectType("USER").subjectId("alice").roleId(10L).build()));
        when(roleStore.listIn(ORGANIZATION)).thenReturn(
                List.of(RoleDefinition.builder().id(10L).organizationId(ORGANIZATION).name("Member Manager").build()));
        when(userDirectory.searchByOrganization(rosterSearch())).thenReturn(List.of(
                new UserDirectory.DirectoryUser("alice", "Alice", "a@x.io", null, null,
                        UserDirectory.MemberStatus.PRESENT),
                new UserDirectory.DirectoryUser("bob", "Bob", "b@x.io", null, null,
                        UserDirectory.MemberStatus.PRESENT)));
        when(directoryGroups.reconciliationKeysForUser(ORGANIZATION, "alice")).thenReturn(List.of());
        when(directoryGroups.reconciliationKeysForUser(ORGANIZATION, "bob")).thenReturn(List.of());

        // when
        var result = sut.list("", PAGE);

        // then — both directory members are listed (sorted by displayName): alice with her role, bob bare
        assertThat(result).extracting(AdminUser::userId).containsExactly("alice", "bob");
        var alice = result.getContent().get(0);
        assertThat(alice.effectiveRoles()).extracting(EffectiveRole::role).containsExactly("Member Manager");
        var bob = result.getContent().get(1);
        assertThat(bob.effectiveRoles()).isEmpty();
    }

    @Test
    void list_totalsTheWholeRosterNotJustThePage() {

        // given — a roster three deep, asked for one person at a time
        when(userDirectory.searchByOrganization(rosterSearch())).thenReturn(List.of(
                new UserDirectory.DirectoryUser("alice", "Alice", "a@x.io", null, null,
                        UserDirectory.MemberStatus.PRESENT),
                new UserDirectory.DirectoryUser("bob", "Bob", "b@x.io", null, null, UserDirectory.MemberStatus.PRESENT),
                new UserDirectory.DirectoryUser("carol", "Carol", "c@x.io", null, null,
                        UserDirectory.MemberStatus.PRESENT)));
        when(directoryGroups.reconciliationKeysForUser(eq(ORGANIZATION), anyString())).thenReturn(List.of());

        // when
        var result = sut.list("", PageRequest.of(1, 1));

        // then — the page holds one person, but the tally is the headcount, not the page length
        assertThat(result.getContent()).extracting(AdminUser::userId).containsExactly("bob");
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(3);
    }

    @Test
    void list_includesMemberWithNoBindingAndEmptyEffectiveRoles() {

        // given — a single directory member with no role binding at all
        when(userDirectory.searchByOrganization(rosterSearch()))
                .thenReturn(List.of(new UserDirectory.DirectoryUser("u9", "Dana", "dana@acme.com", JOINED, SEEN,
                        UserDirectory.MemberStatus.PRESENT)));

        // when
        var result = sut.list("", PAGE);

        // then — the plain member appears with no role chips
        assertThat(result).extracting(AdminUser::userId).containsExactly("u9");
        assertThat(result.getContent().get(0).effectiveRoles()).isEmpty();
    }

    @Test
    void list_includesScimProvisionedMemberAwaitingFirstSignIn_withNullUserId() {

        // given — a SCIM-provisioned directory member has no user_id until their first sign-in attaches a subject
        when(userDirectory.searchByOrganization(rosterSearch()))
                .thenReturn(List.of(new UserDirectory.DirectoryUser(null, "Pending Pat", "pat@acme.com", null, null,
                        UserDirectory.MemberStatus.PRESENT)));
        when(directoryGroups.reconciliationKeysForUser(eq(ORGANIZATION), isNull())).thenReturn(List.of());

        // when
        var result = sut.list("", PAGE);

        // then — the provisioned member is surfaced (as pending, null userId), not dropped, and carries no roles
        assertThat(result).hasSize(1);
        assertThat(result.getContent().get(0).userId()).isNull();
        assertThat(result.getContent().get(0).displayName()).isEqualTo("Pending Pat");
        assertThat(result.getContent().get(0).effectiveRoles()).isEmpty();
    }

    @Test
    void list_emptyDirectoryReturnsNoUsers() {

        // given — no directory users and no bindings (default lenient stubs)

        // when
        var result = sut.list("", PAGE);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void list_passesQueryToDirectory() {

        // given — directory returns only Ann for the "ann" query; Ann has a covering binding
        when(userDirectory.searchByOrganization(rosterSearch("ann")))
                .thenReturn(List.of(new UserDirectory.DirectoryUser("u1", "Ann Smith", "ann@acme.com", JOINED, SEEN,
                        UserDirectory.MemberStatus.PRESENT)));
        when(bindingStore.listIn(ORGANIZATION))
                .thenReturn(List.of(SubjectBinding.builder().subjectType("USER").subjectId("u1").build()));

        // when — query matching is now delegated to the directory port
        var result = sut.list("ann", PAGE);

        // then
        assertThat(result).extracting(AdminUser::userId).containsExactly("u1");
    }

    @Test
    void list_passesEmailQueryToDirectory() {

        // given — query filtering (incl. email) is delegated to the directory port
        when(userDirectory.searchByOrganization(rosterSearch("acme")))
                .thenReturn(List.of(new UserDirectory.DirectoryUser("c1", "Carol", "carol@acme.com", JOINED, SEEN,
                        UserDirectory.MemberStatus.PRESENT)));
        when(bindingStore.listIn(ORGANIZATION))
                .thenReturn(List.of(SubjectBinding.builder().subjectType("USER").subjectId("c1").build()));

        // when
        var result = sut.list("acme", PAGE);

        // then
        assertThat(result).extracting(AdminUser::userId).containsExactly("c1");
    }

    @Test
    void list_returnsDirectoryUsersAnnotatedWithTheirDirectRoles() {

        // given — one user in the directory with a USER assignment
        when(userDirectory.searchByOrganization(rosterSearch()))
                .thenReturn(List.of(new UserDirectory.DirectoryUser("u1", "Alice", "alice@acme.com", JOINED, SEEN,
                        UserDirectory.MemberStatus.PRESENT)));

        var role = RoleDefinition.builder().id(10L).organizationId(ORGANIZATION).name("Moderator").build();
        lenient().when(roleStore.listIn(ORGANIZATION)).thenReturn(List.of(role));

        var assignment = SubjectBinding.builder().id(500L).organizationId(ORGANIZATION).subjectType("USER")
                .subjectId("u1").roleId(10L).build();
        lenient().when(bindingStore.listIn(ORGANIZATION)).thenReturn(List.of(assignment));

        // when
        var result = sut.list("", PAGE);

        // then
        assertThat(result).hasSize(1);
        var adminUser = result.getContent().get(0);
        assertThat(adminUser.userId()).isEqualTo("u1");
        assertThat(adminUser.displayName()).isEqualTo("Alice");
        assertThat(adminUser.email()).isEqualTo("alice@acme.com");
        assertThat(adminUser.effectiveRoles()).hasSize(1);
        var effectiveRole = adminUser.effectiveRoles().get(0);
        assertThat(effectiveRole.role()).isEqualTo("Moderator");
        assertThat(effectiveRole.via()).isEqualTo("DIRECT");
        // bindingId (for revoke) and roleId flow through from the originating binding
        assertThat(effectiveRole.bindingId()).isEqualTo(500L);
        assertThat(effectiveRole.roleId()).isEqualTo(10L);
        assertThat(adminUser.joinedAt()).isEqualTo(JOINED);
        assertThat(adminUser.lastActiveAt()).isEqualTo(SEEN);
    }

    @Test
    void list_deniedWithoutMemberReadPropagatesForbidden() {

        // given
        doThrow(new ForbiddenException("missing permission")).when(gate).requirePermission(Permission.MEMBER_READ);

        // when / then
        assertThatThrownBy(() -> sut.list("", PAGE)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void list_groupDerivedRoleAppearsWhenUserBelongsToMatchedGroup() {

        // given — user in synced group "engineers"; GROUP assignment of "Auditor"
        when(userDirectory.searchByOrganization(rosterSearch()))
                .thenReturn(List.of(new UserDirectory.DirectoryUser("u10", "Frank", "frank@acme.com", JOINED, SEEN,
                        UserDirectory.MemberStatus.PRESENT)));

        var role = RoleDefinition.builder().id(20L).organizationId(ORGANIZATION).name("Auditor").build();
        lenient().when(roleStore.listIn(ORGANIZATION)).thenReturn(List.of(role));

        var groupAssignment = SubjectBinding.builder().id(600L).organizationId(ORGANIZATION).subjectType("GROUP")
                .subjectId("engineers").roleId(20L).build();
        lenient().when(bindingStore.listIn(ORGANIZATION)).thenReturn(List.of(groupAssignment));

        when(directoryGroups.reconciliationKeysForUser(ORGANIZATION, "u10")).thenReturn(List.of("engineers"));

        // when
        var result = sut.list("", PAGE);

        // then
        assertThat(result).hasSize(1);
        var effectiveRole = result.getContent().get(0).effectiveRoles().get(0);
        assertThat(effectiveRole.role()).isEqualTo("Auditor");
        assertThat(effectiveRole.via()).isEqualTo("GROUP");
        assertThat(effectiveRole.bindingId()).isNull();
        assertThat(effectiveRole.roleId()).isEqualTo(20L);
    }

    @Test
    void list_oneRoleReachedThroughTwoBoundGroupsIsOneEffectiveRole() {

        // given — Frank is in both "engineers" and "auditors", and BOTH groups are bound to Auditor. He holds the role
        // once; listing it twice made the roster render two identical chips under one React key.
        when(userDirectory.searchByOrganization(rosterSearch()))
                .thenReturn(List.of(new UserDirectory.DirectoryUser("u12", "Frank", "frank@acme.com", JOINED, SEEN,
                        UserDirectory.MemberStatus.PRESENT)));

        var role = RoleDefinition.builder().id(22L).organizationId(ORGANIZATION).name("Auditor").build();
        lenient().when(roleStore.listIn(ORGANIZATION)).thenReturn(List.of(role));

        lenient().when(bindingStore.listIn(ORGANIZATION))
                .thenReturn(List.of(
                        SubjectBinding.builder().id(700L).organizationId(ORGANIZATION).subjectType("GROUP")
                                .subjectId("engineers").roleId(22L).build(),
                        SubjectBinding.builder().id(701L).organizationId(ORGANIZATION).subjectType("GROUP")
                                .subjectId("auditors").roleId(22L).build()));

        when(directoryGroups.reconciliationKeysForUser(ORGANIZATION, "u12"))
                .thenReturn(List.of("engineers", "auditors"));

        // when
        var result = sut.list("", PAGE);

        // then
        assertThat(result.getContent().get(0).effectiveRoles()).hasSize(1).first()
                .satisfies(r -> assertThat(r.via()).isEqualTo("GROUP"));
    }

    @Test
    void list_theSameRoleHeldDirectlyAndThroughAGroupStaysTwoEffectiveRoles() {

        // given — provenance is the whole point of the drawer's "via" line, and only the DIRECT one is revocable
        // there, so these two must NOT collapse into one entry.
        when(userDirectory.searchByOrganization(rosterSearch()))
                .thenReturn(List.of(new UserDirectory.DirectoryUser("u13", "Helen", "helen@acme.com", JOINED, SEEN,
                        UserDirectory.MemberStatus.PRESENT)));

        var role = RoleDefinition.builder().id(23L).organizationId(ORGANIZATION).name("Auditor").build();
        lenient().when(roleStore.listIn(ORGANIZATION)).thenReturn(List.of(role));

        lenient().when(bindingStore.listIn(ORGANIZATION))
                .thenReturn(List.of(
                        SubjectBinding.builder().id(800L).organizationId(ORGANIZATION).subjectType("USER")
                                .subjectId("u13").roleId(23L).build(),
                        SubjectBinding.builder().id(801L).organizationId(ORGANIZATION).subjectType("GROUP")
                                .subjectId("auditors").roleId(23L).build()));

        when(directoryGroups.reconciliationKeysForUser(ORGANIZATION, "u13")).thenReturn(List.of("auditors"));

        // when
        var result = sut.list("", PAGE);

        // then
        assertThat(result.getContent().get(0).effectiveRoles()).extracting(EffectiveRole::via).containsExactly("DIRECT",
                "GROUP");
    }

    @Test
    void list_userWithNoSyncedGroupsRetainsOnlyDirectRoles() {

        // given — user has a DIRECT role; reconciliation keys are empty (no synced groups)
        when(userDirectory.searchByOrganization(rosterSearch()))
                .thenReturn(List.of(new UserDirectory.DirectoryUser("u11", "Grace", "grace@acme.com", JOINED, SEEN,
                        UserDirectory.MemberStatus.PRESENT)));

        var role = RoleDefinition.builder().id(21L).organizationId(ORGANIZATION).name("Moderator").build();
        lenient().when(roleStore.listIn(ORGANIZATION)).thenReturn(List.of(role));

        var directAssignment = SubjectBinding.builder().id(601L).organizationId(ORGANIZATION).subjectType("USER")
                .subjectId("u11").roleId(21L).build();
        lenient().when(bindingStore.listIn(ORGANIZATION)).thenReturn(List.of(directAssignment));

        when(directoryGroups.reconciliationKeysForUser(ORGANIZATION, "u11")).thenReturn(List.of());

        // when
        var result = sut.list("", PAGE);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getContent().get(0).effectiveRoles()).hasSize(1);
        assertThat(result.getContent().get(0).effectiveRoles().get(0).via()).isEqualTo("DIRECT");
    }

    @Test
    void list_carriesMemberStatusFromTheDirectory() {

        // given - a deprovisioned member still appears on the roster (includeDeparted = true), marked deactivated
        when(userDirectory.searchByOrganization(rosterSearch()))
                .thenReturn(List.of(new UserDirectory.DirectoryUser("u20", "Ivy", "ivy@acme.com", JOINED, SEEN,
                        UserDirectory.MemberStatus.DEACTIVATED)));

        // when
        var result = sut.list("", PAGE);

        // then
        assertThat(result).extracting(AdminUser::memberStatus).containsExactly(UserDirectory.MemberStatus.DEACTIVATED);
    }

    @Test
    void byId_returnsUserWithRoles() {

        // given
        var user = new UserDirectory.DirectoryUser("u5", "Eve", "eve@acme.com", JOINED, SEEN,
                UserDirectory.MemberStatus.PRESENT);
        lenient().when(userDirectory.byIds(ORGANIZATION, List.of("u5"))).thenReturn(List.of(user));

        var role = RoleDefinition.builder().id(13L).organizationId(ORGANIZATION).name("Reviewer").build();
        lenient().when(roleStore.listIn(ORGANIZATION)).thenReturn(List.of(role));

        var assignment = SubjectBinding.builder().id(700L).organizationId(ORGANIZATION).subjectType("USER")
                .subjectId("u5").roleId(13L).build();
        lenient().when(bindingStore.listIn(ORGANIZATION)).thenReturn(List.of(assignment));

        // when
        var result = sut.byId("u5");

        // then
        assertThat(result.userId()).isEqualTo("u5");
        assertThat(result.effectiveRoles()).hasSize(1);
        var effectiveRole = result.effectiveRoles().get(0);
        assertThat(effectiveRole.role()).isEqualTo("Reviewer");
        assertThat(effectiveRole.via()).isEqualTo("DIRECT");
        assertThat(effectiveRole.bindingId()).isEqualTo(700L);
        assertThat(effectiveRole.roleId()).isEqualTo(13L);
    }

    @Test
    void byId_unknownUserThrowsNotFoundException() {

        // given — byIds is total: an unknown id comes back as an ERASED entry, not an empty list
        lenient().when(userDirectory.byIds(ORGANIZATION, List.of("unknown"))).thenReturn(
                List.of(new UserDirectory.DirectoryUser("unknown", UserDirectory.DirectoryUser.DELETED_MEMBER_LABEL,
                        null, null, null, UserDirectory.MemberStatus.ERASED)));

        // when / then — an ERASED entry must still surface as not-found, not as a fabricated user
        assertThatThrownBy(() -> sut.byId("unknown")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void byId_carriesMemberStatusFromTheDirectory() {

        // given
        var user = new UserDirectory.DirectoryUser("u21", "Jack", "jack@acme.com", JOINED, SEEN,
                UserDirectory.MemberStatus.DEACTIVATED);
        lenient().when(userDirectory.byIds(ORGANIZATION, List.of("u21"))).thenReturn(List.of(user));

        // when
        var result = sut.byId("u21");

        // then
        assertThat(result.memberStatus()).isEqualTo(UserDirectory.MemberStatus.DEACTIVATED);
    }

    @Test
    void byId_returnsGroupDisplayNamesForUser() {

        // given
        var user = new UserDirectory.DirectoryUser("u12", "Henry", "henry@acme.com", JOINED, SEEN,
                UserDirectory.MemberStatus.PRESENT);
        lenient().when(userDirectory.byIds(ORGANIZATION, List.of("u12"))).thenReturn(List.of(user));

        when(directoryGroups.groupDisplayNamesForUser(ORGANIZATION, "u12"))
                .thenReturn(List.of("Engineering", "Platform"));

        // when
        var result = sut.byId("u12");

        // then
        assertThat(result.groups()).containsExactlyInAnyOrder("Engineering", "Platform");
    }

    @Test
    void list_effectiveRoleCarriesPermissions() {

        // given — user with a direct USER binding; role has a "member:read" permission
        when(userDirectory.searchByOrganization(rosterSearch()))
                .thenReturn(List.of(new UserDirectory.DirectoryUser("u1", "Alice", "alice@acme.com", JOINED, SEEN,
                        UserDirectory.MemberStatus.PRESENT)));

        var perm = "member:read";
        var role = RoleDefinition.builder().id(10L).organizationId(ORGANIZATION).name("Member Manager")
                .permissions(List.of(perm)).build();
        when(roleStore.listIn(ORGANIZATION)).thenReturn(List.of(role));

        var assignment = SubjectBinding.builder().id(500L).organizationId(ORGANIZATION).subjectType("USER")
                .subjectId("u1").roleId(10L).build();
        when(bindingStore.listIn(ORGANIZATION)).thenReturn(List.of(assignment));

        // when
        var result = sut.list("", PAGE);

        // then
        assertThat(result.getContent().get(0).effectiveRoles().get(0).permissions()).contains("member:read");
    }
}
