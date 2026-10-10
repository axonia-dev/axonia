package dev.axonia.access.adapter.out.persistence;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.access.api.Permission;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code role_binding}/{@code role_permission} join behind {@code PermissionHolders}, against real Postgres: the
 * join is what matters, not a mocked repository standing in for it. Each test binds in an Organization of its own, so
 * every answer can be asserted exactly.
 */
class RoleBindingRepositoryTest extends CoreJdbcTestSupport {

    private final String organization = "org-" + UUID.randomUUID();

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RoleBindingRepository repository;

    @Test
    void findsTheSubjectWhoseRoleCarriesThePermission() {

        var admin = aUserBoundTo(aRoleWith(Permission.SCIM_WRITE));

        assertThat(holdersOf(organization, Permission.SCIM_WRITE)).containsExactly(admin);
    }

    @Test
    void doesNotFindTheSubjectWhoseRoleCarriesADifferentPermission() {

        aUserBoundTo(aRoleWith(Permission.MEMBER_READ));

        assertThat(holdersOf(organization, Permission.SCIM_WRITE)).isEmpty();
    }

    @Test
    void namesTheSubjectOnlyOnceWhenTwoOfTheirRolesCarryIt() {

        var admin = aUserBoundTo(aRoleWith(Permission.SCIM_WRITE), aRoleWith(Permission.SCIM_WRITE));

        assertThat(holdersOf(organization, Permission.SCIM_WRITE)).containsExactly(admin);
    }

    /**
     * The Testcontainers connection is the table owner, and ownership bypasses row-level security, so this holds only
     * the query's explicit {@code organization_id} predicate.
     */
    @Test
    void doesNotReachIntoAnotherOrganization() {

        aUserBoundTo(aRoleWith(Permission.SCIM_WRITE));

        assertThat(holdersOf("other-" + organization, Permission.SCIM_WRITE)).isEmpty();
    }

    @Test
    void readsOnlyTheSubjectTypeAskedFor() {

        var groupKey = "group-" + UUID.randomUUID();
        bind("GROUP", groupKey, aRoleWith(Permission.SCIM_WRITE));

        assertThat(holdersOf(organization, Permission.SCIM_WRITE)).isEmpty();
        assertThat(repository.findDistinctSubjectIdsByOrganizationIdAndSubjectTypeAndPermission(organization, "GROUP",
                Permission.SCIM_WRITE.code())).containsExactly(groupKey);
    }

    @Test
    void namesASubjectSomeBindingNamesWhateverItsRole() {

        var groupKey = "group-" + UUID.randomUUID();
        bind("GROUP", groupKey, aRoleWith(Permission.MEMBER_READ));

        assertThat(repository.existsByOrganizationIdAndSubjectTypeAndSubjectId(organization, "GROUP", groupKey))
                .isTrue();
        assertThat(repository.existsByOrganizationIdAndSubjectTypeAndSubjectId(organization, "GROUP", "unbound"))
                .isFalse();
    }

    private List<String> holdersOf(String organizationId, Permission permission) {

        return repository.findDistinctSubjectIdsByOrganizationIdAndSubjectTypeAndPermission(organizationId, "USER",
                permission.code());
    }

    private Long aRoleWith(Permission... permissions) {

        var permissionEntities = Arrays.stream(permissions).map(p -> RoleEntityMother.permission(p.code()))
                .collect(Collectors.toSet());
        return roleRepository.save(RoleEntityMother.any().organizationId(organization).name("Role " + UUID.randomUUID())
                .permissions(permissionEntities).build()).getId();
    }

    private String aUserBoundTo(Long... roleIds) {

        var userId = "user-" + UUID.randomUUID();
        for (var roleId : roleIds) {
            bind("USER", userId, roleId);
        }
        return userId;
    }

    private void bind(String subjectType, String subjectId, Long roleId) {

        repository.save(RoleBindingEntity.builder().organizationId(organization).subjectType(subjectType)
                .subjectId(subjectId).subjectLabel(subjectId).roleId(roleId).build());
    }
}
