package dev.axonia.access.application;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.access.api.AuthorizationGate;
import dev.axonia.access.api.Permission;
import dev.axonia.access.api.spi.DirectoryGroupsPolicy;
import dev.axonia.access.application.port.out.OrganizationContext;
import dev.axonia.access.application.port.out.RoleBindingStore;
import dev.axonia.access.application.port.out.RoleStore;
import dev.axonia.access.domain.RoleDefinition;
import dev.axonia.access.domain.RoleDefinitionMother;
import dev.axonia.access.domain.SubjectBinding;
import dev.axonia.identity.api.DirectoryGroupConfirmation;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class RoleBindingServiceTest {

    @Mock
    private RoleBindingStore bindingStore;

    @Mock
    private RoleStore roleStore;

    @Mock
    private AuthorizationGate gate;

    @Mock
    private OrganizationContext organization;

    @Mock
    private DirectoryGroupConfirmation directoryGroupConfirmation;

    private RoleBindingService sut;

    private Boolean groupsApplyHere;

    /**
     * {@link RoleCeiling} and {@link OrganizationOwnership} are wired in for real, not mocked: every ceiling assertion
     * below is about which permissions the caller is refused and what it is told, and every last-Owner assertion is
     * about who actually still holds the role - a mocked collaborator would prove neither.
     */
    @BeforeEach
    void buildService() {

        DirectoryGroupsPolicy policy = organizationId -> Optional.ofNullable(groupsApplyHere);
        sut = new RoleBindingService(bindingStore, roleStore, gate, organization, directoryGroupConfirmation,
                new RoleCeiling(gate, new KnownPermissions(List.of())),
                new OrganizationOwnership(roleStore, bindingStore), new GroupBindingApplicability(List.of(policy)));
    }

    @Test
    void list_requiresRoleReadAndReturnsMappedAssignments() {

        // given
        when(organization.organizationId()).thenReturn("default");
        var entity = SubjectBinding.builder().id(1L).organizationId("default").subjectType("USER").subjectId("u1")
                .roleId(2L).build();
        when(bindingStore.listIn("default")).thenReturn(List.of(entity));

        // when
        var actual = sut.list();

        // then
        verify(gate).requirePermission(Permission.ROLE_READ);
        assertThat(actual).hasSize(1).first().extracting(RoleBinding::id).isEqualTo(1L);
    }

    @Test
    void create_requiresRoleAssignAndSavesWithTenant() {

        // given
        when(organization.organizationId()).thenReturn("acme");
        when(roleStore.find(2L)).thenReturn(Optional.of(RoleDefinitionMother.any().id(2L).organizationId("acme")
                .permissions(List.of(RoleDefinitionMother.permission(Permission.MEMBER_READ.code()))).build()));
        when(gate.currentPermissions()).thenReturn(Set.of(Permission.ROLE_ASSIGN, Permission.MEMBER_READ));
        var saved = SubjectBinding.builder().id(1L).organizationId("acme").subjectType("USER").subjectId("u1")
                .roleId(2L).build();
        when(bindingStore.save(any())).thenReturn(saved);

        var request = new CreateRoleBindingRequest("USER", "u1", 2L, null);

        // when
        sut.create(request);

        // then
        verify(gate).requirePermission(Permission.ROLE_ASSIGN);
        verify(bindingStore).save(any(SubjectBinding.class));
    }

    @Test
    void create_refusesARoleTheSubjectAlreadyHoldsDirectly() {

        // given - the same subject already holds this very role, so a second binding would only duplicate it
        when(organization.organizationId()).thenReturn("acme");
        when(roleStore.find(2L))
                .thenReturn(Optional.of(RoleDefinitionMother.any().id(2L).organizationId("acme").name("Admin")
                        .permissions(List.of(RoleDefinitionMother.permission(Permission.MEMBER_READ.code()))).build()));
        when(gate.currentPermissions()).thenReturn(Set.of(Permission.ROLE_ASSIGN, Permission.MEMBER_READ));
        when(bindingStore.exists("acme", "USER", "u1", 2L)).thenReturn(true);

        var request = new CreateRoleBindingRequest("USER", "u1", 2L, null);

        // when / then
        assertThatThrownBy(() -> sut.create(request)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("Admin");
        verify(bindingStore, never()).save(any());
    }

    @Test
    void create_forbidden_propagatesAndNeverSaves() {

        // given
        doThrow(new ForbiddenException("forbidden")).when(gate).requirePermission(Permission.ROLE_ASSIGN);
        var request = new CreateRoleBindingRequest("USER", "u1", 2L, null);

        // when / then
        assertThatThrownBy(() -> sut.create(request)).isInstanceOf(ForbiddenException.class);
        verify(bindingStore, never()).save(any());
    }

    // --- confirmGroup: releasing a held directory group ---

    @Test
    void confirmGroup_releasesTheHeldDirectoryGroupTheBindingNames() {

        // given - the binding names a directory group SCIM pushed and held
        when(organization.organizationId()).thenReturn("acme");
        var binding = SubjectBinding.builder().id(5L).organizationId("acme").subjectType("GROUP")
                .subjectId("platform-admins").roleId(2L).build();
        when(bindingStore.find(5L)).thenReturn(Optional.of(binding));
        when(bindingStore.findBySubject("acme", "GROUP", "platform-admins")).thenReturn(List.of(binding));
        when(roleStore.find(2L)).thenReturn(Optional.of(RoleDefinitionMother.any().id(2L).organizationId("acme")
                .permissions(List.of(RoleDefinitionMother.permission(Permission.MEMBER_READ.code()))).build()));
        when(gate.currentPermissions()).thenReturn(Set.of(Permission.ROLE_ASSIGN, Permission.MEMBER_READ));
        when(directoryGroupConfirmation.confirm("acme", "platform-admins")).thenReturn(true);

        // when
        sut.confirmGroup(5L);

        // then - gated on the permission a SCIM writer is precisely the one not to hold
        verify(gate).requirePermission(Permission.ROLE_ASSIGN);
        verify(directoryGroupConfirmation).confirm("acme", "platform-admins");
    }

    @Test
    void confirmGroup_forbidden_propagatesAndNeverConfirms() {

        // given
        doThrow(new ForbiddenException("forbidden")).when(gate).requirePermission(Permission.ROLE_ASSIGN);

        // when / then
        assertThatThrownBy(() -> sut.confirmGroup(5L)).isInstanceOf(ForbiddenException.class);
        verify(directoryGroupConfirmation, never()).confirm(any(), any());
    }

    @Test
    void confirmGroup_unknownBinding_isNotFound() {

        // given
        when(bindingStore.find(5L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.confirmGroup(5L)).isInstanceOf(NotFoundException.class);
        verify(directoryGroupConfirmation, never()).confirm(any(), any());
    }

    @Test
    void confirmGroup_bindingOfAnotherOrganization_isNotFound() {

        // given - RLS aside, the id is the caller's only input and must never reach another tenant's row
        when(organization.organizationId()).thenReturn("acme");
        when(bindingStore.find(5L)).thenReturn(Optional.of(SubjectBinding.builder().id(5L)
                .organizationId("other-tenant").subjectType("GROUP").subjectId("platform-admins").roleId(2L).build()));

        // when / then
        assertThatThrownBy(() -> sut.confirmGroup(5L)).isInstanceOf(NotFoundException.class);
        verify(directoryGroupConfirmation, never()).confirm(any(), any());
    }

    @Test
    void confirmGroup_userBinding_isNotFound() {

        // given - a USER binding names a person's subject, and there is no directory group behind it to release
        when(organization.organizationId()).thenReturn("acme");
        when(bindingStore.find(5L)).thenReturn(Optional.of(SubjectBinding.builder().id(5L).organizationId("acme")
                .subjectType("USER").subjectId("u1").roleId(2L).build()));

        // when / then
        assertThatThrownBy(() -> sut.confirmGroup(5L)).isInstanceOf(NotFoundException.class);
        verify(directoryGroupConfirmation, never()).confirm(any(), any());
    }

    @Test
    void confirmGroup_beyondTheCallersCeiling_isRefused() {

        // given - releasing a group hands out the role bound to it, so the same ceiling as granting it applies
        when(organization.organizationId()).thenReturn("acme");
        var binding = SubjectBinding.builder().id(5L).organizationId("acme").subjectType("GROUP")
                .subjectId("platform-admins").roleId(2L).build();
        when(bindingStore.find(5L)).thenReturn(Optional.of(binding));
        when(bindingStore.findBySubject("acme", "GROUP", "platform-admins")).thenReturn(List.of(binding));
        when(roleStore.find(2L)).thenReturn(Optional.of(RoleDefinitionMother.any().id(2L).organizationId("acme")
                .permissions(List.of(RoleDefinitionMother.permission(Permission.ORGANIZATION_DELETE.code()))).build()));
        when(gate.currentPermissions()).thenReturn(Set.of(Permission.ROLE_ASSIGN));

        // when / then
        assertThatThrownBy(() -> sut.confirmGroup(5L)).isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("A role can only be assigned if every permission it grants is already held")
                .hasMessageContaining(Permission.ORGANIZATION_DELETE.code());
        verify(directoryGroupConfirmation, never()).confirm(any(), any());
    }

    @Test
    void confirmGroup_throughAWeakerBindingOnAKeyAStrongerOneAlsoNames_isRefused() {

        // given - one key, two bindings. A binding is unique on subject PLUS role, so a delegated administrator
        // binding some trivial role of their own to a key an Owner binding is already waiting on is an ordinary
        // write that passes create's ceiling on the trivial role alone.
        when(organization.organizationId()).thenReturn("acme");
        var trivial = SubjectBinding.builder().id(5L).organizationId("acme").subjectType("GROUP")
                .subjectId("platform-admins").roleId(2L).build();
        var owner = SubjectBinding.builder().id(6L).organizationId("acme").subjectType("GROUP")
                .subjectId("platform-admins").roleId(3L).build();
        when(bindingStore.find(5L)).thenReturn(Optional.of(trivial));
        when(bindingStore.findBySubject("acme", "GROUP", "platform-admins")).thenReturn(List.of(trivial, owner));
        when(roleStore.find(2L))
                .thenReturn(Optional.of(RoleDefinitionMother.any().id(2L).organizationId("acme").name("Reader")
                        .permissions(List.of(RoleDefinitionMother.permission(Permission.MEMBER_READ.code()))).build()));
        when(roleStore.find(3L)).thenReturn(Optional.of(RoleDefinitionMother.any().id(3L).organizationId("acme")
                .name("Owner")
                .permissions(List.of(RoleDefinitionMother.permission(Permission.ORGANIZATION_DELETE.code()))).build()));
        when(gate.currentPermissions()).thenReturn(Set.of(Permission.ROLE_ASSIGN, Permission.MEMBER_READ));

        // when / then - confirmation is stamped on the group, so releasing through the trivial binding would
        // release the Owner one with it
        assertThatThrownBy(() -> sut.confirmGroup(5L)).isInstanceOf(ForbiddenException.class)
                .hasMessageContaining(Permission.ORGANIZATION_DELETE.code());
        verify(directoryGroupConfirmation, never()).confirm(any(), any());
    }

    @Test
    void confirmGroup_whenNoDirectoryGroupHasBeenProvisioned_isNotFound() {

        // given - the ordinary state of a binding authored before the directory ever pushed the group
        when(organization.organizationId()).thenReturn("acme");
        var binding = SubjectBinding.builder().id(5L).organizationId("acme").subjectType("GROUP")
                .subjectId("platform-admins").roleId(2L).build();
        when(bindingStore.find(5L)).thenReturn(Optional.of(binding));
        when(bindingStore.findBySubject("acme", "GROUP", "platform-admins")).thenReturn(List.of(binding));
        when(roleStore.find(2L)).thenReturn(Optional.of(RoleDefinitionMother.any().id(2L).organizationId("acme")
                .permissions(List.of(RoleDefinitionMother.permission(Permission.MEMBER_READ.code()))).build()));
        when(gate.currentPermissions()).thenReturn(Set.of(Permission.ROLE_ASSIGN, Permission.MEMBER_READ));
        when(directoryGroupConfirmation.confirm("acme", "platform-admins")).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> sut.confirmGroup(5L)).isInstanceOf(NotFoundException.class)
                .hasMessageContaining("platform-admins");
    }

    @Test
    void delete_requiresRoleAssign() {

        // given - a binding whose role no longer exists, so neither ceiling nor last-owner guard has anything to check
        var binding = SubjectBinding.builder().id(99L).organizationId("default").subjectType("GROUP")
                .subjectId("some-group").roleId(404L).build();
        when(bindingStore.find(99L)).thenReturn(Optional.of(binding));

        // when
        sut.delete(99L);

        // then
        verify(gate).requirePermission(Permission.ROLE_ASSIGN);
        verify(bindingStore).deleteById(99L);
    }

    /** A binding already gone (raced with another delete, or never existed) is a no-op, not an error. */
    @Test
    void delete_missingBindingDoesNothing() {

        // when
        sut.delete(404L);

        // then
        verify(gate).requirePermission(Permission.ROLE_ASSIGN);
        verify(bindingStore, never()).deleteById(any());
    }

    /**
     * Authority only moves within the ceiling, in both directions. Handing out a role the caller does not hold is
     * refused because it is self-escalation; taking away a role the caller does not hold is refused because it is
     * exactly as much power over somebody above them. The delegated role admin who can strip the Owner has taken
     * ownership transfer, organization deletion and SCIM off them.
     */
    @Test
    void aDelegatedRoleAdminCannotRevokeABindingToARoleAboveTheirCeiling() {

        // given - Owner grants role:assign (which the caller holds) and organization:delete (which it doesn't)
        var ownerRole = RoleDefinitionMother.any().id(2L).organizationId("default").name("Owner")
                .permissions(List.of(RoleDefinitionMother.permission(Permission.ROLE_ASSIGN.code()),
                        RoleDefinitionMother.permission(Permission.ORGANIZATION_DELETE.code())))
                .build();
        var ownerBinding = SubjectBinding.builder().id(1L).organizationId("default").subjectType("USER")
                .subjectId("u-owner").roleId(2L).build();
        when(bindingStore.find(1L)).thenReturn(Optional.of(ownerBinding));
        when(roleStore.find(2L)).thenReturn(Optional.of(ownerRole));
        when(gate.currentPermissions()).thenReturn(Set.of(Permission.ROLE_READ, Permission.ROLE_ASSIGN));

        // when / then
        assertThatThrownBy(() -> sut.delete(1L)).isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("can only be revoked");
        verify(bindingStore, never()).deleteById(any());
    }

    /** The last direct Owner is never revocable, whoever asks: an ownerless Organization cannot be repaired in-app. */
    @Test
    void theLastUserOwnerBindingCannotBeRevokedEvenByAnOwner() {

        // given - the sole USER binding to the Owner role, with a caller who holds every permission
        var ownerRole = RoleDefinitionMother.any().id(2L).organizationId("default").name("Owner")
                .permissions(List.of(RoleDefinitionMother.permission(Permission.ROLE_ASSIGN.code()))).build();
        var ownerBinding = SubjectBinding.builder().id(1L).organizationId("default").subjectType("USER")
                .subjectId("u-owner").roleId(2L).build();
        when(bindingStore.find(1L)).thenReturn(Optional.of(ownerBinding));
        when(roleStore.find(2L)).thenReturn(Optional.of(ownerRole));
        when(gate.currentPermissions()).thenReturn(Permission.OPEN);
        when(roleStore.findByName("default", "Owner")).thenReturn(Optional.of(ownerRole));
        when(bindingStore.boundTo(2L)).thenReturn(List.of(ownerBinding));

        // when / then
        assertThatThrownBy(() -> sut.delete(1L)).isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode()).isEqualTo("last_owner");
        verify(bindingStore, never()).deleteById(any());
    }

    /**
     * A GROUP Owner binding must not mask the last direct USER Owner. This codebase's own notion of who owns an
     * Organization ({@link OrganizationOwnership}'s {@code heldBy}) never counts a GROUP binding, so a revoke that
     * leaves one GROUP Owner binding and zero USER Owner bindings leaves the Organization with no Owner by that same
     * definition, whatever the raw binding count on the role says.
     */
    @Test
    void theLastUserOwnerBindingCannotBeRevokedWhenAGroupAlsoHoldsOwner() {

        // given - one USER Owner binding and one GROUP Owner binding on the same role
        var ownerRole = RoleDefinitionMother.any().id(2L).organizationId("default").name("Owner")
                .permissions(List.of(RoleDefinitionMother.permission(Permission.ROLE_ASSIGN.code()))).build();
        var userBinding = SubjectBinding.builder().id(1L).organizationId("default").subjectType("USER")
                .subjectId("u-owner").roleId(2L).build();
        var groupBinding = SubjectBinding.builder().id(2L).organizationId("default").subjectType("GROUP")
                .subjectId("axonia-org-admins").roleId(2L).build();
        when(bindingStore.find(1L)).thenReturn(Optional.of(userBinding));
        when(roleStore.find(2L)).thenReturn(Optional.of(ownerRole));
        when(gate.currentPermissions()).thenReturn(Permission.OPEN);
        when(roleStore.findByName("default", "Owner")).thenReturn(Optional.of(ownerRole));
        when(bindingStore.boundTo(2L)).thenReturn(List.of(userBinding, groupBinding));

        // when / then
        assertThatThrownBy(() -> sut.delete(1L)).isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode()).isEqualTo("last_owner");
        verify(bindingStore, never()).deleteById(any());
    }

    /** A second Owner makes the first revocable again, which is how ownership is actually transferred. */
    @Test
    void anOwnerBindingIsRevocableOnceASecondOwnerExists() {

        // given - two USER bindings to the Owner role; revoking the first no longer leaves it ownerless
        var ownerRole = RoleDefinitionMother.any().id(2L).organizationId("default").name("Owner")
                .permissions(List.of(RoleDefinitionMother.permission(Permission.ROLE_ASSIGN.code()))).build();
        var first = SubjectBinding.builder().id(1L).organizationId("default").subjectType("USER").subjectId("u-owner")
                .roleId(2L).build();
        var second = SubjectBinding.builder().id(2L).organizationId("default").subjectType("USER").subjectId("u-second")
                .roleId(2L).build();
        when(bindingStore.find(1L)).thenReturn(Optional.of(first));
        when(roleStore.find(2L)).thenReturn(Optional.of(ownerRole));
        when(gate.currentPermissions()).thenReturn(Permission.OPEN);
        when(roleStore.findByName("default", "Owner")).thenReturn(Optional.of(ownerRole));
        when(bindingStore.boundTo(2L)).thenReturn(List.of(first, second));
        when(roleStore.listIn("default")).thenReturn(List.of(ownerRole));
        when(bindingStore.listIn("default")).thenReturn(List.of(first, second));

        // when
        sut.delete(1L);

        // then
        verify(bindingStore).deleteById(1L);
    }

    /**
     * The last-owner guard only fires when the binding actually being deleted is itself a binding to the Owner role.
     * Alice holds both the sole USER Owner binding and a separate, unrelated Member Manager binding; only the latter is
     * removed here, and the fact that she also happens to be the Organization's sole Owner elsewhere must not block it
     * - that would refuse an entirely unrelated revoke with a message about a role that isn't being touched.
     */
    @Test
    void delete_allowsRemovingANonOwnerUserBindingEvenWhenTheSameSubjectIsTheSoleOwner() {

        // given - alice holds both the sole Owner binding and an unrelated Member Manager binding; only the latter is
        // removed
        var memberRole = RoleDefinitionMother.any().id(5L).organizationId("default").name("Member Manager")
                .permissions(List.of(RoleDefinitionMother.permission(Permission.MEMBER_READ.code()))).build();
        var aliceMemberBinding = SubjectBinding.builder().id(8L).organizationId("default").subjectType("USER")
                .subjectId("alice").roleId(5L).build();
        var ownerRole = RoleDefinitionMother.any().id(2L).organizationId("default").name("Owner")
                .permissions(List.of(RoleDefinitionMother.permission(Permission.ROLE_ASSIGN.code()))).build();
        var aliceOwnerBinding = SubjectBinding.builder().id(1L).organizationId("default").subjectType("USER")
                .subjectId("alice").roleId(2L).build();
        when(bindingStore.find(8L)).thenReturn(Optional.of(aliceMemberBinding));
        when(roleStore.find(5L)).thenReturn(Optional.of(memberRole));
        when(gate.currentPermissions()).thenReturn(Permission.OPEN);
        when(roleStore.findByName("default", "Owner")).thenReturn(Optional.of(ownerRole));
        // Only consulted by the pre-fix code, which looked up owner bindings before checking that the binding being
        // deleted was even an Owner-role binding; the fixed guard short-circuits before ever reaching this lookup.
        lenient().when(bindingStore.boundTo(2L)).thenReturn(List.of(aliceOwnerBinding));
        when(roleStore.listIn("default")).thenReturn(List.of(memberRole, ownerRole));
        when(bindingStore.listIn("default")).thenReturn(List.of(aliceMemberBinding, aliceOwnerBinding));

        // when
        sut.delete(8L);

        // then
        verify(bindingStore).deleteById(8L);
    }

    /**
     * {@link RoleBindingService#soleUserOwner(String, String)}'s own subject-match branch, exercised through the guard:
     * a lone USER Owner-role record belonging to somebody other than the binding's own subject must not read as that
     * subject being the sole Owner. Carol's Owner-role binding is being deleted, but the Organization's Owner-role
     * bindings resolve to dave alone - carol is not the person that lone record belongs to, so the `size == 1 &&
     * subject matches` check must fall through to false rather than short-circuit true.
     */
    @Test
    void delete_ownerBindingIsRevocableWhenTheSoleOwnerRecordBelongsToADifferentSubject() {

        // given - carol's own Owner-role binding is deleted; the only Owner-role binding on record belongs to dave
        var ownerRole = RoleDefinitionMother.any().id(2L).organizationId("default").name("Owner")
                .permissions(List.of(RoleDefinitionMother.permission(Permission.ROLE_ASSIGN.code()))).build();
        var carolBinding = SubjectBinding.builder().id(9L).organizationId("default").subjectType("USER")
                .subjectId("carol").roleId(2L).build();
        var daveBinding = SubjectBinding.builder().id(10L).organizationId("default").subjectType("USER")
                .subjectId("dave").roleId(2L).build();
        when(bindingStore.find(9L)).thenReturn(Optional.of(carolBinding));
        when(roleStore.find(2L)).thenReturn(Optional.of(ownerRole));
        when(gate.currentPermissions()).thenReturn(Permission.OPEN);
        when(roleStore.findByName("default", "Owner")).thenReturn(Optional.of(ownerRole));
        when(bindingStore.boundTo(2L)).thenReturn(List.of(daveBinding));
        when(roleStore.listIn("default")).thenReturn(List.of(ownerRole));
        when(bindingStore.listIn("default")).thenReturn(List.of(carolBinding, daveBinding));

        // when
        sut.delete(9L);

        // then
        verify(bindingStore).deleteById(9L);
    }

    @Test
    void delete_refusesUnbindingTheLastBindingThatKeepsRolesManageable() {

        // given - a single organization-wide binding of a role that alone keeps role:write/role:assign live
        var ownerRole = RoleDefinition.builder().id(2L).organizationId("default").name("Tenant Owner")
                .permissions(List.of("role:write", "role:assign")).build();
        var binding = SubjectBinding.builder().id(1L).organizationId("default").subjectType("GROUP")
                .subjectId("axonia-org-admins").roleId(2L).build();
        when(bindingStore.find(1L)).thenReturn(Optional.of(binding));
        when(roleStore.listIn("default")).thenReturn(List.of(ownerRole));
        when(bindingStore.listIn("default")).thenReturn(List.of(binding));

        // when / then
        assertThatThrownBy(() -> sut.delete(1L)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("role:write");
        verify(bindingStore, never()).deleteById(any());
    }

    @Test
    void delete_allowsUnbindingWhenAnotherBindingStillKeepsRolesManageable() {

        // given - two groups hold the management role organization-wide; dropping one is safe
        var ownerRole = RoleDefinition.builder().id(2L).organizationId("default").name("Tenant Owner")
                .permissions(List.of("role:write", "role:assign")).build();
        var bindingA = SubjectBinding.builder().id(1L).organizationId("default").subjectType("GROUP")
                .subjectId("admins-a").roleId(2L).build();
        var bindingB = SubjectBinding.builder().id(2L).organizationId("default").subjectType("GROUP")
                .subjectId("admins-b").roleId(2L).build();
        when(bindingStore.find(1L)).thenReturn(Optional.of(bindingA));
        when(roleStore.listIn("default")).thenReturn(List.of(ownerRole));
        when(bindingStore.listIn("default")).thenReturn(List.of(bindingA, bindingB));

        // when
        sut.delete(1L);

        // then
        verify(bindingStore).deleteById(1L);
    }

    @Test
    void delete_skipsTheGuardWhenTheBindingsRoleNoLongerExists() {

        // given - a dangling binding whose role has since been removed from the organization
        var binding = SubjectBinding.builder().id(7L).organizationId("default").subjectType("GROUP").subjectId("ghosts")
                .roleId(404L).build();
        when(bindingStore.find(7L)).thenReturn(Optional.of(binding));
        when(roleStore.listIn("default")).thenReturn(List.of());

        // when
        sut.delete(7L);

        // then
        verify(bindingStore).deleteById(7L);
    }

    @Test
    void delete_allowsUnbindingABindingThatCarriesNoManagementPermission() {

        // given - the only binding of a non-management role; removing it can't lock the organization out
        var memberRole = RoleDefinition.builder().id(5L).organizationId("default").name("Member Manager")
                .permissions(List.of("member:read")).build();
        var binding = SubjectBinding.builder().id(8L).organizationId("default").subjectType("GROUP")
                .subjectId("members").roleId(5L).build();
        when(bindingStore.find(8L)).thenReturn(Optional.of(binding));
        when(roleStore.listIn("default")).thenReturn(List.of(memberRole));
        when(bindingStore.listIn("default")).thenReturn(List.of(binding));

        // when
        sut.delete(8L);

        // then
        verify(bindingStore).deleteById(8L);
    }

    @Test
    void assigneesOf_requiresRoleReadAndReturnsMappedAssignments() {

        // given
        var entity = SubjectBinding.builder().id(3L).organizationId("default").subjectType("USER").subjectId("u2")
                .roleId(7L).build();
        when(bindingStore.boundTo(7L)).thenReturn(List.of(entity));

        // when
        var actual = sut.assigneesOf(7L);

        // then
        verify(gate).requirePermission(Permission.ROLE_READ);
        assertThat(actual).hasSize(1).first().extracting(RoleBinding::id).isEqualTo(3L);
        assertThat(actual.get(0).subjectId()).isEqualTo("u2");
        assertThat(actual.get(0).roleId()).isEqualTo(7L);
    }

    @Test
    void create_groupSubjectWithLabel_persistsLabel() {

        // given
        when(organization.organizationId()).thenReturn("acme");
        when(roleStore.find(2L)).thenReturn(Optional.of(RoleDefinitionMother.any().id(2L).organizationId("acme")
                .permissions(List.of(RoleDefinitionMother.permission(Permission.MEMBER_READ.code()))).build()));
        when(gate.currentPermissions()).thenReturn(Set.of(Permission.ROLE_ASSIGN, Permission.MEMBER_READ));
        var saved = SubjectBinding.builder().id(5L).organizationId("acme").subjectType("GROUP").subjectId("grp-admin")
                .subjectLabel("Admins").roleId(2L).build();
        when(bindingStore.save(any())).thenReturn(saved);

        var request = new CreateRoleBindingRequest("GROUP", "grp-admin", 2L, "Admins");

        // when
        var result = sut.create(request);

        // then
        verify(bindingStore).save(any(SubjectBinding.class));
        assertThat(result.subjectLabel()).isEqualTo("Admins");
    }

    @Test
    void create_groupSubjectWhereGroupsDoNotApply_isRefused() {

        // given - an Organization with no directory, so no sign-in there can ever assert a group claim
        when(organization.organizationId()).thenReturn("rust-hub");
        groupsApplyHere = false;
        var request = new CreateRoleBindingRequest("GROUP", "axonia-org-admins", 2L, "Admins");

        // when / then
        assertThatThrownBy(() -> sut.create(request)).isInstanceOf(ValidationException.class)
                .hasMessageContaining("group");
        verify(bindingStore, never()).save(any());
    }

    @Test
    void create_rejectsUnknownSubjectType() {

        // given
        var request = new CreateRoleBindingRequest("ROBOT", "u1", 2L, null);

        // when / then
        assertThatThrownBy(() -> sut.create(request)).isInstanceOf(ValidationException.class)
                .hasMessageContaining("ROBOT");
        verify(bindingStore, never()).save(any());
    }

    @Test
    void create_rejectsRoleFromAnotherOrganization() {

        // given
        when(organization.organizationId()).thenReturn("acme");
        when(roleStore.find(2L))
                .thenReturn(Optional.of(RoleDefinitionMother.any().id(2L).organizationId("other").build()));
        var request = new CreateRoleBindingRequest("USER", "u1", 2L, null);

        // when / then
        assertThatThrownBy(() -> sut.create(request)).isInstanceOf(NotFoundException.class);
        verify(bindingStore, never()).save(any());
    }

    @Test
    void create_rejectsRoleAboveCallerCeiling() {

        // given - a delegated role-admin holding only role:assign tries to bind the Owner role
        when(organization.organizationId()).thenReturn("acme");
        var owner = RoleDefinitionMother.any().id(2L).organizationId("acme").name("Owner")
                .permissions(List.of(RoleDefinitionMother.permission("organization:delete"),
                        RoleDefinitionMother.permission("role:assign")))
                .build();
        when(roleStore.find(2L)).thenReturn(Optional.of(owner));
        when(gate.currentPermissions()).thenReturn(Set.of(Permission.ROLE_ASSIGN));
        var request = new CreateRoleBindingRequest("USER", "attacker-sub", 2L, null);

        // when / then
        assertThatThrownBy(() -> sut.create(request)).isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("organization:delete");
        verify(bindingStore, never()).save(any());
    }

    @Test
    void create_aCodeThisEditionDoesNotKnowIsNoPartOfTheCeiling() {

        // given - the role row still carries a Cloud code, as in a restored dump; this edition grants nothing by it
        when(organization.organizationId()).thenReturn("acme");
        when(roleStore.find(2L)).thenReturn(Optional.of(RoleDefinitionMother.any().id(2L).organizationId("acme")
                .permissions(List.of(RoleDefinitionMother.permission(Permission.MEMBER_READ.code()),
                        RoleDefinitionMother.permission("sso:read")))
                .build()));
        when(gate.currentPermissions()).thenReturn(Set.of(Permission.ROLE_ASSIGN, Permission.MEMBER_READ));
        when(bindingStore.save(any())).thenReturn(SubjectBinding.builder().id(1L).organizationId("acme")
                .subjectType("USER").subjectId("u1").roleId(2L).build());
        var request = new CreateRoleBindingRequest("USER", "u1", 2L, null);

        // when
        sut.create(request);

        // then
        verify(bindingStore).save(any(SubjectBinding.class));
    }

    @Test
    void isSoleOwner_trueForTheLoneUserBindingOnTheOwnerRole() {

        // given
        var owner = RoleDefinitionMother.any().id(2L).organizationId("acme-hub").name("Owner").build();
        when(roleStore.findByName("acme-hub", "Owner")).thenReturn(Optional.of(owner));
        var binding = SubjectBinding.builder().id(1L).organizationId("acme-hub").subjectType("USER").subjectId("alice")
                .roleId(2L).build();
        when(bindingStore.boundTo(2L)).thenReturn(List.of(binding));

        // when / then
        assertThat(sut.isSoleOwner("acme-hub", "alice")).isTrue();
    }

    @Test
    void isSoleOwner_falseWhenAnotherSubjectHoldsTheLoneBinding() {

        // given
        var owner = RoleDefinitionMother.any().id(2L).organizationId("acme-hub").name("Owner").build();
        when(roleStore.findByName("acme-hub", "Owner")).thenReturn(Optional.of(owner));
        var binding = SubjectBinding.builder().id(1L).organizationId("acme-hub").subjectType("USER").subjectId("bob")
                .roleId(2L).build();
        when(bindingStore.boundTo(2L)).thenReturn(List.of(binding));

        // when / then
        assertThat(sut.isSoleOwner("acme-hub", "alice")).isFalse();
    }

    @Test
    void isSoleOwner_falseWhenMoreThanOneBindingHoldsTheOwnerRole() {

        // given - ownership shared, e.g. with a co-owner or a group
        var owner = RoleDefinitionMother.any().id(2L).organizationId("acme-hub").name("Owner").build();
        when(roleStore.findByName("acme-hub", "Owner")).thenReturn(Optional.of(owner));
        var bindingA = SubjectBinding.builder().id(1L).organizationId("acme-hub").subjectType("USER").subjectId("alice")
                .roleId(2L).build();
        var bindingB = SubjectBinding.builder().id(2L).organizationId("acme-hub").subjectType("GROUP")
                .subjectId("admins").roleId(2L).build();
        when(bindingStore.boundTo(2L)).thenReturn(List.of(bindingA, bindingB));

        // when / then
        assertThat(sut.isSoleOwner("acme-hub", "alice")).isFalse();
    }

    @Test
    void isSoleOwner_falseWhenTheLoneBindingIsAGroupNotAUser() {

        // given
        var owner = RoleDefinitionMother.any().id(2L).organizationId("acme-hub").name("Owner").build();
        when(roleStore.findByName("acme-hub", "Owner")).thenReturn(Optional.of(owner));
        var binding = SubjectBinding.builder().id(1L).organizationId("acme-hub").subjectType("GROUP")
                .subjectId("admins").roleId(2L).build();
        when(bindingStore.boundTo(2L)).thenReturn(List.of(binding));

        // when / then
        assertThat(sut.isSoleOwner("acme-hub", "alice")).isFalse();
    }

    @Test
    void isSoleOwner_falseWhenTheOrganizationHasNoOwnerRole() {

        // given - a broken invariant (every Organization is seeded with one), guarded against defensively
        when(roleStore.findByName("acme-hub", "Owner")).thenReturn(Optional.empty());

        // when / then
        assertThat(sut.isSoleOwner("acme-hub", "alice")).isFalse();
        verify(bindingStore, never()).boundTo(any());
    }

    @Test
    void callerHoldsOwnerRole_trueThroughAGroupBinding() {

        // given - the gate matched the caller's group binding to the Owner role
        var owner = RoleDefinitionMother.any().id(2L).organizationId("acme").name("Owner").build();
        when(roleStore.findByName("acme", "Owner")).thenReturn(Optional.of(owner));
        when(gate.currentRoleIds()).thenReturn(Set.of(2L, 7L));

        // when / then
        assertThat(sut.callerHoldsOwnerRole("acme")).isTrue();
    }

    @Test
    void callerHoldsOwnerRole_falseWhenTheCallerHoldsOtherRolesOnly() {

        // given
        var owner = RoleDefinitionMother.any().id(2L).organizationId("acme").name("Owner").build();
        when(roleStore.findByName("acme", "Owner")).thenReturn(Optional.of(owner));
        when(gate.currentRoleIds()).thenReturn(Set.of(7L));

        // when / then
        assertThat(sut.callerHoldsOwnerRole("acme")).isFalse();
    }

    @Test
    void callerHoldsOwnerRole_falseWhenTheOrganizationHasNoOwnerRole() {

        // given - a broken invariant (every Organization is seeded with one), guarded against defensively
        when(roleStore.findByName("acme", "Owner")).thenReturn(Optional.empty());

        // when / then
        assertThat(sut.callerHoldsOwnerRole("acme")).isFalse();
        verify(gate, never()).currentRoleIds();
    }

    @Test
    void assigneesOf_deniedWithoutRoleRead() {

        // given
        doThrow(new ForbiddenException("forbidden")).when(gate).requirePermission(Permission.ROLE_READ);

        // when / then
        assertThatThrownBy(() -> sut.assigneesOf(7L)).isInstanceOf(ForbiddenException.class);
        verify(bindingStore, never()).boundTo(any());
    }
}
