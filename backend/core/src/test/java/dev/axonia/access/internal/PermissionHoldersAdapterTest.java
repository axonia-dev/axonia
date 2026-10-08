package dev.axonia.access.internal;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.JdbcTestSupport;
import dev.axonia.access.KnownPermissions;
import dev.axonia.access.Permission;
import dev.axonia.access.mother.RoleEntityMother;
import dev.axonia.identity.DirectoryGroups;
import dev.axonia.identity.PermissionHolders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code USER}-typed resolution is exercised against real Postgres: the {@code role_binding}/{@code role_permission}
 * join is the one that matters, not a mocked repository standing in for it. Extends {@link JdbcTestSupport} - the same
 * base several other {@code access} and {@code identity} tests already share - rather than a distinct
 * {@code @DataJdbcTest} import set, because the suite's shared {@code jdbc:tc} Postgres exhausts under too many
 * distinct {@code @Import} sets, and that only shows up in the full gate.
 *
 * <p>
 * {@link DirectoryGroups} is a plain stubbed field rather than a repository: {@link PermissionHoldersAdapter}'s job for
 * the {@code GROUP} half is only to resolve which reconciliation keys carry the permission and merge the users the port
 * hands back, and {@code CurrentTokenCallerIdentityTest}/{@code AdminUserServiceTest} already establish that a consumer
 * of this exact port mocks it rather than reaching into {@code identity.internal} for a real adapter.
 */
class PermissionHoldersAdapterTest extends CoreJdbcTestSupport {

    private static final String ORGANIZATION = "acme";
    private static final String OTHER_ORGANIZATION = "other-org";

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RoleBindingRepository roleBindingRepository;

    private final DirectoryGroups directoryGroups = mock(DirectoryGroups.class);

    @Test
    void findsSomebodyWhoseRoleCarriesThePermission() {

        var admin = aUserBoundTo(aRoleWith(Permission.SCIM_WRITE));

        assertThat(adapter().userIdsWith(ORGANIZATION, Permission.SCIM_WRITE.code())).containsExactly(admin);
    }

    @Test
    void doesNotFindSomebodyWhoseRoleCarriesADifferentPermission() {

        aUserBoundTo(aRoleWith(Permission.MEMBER_READ));

        assertThat(adapter().userIdsWith(ORGANIZATION, Permission.SCIM_WRITE.code())).isEmpty();
    }

    @Test
    void namesSomebodyOnlyOnceWhenTwoOfTheirRolesCarryIt() {

        var admin = aUserBoundTo(aRoleWith(Permission.SCIM_WRITE), aRoleWith(Permission.SCIM_WRITE));

        assertThat(adapter().userIdsWith(ORGANIZATION, Permission.SCIM_WRITE.code())).containsExactly(admin);
    }

    @Test
    void answersNobodyForACodeNoPermissionCarries() {

        aUserBoundTo(aRoleWith(Permission.SCIM_WRITE));

        assertThat(adapter().userIdsWith(ORGANIZATION, "not:a-permission")).isEmpty();
    }

    @Test
    void answersNobodyForACodeThisEditionDoesNotKnowEvenWhenARoleCarriesIt() {

        var retired = new Permission("idp_connection:write");
        aUserBoundTo(aRoleWith(retired));

        assertThat(adapter().userIdsWith(ORGANIZATION, retired.code())).isEmpty();
    }

    /**
     * The Testcontainers connection is the table owner, and ownership bypasses row-level security, so this cannot prove
     * isolation end to end. It holds the explicit {@code organization_id} predicate in
     * {@link RoleBindingRepository#findDistinctSubjectIdsByOrganizationIdAndSubjectTypeAndPermission}, which is the
     * half that is testable here; the RLS half belongs in {@code RlsStackIsolationTest}'s shape if this query is ever
     * reached without an explicit organization.
     */
    @Test
    void doesNotReachIntoAnotherOrganization() {

        aUserBoundTo(aRoleWith(Permission.SCIM_WRITE));

        assertThat(adapter().userIdsWith(OTHER_ORGANIZATION, Permission.SCIM_WRITE.code())).isEmpty();
    }

    /**
     * The {@code GROUP} binding names a reconciliation key, and {@link DirectoryGroups#userIdsInGroups} - stubbed here,
     * proven for real in {@code identity}'s own test tree - is what turns that key into a user id. This test proves
     * {@link PermissionHoldersAdapter} asks for the right key and returns what the port hands back.
     */
    @Test
    void findsSomebodyWhoseConfirmedGroupBindingCarriesThePermission() {

        var admin = "user-" + UUID.randomUUID();
        var reconciliationKey = "platform-admins";
        aGroupBoundTo(reconciliationKey, aRoleWith(Permission.SCIM_WRITE));
        when(directoryGroups.userIdsInGroups(ORGANIZATION, List.of(reconciliationKey))).thenReturn(List.of(admin));

        assertThat(adapter().userIdsWith(ORGANIZATION, Permission.SCIM_WRITE.code())).containsExactly(admin);
    }

    @Test
    void namesSomebodyOnlyOnceWhenTheyHoldItBothDirectlyAndThroughAGroup() {

        var admin = "user-" + UUID.randomUUID();
        var role = aRoleWith(Permission.SCIM_WRITE);
        bindUser(admin, role);
        var reconciliationKey = "platform-admins";
        aGroupBoundTo(reconciliationKey, role);
        when(directoryGroups.userIdsInGroups(ORGANIZATION, List.of(reconciliationKey))).thenReturn(List.of(admin));

        assertThat(adapter().userIdsWith(ORGANIZATION, Permission.SCIM_WRITE.code())).containsExactly(admin);
    }

    private PermissionHolders adapter() {

        return new PermissionHoldersAdapter(roleBindingRepository, directoryGroups, new KnownPermissions(List.of()));
    }

    private Long aRoleWith(Permission... permissions) {

        var permissionEntities = Arrays.stream(permissions)
                .map(p -> RolePermissionEntity.builder().permission(p.code()).build()).collect(Collectors.toSet());
        var role = roleRepository.save(RoleEntityMother.any().organizationId(ORGANIZATION)
                .name("Role " + UUID.randomUUID()).permissions(permissionEntities).build());
        return role.getId();
    }

    private String aUserBoundTo(Long... roleIds) {

        var userId = "user-" + UUID.randomUUID();
        for (var roleId : roleIds) {
            bindUser(userId, roleId);
        }
        return userId;
    }

    private void bindUser(String userId, Long roleId) {

        roleBindingRepository.save(RoleBindingEntity.builder().organizationId(ORGANIZATION).subjectType("USER")
                .subjectId(userId).subjectLabel(userId).roleId(roleId).build());
    }

    private void aGroupBoundTo(String reconciliationKey, Long roleId) {

        roleBindingRepository.save(RoleBindingEntity.builder().organizationId(ORGANIZATION).subjectType("GROUP")
                .subjectId(reconciliationKey).subjectLabel(reconciliationKey).roleId(roleId).build());
    }
}
