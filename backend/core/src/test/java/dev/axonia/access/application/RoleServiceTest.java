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
import dev.axonia.access.api.spi.PermissionCatalogue;
import dev.axonia.access.application.port.out.OrganizationContext;
import dev.axonia.access.application.port.out.RoleStore;
import dev.axonia.access.domain.RoleDefinitionMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class RoleServiceTest {

    @Mock
    private RoleStore roleStore;

    @Mock
    private AuthorizationGate gate;

    @Mock
    private OrganizationContext organization;

    @Mock
    private dev.axonia.access.application.port.out.RoleBindingStore bindingStore;

    private RoleService sut;

    private Set<Permission> narrowedCatalogue;

    @BeforeEach
    void setUp() {

        lenient().when(organization.organizationId()).thenReturn("default");
        PermissionCatalogue catalogue = organizationId -> Optional.ofNullable(narrowedCatalogue);
        var known = new KnownPermissions(List.of());
        sut = new RoleService(roleStore, gate, organization, bindingStore,
                new ApplicablePermissions(List.of(catalogue), known), known);
    }

    @Test
    void list_requiresRoleReadAndReturnsMappedRoles() {

        // given
        var entity = RoleDefinitionMother.any().id(1L).name("Admin")
                .permissions(List.of(RoleDefinitionMother.permission(Permission.ROLE_READ.code()))).build();
        when(roleStore.listIn("default")).thenReturn(List.of(entity));

        // when
        var actual = sut.list();

        // then
        verify(gate).requirePermission(Permission.ROLE_READ);
        assertThat(actual).hasSize(1);
        var role = actual.get(0);
        assertThat(role.id()).isEqualTo(1L);
        assertThat(role.name()).isEqualTo("Admin");
        assertThat(role.permissions()).containsExactly(Permission.ROLE_READ);
    }

    @Test
    void list_mapsDescription() {

        // given
        var entity = RoleDefinitionMother.any().id(7L).name("Curator").description("Edits the library")
                .permissions(List.of()).build();
        when(roleStore.listIn("default")).thenReturn(List.of(entity));

        // when
        var actual = sut.list();

        // then
        assertThat(actual.get(0).description()).isEqualTo("Edits the library");
    }

    @Test
    void list_unknownPermissionCodeIsDropped() {

        // given
        var entity = RoleDefinitionMother.any().id(2L)
                .permissions(List.of(RoleDefinitionMother.permission("unknown:code"))).build();
        when(roleStore.listIn("default")).thenReturn(List.of(entity));

        // when
        var actual = sut.list();

        // then
        assertThat(actual).hasSize(1);
        assertThat(actual.get(0).permissions()).isEmpty();
    }

    @Test
    void myRoles_noBindings_returnsEmptyWithoutQueryingRoles() {

        // given
        when(gate.currentRoleIds()).thenReturn(Set.of());

        // when
        var actual = sut.myRoles();

        // then
        assertThat(actual).isEmpty();
        verify(roleStore, never()).listIn(any());
        verify(gate, never()).requirePermission(any());
    }

    @Test
    void myRoles_returnsBoundRoleIdsAndNamesSortedByName() {

        // given - the repository order is deliberately unsorted so the assertion proves the sort
        var admin = RoleDefinitionMother.any().id(1L).name("Admin").build();
        var auditor = RoleDefinitionMother.any().id(2L).name("Auditor").build();
        var unbound = RoleDefinitionMother.any().id(3L).name("Zealot").build();
        when(roleStore.listIn("default")).thenReturn(List.of(auditor, admin, unbound));
        when(gate.currentRoleIds()).thenReturn(Set.of(1L, 2L));

        // when
        var actual = sut.myRoles();

        // then - the id travels beside the name, so the profile can tint a role the way the roster does
        assertThat(actual).containsExactly(new MyRole(1L, "Admin"), new MyRole(2L, "Auditor"));
        verify(gate, never()).requirePermission(any());
    }

    @Test
    void myRoles_idWithNoMatchingRoleInTheOrganizationIsDropped() {

        // given - a stale binding pointing at a role id no role in this organization has
        var admin = RoleDefinitionMother.any().id(1L).name("Admin").build();
        when(roleStore.listIn("default")).thenReturn(List.of(admin));
        when(gate.currentRoleIds()).thenReturn(Set.of(1L, 99L));

        // when
        var actual = sut.myRoles();

        // then
        assertThat(actual).containsExactly(new MyRole(1L, "Admin"));
        verify(gate, never()).requirePermission(any());
    }

    @Test
    void create_persistsCustomRoleWithPermissions() {

        // given
        when(roleStore.findByName("default", "Curator")).thenReturn(java.util.Optional.empty());
        when(gate.currentPermissions()).thenReturn(Set.of(Permission.LIBRARY_TYPE_READ));
        when(roleStore.save(any())).thenAnswer(inv -> {
            var e = (dev.axonia.access.domain.RoleDefinition) inv.getArgument(0);
            return e.toBuilder().id(99L).build();
        });

        // when
        var role = sut.create(new CreateRoleRequest("Curator", "Edits library", List.of("library_type:read")));

        // then
        verify(gate).requirePermission(Permission.ROLE_WRITE);
        assertThat(role.id()).isEqualTo(99L);
        assertThat(role.permissions()).containsExactly(Permission.LIBRARY_TYPE_READ);
    }

    @Test
    void create_rejectsAPermissionThatDoesNotApplyInTheOrganization() {

        narrowedCatalogue = Set.of(Permission.LIBRARY_TYPE_READ);
        when(roleStore.findByName("default", "Auditor")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> sut.create(new CreateRoleRequest("Auditor", null, List.of("scim:write"))))
                .isInstanceOf(dev.axonia.shared.api.error.ValidationException.class).hasMessageContaining("scim:write");
        verify(roleStore, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void create_allowsAPermissionThatAppliesInANarrowedOrganization() {

        narrowedCatalogue = Set.of(Permission.LIBRARY_TYPE_READ);
        when(roleStore.findByName("default", "Librarian")).thenReturn(java.util.Optional.empty());
        when(gate.currentPermissions()).thenReturn(Set.of(Permission.LIBRARY_TYPE_READ));
        when(roleStore.save(any())).thenAnswer(inv -> {
            var e = (dev.axonia.access.domain.RoleDefinition) inv.getArgument(0);
            return e.toBuilder().id(9L).build();
        });

        var role = sut.create(new CreateRoleRequest("Librarian", null, List.of("library_type:read")));

        assertThat(role.name()).isEqualTo("Librarian");
    }

    @Test
    void create_rejectsPermissionAboveCallerCeiling() {

        // given - a delegated role-author holding only role:write tries to grant organization:delete
        when(roleStore.findByName("default", "Escalator")).thenReturn(java.util.Optional.empty());
        when(gate.currentPermissions()).thenReturn(Set.of(Permission.ROLE_WRITE));

        // when / then
        assertThatThrownBy(() -> sut.create(new CreateRoleRequest("Escalator", null, List.of("organization:delete"))))
                .isInstanceOf(dev.axonia.shared.api.error.ForbiddenException.class)
                .hasMessageContaining("organization:delete");
        verify(roleStore, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void create_duplicateName_throwsConflict() {

        when(roleStore.findByName("default", "Curator"))
                .thenReturn(java.util.Optional.of(RoleDefinitionMother.any().id(1L).name("Curator").build()));

        assertThatThrownBy(() -> sut.create(new CreateRoleRequest("Curator", null, List.of())))
                .isInstanceOf(dev.axonia.shared.api.error.ConflictException.class);
    }

    @Test
    void create_unknownPermission_throwsValidation() {

        when(roleStore.findByName("default", "Curator")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> sut.create(new CreateRoleRequest("Curator", null, List.of("bogus:read"))))
                .isInstanceOf(dev.axonia.shared.api.error.ValidationException.class);
    }

    @Test
    void create_refusesACloudPermissionAsUnknownInTheOpenEdition() {

        when(roleStore.findByName("default", "Gatekeeper")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.create(new CreateRoleRequest("Gatekeeper", null, List.of("sso:write"))))
                .isInstanceOf(dev.axonia.shared.api.error.ValidationException.class)
                .hasMessageContaining("Unknown permission: sso:write");
        verify(roleStore, never()).save(any());
    }

    @Test
    void update_persistsChanges() {

        // given
        when(roleStore.find(3L))
                .thenReturn(java.util.Optional.of(RoleDefinitionMother.any().organizationId("default").id(3L).build()));
        when(roleStore.findByName("default", "Curator")).thenReturn(java.util.Optional.empty());
        when(gate.currentPermissions()).thenReturn(Set.of(Permission.MEMBER_READ));
        when(roleStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var role = sut.update(3L, new UpdateRoleRequest("Curator", "desc", List.of("member:read")));

        // then
        verify(gate).requirePermission(Permission.ROLE_WRITE);
        assertThat(role.name()).isEqualTo("Curator");
        assertThat(role.description()).isEqualTo("desc");
        assertThat(role.permissions()).containsExactlyInAnyOrder(Permission.MEMBER_READ);
    }

    @Test
    void update_rejectsPermissionAboveCallerCeiling() {

        // given
        when(roleStore.find(3L))
                .thenReturn(java.util.Optional.of(RoleDefinitionMother.any().organizationId("default").id(3L).build()));
        when(gate.currentPermissions()).thenReturn(Set.of(Permission.ROLE_WRITE));

        // when / then
        assertThatThrownBy(() -> sut.update(3L, new UpdateRoleRequest("Curator", null, List.of("ownership:transfer"))))
                .isInstanceOf(dev.axonia.shared.api.error.ForbiddenException.class)
                .hasMessageContaining("ownership:transfer");
    }

    @Test
    void update_allowsFlippingAnInReachPermissionOnARoleThatReachesFurther() {

        // given - the role already grants organization:export, which the caller does not hold
        when(roleStore.find(3L)).thenReturn(java.util.Optional.of(RoleDefinitionMother.any().organizationId("default")
                .id(3L).permissions(List.of(RoleDefinitionMother.permission(Permission.ORGANIZATION_EXPORT.code())))
                .build()));
        when(roleStore.findByName("default", "Support Lead")).thenReturn(java.util.Optional.empty());
        when(gate.currentPermissions()).thenReturn(Set.of(Permission.ROLE_WRITE, Permission.MEMBER_READ));
        when(roleStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when - member:read is added and organization:export is left exactly as it was
        var role = sut.update(3L,
                new UpdateRoleRequest("Support Lead", null, List.of("organization:export", "member:read")));

        // then
        assertThat(role.permissions()).containsExactlyInAnyOrder(Permission.ORGANIZATION_EXPORT,
                Permission.MEMBER_READ);
    }

    @Test
    void update_allowsRenamingARoleThatReachesFurther() {

        // given - the role grants organization:export, which the caller does not hold, and nothing else changes
        when(roleStore.find(3L)).thenReturn(java.util.Optional.of(RoleDefinitionMother.any().organizationId("default")
                .id(3L).name("Payments")
                .permissions(List.of(RoleDefinitionMother.permission(Permission.ORGANIZATION_EXPORT.code()))).build()));
        when(roleStore.findByName("default", "Collections")).thenReturn(java.util.Optional.empty());
        when(gate.currentPermissions()).thenReturn(Set.of(Permission.ROLE_WRITE));
        when(roleStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when - a rename alone, with the permission set left exactly as it was
        var role = sut.update(3L, new UpdateRoleRequest("Collections", null, List.of("organization:export")));

        // then - the ceiling measures what moves, and a name is not authority
        assertThat(role.name()).isEqualTo("Collections");
        assertThat(role.permissions()).containsExactly(Permission.ORGANIZATION_EXPORT);
    }

    @Test
    void update_stillRefusesRemovingAPermissionBeyondTheCallerCeiling() {

        // given - the same role and the same caller
        when(roleStore.find(3L)).thenReturn(java.util.Optional.of(RoleDefinitionMother.any().organizationId("default")
                .id(3L).permissions(List.of(RoleDefinitionMother.permission(Permission.ORGANIZATION_EXPORT.code())))
                .build()));
        when(gate.currentPermissions()).thenReturn(Set.of(Permission.ROLE_WRITE, Permission.MEMBER_READ));

        // when / then - dropping organization:export is the direction the ceiling must keep refusing
        assertThatThrownBy(() -> sut.update(3L, new UpdateRoleRequest("Support Lead", null, List.of("member:read"))))
                .isInstanceOf(dev.axonia.shared.api.error.ForbiddenException.class)
                .hasMessageContaining("organization:export");
    }

    @Test
    void update_unknownPermission_throwsValidation() {

        when(roleStore.find(3L))
                .thenReturn(java.util.Optional.of(RoleDefinitionMother.any().organizationId("default").id(3L).build()));
        when(roleStore.findByName("default", "Curator")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> sut.update(3L, new UpdateRoleRequest("Curator", null, List.of("bogus:read"))))
                .isInstanceOf(dev.axonia.shared.api.error.ValidationException.class);
    }

    @Test
    void update_removesRoleWriteFromOwnRole_throwsConflict() {

        when(roleStore.find(1L)).thenReturn(java.util.Optional.of(roleGrantingRoleWrite(1L, "Tenant Owner")));
        when(gate.currentRoleIds()).thenReturn(Set.of(1L));

        assertThatThrownBy(() -> sut.update(1L, new UpdateRoleRequest("Tenant Owner", null, List.of("role:read"))))
                .isInstanceOf(dev.axonia.shared.api.error.ConflictException.class).hasMessageContaining("rely on");
    }

    @Test
    void update_removesLastRoleWrite_throwsConflict() {

        var owner = roleGrantingRoleWrite(1L, "Tenant Owner");
        when(roleStore.find(1L)).thenReturn(java.util.Optional.of(owner));
        when(gate.currentRoleIds()).thenReturn(Set.of());
        when(roleStore.listIn("default")).thenReturn(List.of(owner));

        assertThatThrownBy(() -> sut.update(1L, new UpdateRoleRequest("Tenant Owner", null, List.of())))
                .isInstanceOf(dev.axonia.shared.api.error.ConflictException.class).hasMessageContaining("manageable");
    }

    @Test
    void update_removesRoleWriteWhileAnotherRoleKeepsIt_succeeds() {

        var owner = roleGrantingRoleWrite(1L, "Tenant Owner");
        when(roleStore.find(1L)).thenReturn(java.util.Optional.of(owner));
        when(gate.currentRoleIds()).thenReturn(Set.of());
        when(roleStore.listIn("default")).thenReturn(List.of(owner, roleGrantingRoleWrite(2L, "Org Admin")));
        when(roleStore.findByName("default", "Tenant Owner")).thenReturn(java.util.Optional.empty());
        when(gate.currentPermissions()).thenReturn(Set.of(Permission.ROLE_READ, Permission.ROLE_WRITE));
        when(roleStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var role = sut.update(1L, new UpdateRoleRequest("Tenant Owner", null, List.of("role:read")));

        assertThat(role.permissions()).containsExactly(Permission.ROLE_READ);
    }

    /**
     * A role author may only put permissions into a role that they hold. The same has to be true of taking them out: a
     * role:write holder with no organization:export could otherwise PUT permissions: [] onto the Finance role and take
     * organization:export off everybody bound to it, which is authority over a permission they were never trusted with.
     */
    @Test
    void aRoleAuthorCannotRemoveAPermissionTheyDoNotHold() {

        var finance = RoleDefinitionMother.any().id(4L).organizationId("default").name("Finance")
                .permissions(List.of(RoleDefinitionMother.permission(Permission.ORGANIZATION_EXPORT.code()))).build();
        when(roleStore.find(4L)).thenReturn(java.util.Optional.of(finance));
        when(gate.currentPermissions()).thenReturn(Set.of(Permission.ROLE_WRITE, Permission.ROLE_READ));

        assertThatThrownBy(() -> sut.update(4L, new UpdateRoleRequest("Finance", null, List.of())))
                .isInstanceOf(dev.axonia.shared.api.error.ForbiddenException.class)
                .hasMessageContaining(Permission.ORGANIZATION_EXPORT.code());
    }

    /**
     * Removing a permission the caller does hold is ordinary role administration and stays allowed - alongside a
     * permission that is kept, so both outcomes of "is this permission being removed" are exercised in one edit.
     */
    @Test
    void aRoleAuthorMayRemoveAPermissionTheyHold() {

        var finance = RoleDefinitionMother.any().id(4L).organizationId("default").name("Finance")
                .permissions(List.of(RoleDefinitionMother.permission(Permission.ORGANIZATION_EXPORT.code()),
                        RoleDefinitionMother.permission(Permission.MEMBER_READ.code())))
                .build();
        when(roleStore.find(4L)).thenReturn(java.util.Optional.of(finance));
        when(gate.currentPermissions()).thenReturn(Set.of(Permission.ROLE_WRITE, Permission.ROLE_READ,
                Permission.ORGANIZATION_EXPORT, Permission.MEMBER_READ));
        when(roleStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var updated = sut.update(4L, new UpdateRoleRequest("Finance", null, List.of("member:read")));

        assertThat(updated.permissions()).containsExactly(Permission.MEMBER_READ);
    }

    @Test
    void delete_lastRoleGrantingRoleWrite_throwsConflict() {

        var owner = roleGrantingRoleWrite(1L, "Tenant Owner");
        when(roleStore.find(1L)).thenReturn(java.util.Optional.of(owner));
        when(gate.currentRoleIds()).thenReturn(Set.of());
        when(roleStore.listIn("default")).thenReturn(List.of(owner));

        assertThatThrownBy(() -> sut.delete(1L)).isInstanceOf(dev.axonia.shared.api.error.ConflictException.class)
                .hasMessageContaining("manageable");
    }

    @Test
    void update_removesLastRoleAssign_throwsConflict() {

        var assigner = roleGrantingRoleAssign(1L, "Org Admin");
        when(roleStore.find(1L)).thenReturn(java.util.Optional.of(assigner));
        when(gate.currentRoleIds()).thenReturn(Set.of());
        when(roleStore.listIn("default")).thenReturn(List.of(assigner));

        assertThatThrownBy(() -> sut.update(1L, new UpdateRoleRequest("Org Admin", null, List.of("role:read"))))
                .isInstanceOf(dev.axonia.shared.api.error.ConflictException.class).hasMessageContaining("assignable");
    }

    @Test
    void update_removesRoleAssignFromOwnRole_throwsConflict() {

        when(roleStore.find(1L)).thenReturn(java.util.Optional.of(roleGrantingRoleAssign(1L, "Org Admin")));
        when(gate.currentRoleIds()).thenReturn(Set.of(1L));

        assertThatThrownBy(() -> sut.update(1L, new UpdateRoleRequest("Org Admin", null, List.of("role:read"))))
                .isInstanceOf(dev.axonia.shared.api.error.ConflictException.class).hasMessageContaining("rely on");
    }

    private static dev.axonia.access.domain.RoleDefinition roleGrantingRoleWrite(Long id, String name) {

        return RoleDefinitionMother.any().organizationId("default").id(id).name(name)
                .permissions(List.of(RoleDefinitionMother.permission(Permission.ROLE_WRITE.code()))).build();
    }

    private static dev.axonia.access.domain.RoleDefinition roleGrantingRoleAssign(Long id, String name) {

        return RoleDefinitionMother.any().organizationId("default").id(id).name(name)
                .permissions(List.of(RoleDefinitionMother.permission(Permission.ROLE_ASSIGN.code()))).build();
    }

    @Test
    void delete_withAssignments_throwsConflict() {

        when(roleStore.find(2L))
                .thenReturn(java.util.Optional.of(RoleDefinitionMother.any().organizationId("default").id(2L).build()));
        when(bindingStore.boundTo(2L))
                .thenReturn(List.of(dev.axonia.access.domain.SubjectBinding.builder().id(5L).roleId(2L).build()));

        assertThatThrownBy(() -> sut.delete(2L)).isInstanceOf(dev.axonia.shared.api.error.ConflictException.class);
    }

    @Test
    void delete_customRoleNoAssignments_deletes() {

        when(roleStore.find(2L))
                .thenReturn(java.util.Optional.of(RoleDefinitionMother.any().organizationId("default").id(2L).build()));
        when(bindingStore.boundTo(2L)).thenReturn(List.of());

        sut.delete(2L);

        verify(roleStore).deleteById(2L);
    }

    @Test
    void update_duplicateName_throwsConflict() {

        when(roleStore.find(3L))
                .thenReturn(java.util.Optional.of(RoleDefinitionMother.any().organizationId("default").id(3L).build()));
        when(roleStore.findByName("default", "Curator"))
                .thenReturn(java.util.Optional.of(RoleDefinitionMother.any().id(99L).name("Curator").build()));

        assertThatThrownBy(() -> sut.update(3L, new UpdateRoleRequest("Curator", null, List.of())))
                .isInstanceOf(dev.axonia.shared.api.error.ConflictException.class);
    }

    @Test
    void update_rejectsSystemRole() {

        when(roleStore.find(1L)).thenReturn(java.util.Optional
                .of(RoleDefinitionMother.any().organizationId("default").id(1L).name("Owner").system(true).build()));

        assertThatThrownBy(() -> sut.update(1L, new UpdateRoleRequest("Owner", "x", List.of("role:read"))))
                .isInstanceOf(dev.axonia.shared.api.error.ConflictException.class).hasMessageContaining("System roles");
    }

    @Test
    void delete_rejectsSystemRole() {

        when(roleStore.find(1L)).thenReturn(java.util.Optional
                .of(RoleDefinitionMother.any().organizationId("default").id(1L).name("Owner").system(true).build()));

        assertThatThrownBy(() -> sut.delete(1L)).isInstanceOf(dev.axonia.shared.api.error.ConflictException.class)
                .hasMessageContaining("System roles");
    }

    @Test
    void delete_differentTenant_throwsNotFound() {

        when(roleStore.find(5L))
                .thenReturn(java.util.Optional.of(RoleDefinitionMother.any().organizationId("other").id(5L).build()));

        assertThatThrownBy(() -> sut.delete(5L)).isInstanceOf(dev.axonia.shared.api.error.NotFoundException.class);
    }
}
