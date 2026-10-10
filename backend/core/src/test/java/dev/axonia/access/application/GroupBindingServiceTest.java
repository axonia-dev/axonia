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
import dev.axonia.access.domain.SubjectBinding;
import dev.axonia.identity.api.DirectoryGroupConfirmation;
import dev.axonia.identity.api.GroupSource;
import dev.axonia.identity.api.IdentityProviderDirectory;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class GroupBindingServiceTest {

    @Mock
    private IdentityProviderDirectory directory;

    @Mock
    private RoleBindingStore bindingStore;

    @Mock
    private AuthorizationGate gate;

    @Mock
    private OrganizationContext organization;

    @Mock
    private DirectoryGroupConfirmation directoryGroupConfirmation;

    private GroupBindingService sut;

    private Boolean groupsApplyHere;

    @BeforeEach
    void setUp() {

        DirectoryGroupsPolicy policy = organizationId -> Optional.ofNullable(groupsApplyHere);
        sut = new GroupBindingService(directory, bindingStore, gate, organization, directoryGroupConfirmation,
                new GroupBindingApplicability(List.of(policy)));
    }

    @Test
    void list_requiresRoleReadAndAnnotatesGroupsWithBindings() {

        // given
        when(organization.organizationId()).thenReturn("acme");
        var group1 = new IdentityProviderDirectory.IdpGroup("grp-admin", "Admin Group");
        var group2 = new IdentityProviderDirectory.IdpGroup("grp-dev", "Dev Group");
        when(directory.listGroups("acme"))
                .thenReturn(new IdentityProviderDirectory.DirectoryListing(GroupSource.SCIM, List.of(group1, group2)));

        var groupBinding = SubjectBinding.builder().id(1L).organizationId("acme").subjectType("GROUP")
                .subjectId("grp-admin").subjectLabel("Admin Group").roleId(2L).build();
        var userBinding = SubjectBinding.builder().id(2L).organizationId("acme").subjectType("USER").subjectId("u1")
                .roleId(2L).build();
        when(bindingStore.listIn("acme")).thenReturn(List.of(groupBinding, userBinding));

        // when
        var actual = sut.list();

        // then
        verify(gate).requirePermission(Permission.ROLE_READ);
        assertThat(actual.groups()).hasSize(2);

        var adminView = actual.groups().stream().filter(v -> "grp-admin".equals(v.groupId())).findFirst().orElseThrow();
        assertThat(adminView.displayName()).isEqualTo("Admin Group");
        assertThat(adminView.bindings()).hasSize(1);
        assertThat(adminView.bindings().get(0).roleId()).isEqualTo(2L);
        assertThat(adminView.bindings().get(0).subjectLabel()).isEqualTo("Admin Group");

        var devView = actual.groups().stream().filter(v -> "grp-dev".equals(v.groupId())).findFirst().orElseThrow();
        assertThat(devView.bindings()).isEmpty();
    }

    @Test
    void list_surfacesSource_andIncludesValueAuthoredBindingsNotInDirectory() {
        when(organization.organizationId()).thenReturn("acme");
        var listed = new IdentityProviderDirectory.IdpGroup("b7c1", "Platform");
        when(directory.listGroups("acme"))
                .thenReturn(new IdentityProviderDirectory.DirectoryListing(GroupSource.SCIM, List.of(listed)));

        var listedBinding = SubjectBinding.builder().id(1L).organizationId("acme").subjectType("GROUP")
                .subjectId("b7c1").subjectLabel("Platform").roleId(2L).build();
        var manualBinding = SubjectBinding.builder().id(2L).organizationId("acme").subjectType("GROUP")
                .subjectId("data-leads").subjectLabel("data-leads").roleId(3L).build();
        when(bindingStore.listIn("acme")).thenReturn(List.of(listedBinding, manualBinding));

        var actual = sut.list();

        assertThat(actual.source()).isEqualTo("SCIM");
        var platform = actual.groups().stream().filter(v -> "b7c1".equals(v.groupId())).findFirst().orElseThrow();
        assertThat(platform.inDirectory()).isTrue();
        assertThat(platform.bindings()).hasSize(1);
        var manual = actual.groups().stream().filter(v -> "data-leads".equals(v.groupId())).findFirst().orElseThrow();
        assertThat(manual.inDirectory()).isFalse();
        assertThat(manual.displayName()).isEqualTo("data-leads");
        assertThat(manual.bindings()).hasSize(1);
    }

    @Test
    void list_valueAuthoredBindingWithoutLabel_fallsBackToSubjectIdAsDisplayName() {
        when(organization.organizationId()).thenReturn("acme");
        var listed = new IdentityProviderDirectory.IdpGroup("b7c1", "Platform");
        when(directory.listGroups("acme"))
                .thenReturn(new IdentityProviderDirectory.DirectoryListing(GroupSource.SCIM, List.of(listed)));

        var manualBinding = SubjectBinding.builder().id(1L).organizationId("acme").subjectType("GROUP")
                .subjectId("orphan-grp").subjectLabel(null).roleId(3L).build();
        when(bindingStore.listIn("acme")).thenReturn(List.of(manualBinding));

        var actual = sut.list();

        var orphan = actual.groups().stream().filter(v -> "orphan-grp".equals(v.groupId())).findFirst().orElseThrow();
        assertThat(orphan.inDirectory()).isFalse();
        assertThat(orphan.displayName()).isEqualTo("orphan-grp");
        assertThat(orphan.bindings()).hasSize(1);
    }

    @Test
    void list_valueAuthoredBindingWithBlankLabel_fallsBackToSubjectIdAsDisplayName() {
        when(organization.organizationId()).thenReturn("acme");
        var listed = new IdentityProviderDirectory.IdpGroup("b7c1", "Platform");
        when(directory.listGroups("acme"))
                .thenReturn(new IdentityProviderDirectory.DirectoryListing(GroupSource.SCIM, List.of(listed)));

        var manualBinding = SubjectBinding.builder().id(1L).organizationId("acme").subjectType("GROUP")
                .subjectId("orphan-grp").subjectLabel("").roleId(3L).build();
        when(bindingStore.listIn("acme")).thenReturn(List.of(manualBinding));

        var actual = sut.list();

        var orphan = actual.groups().stream().filter(v -> "orphan-grp".equals(v.groupId())).findFirst().orElseThrow();
        assertThat(orphan.inDirectory()).isFalse();
        assertThat(orphan.displayName()).isEqualTo("orphan-grp");
        assertThat(orphan.bindings()).hasSize(1);
    }

    @Test
    void list_groupHeldUnconfirmed_isFlaggedSoTheConfirmationIsVisible() {

        // given - SCIM pushed a group under a key this binding was already waiting on, so it is being held
        when(organization.organizationId()).thenReturn("acme");
        when(directory.listGroups("acme")).thenReturn(new IdentityProviderDirectory.DirectoryListing(GroupSource.SCIM,
                List.of(new IdentityProviderDirectory.IdpGroup("platform-admins", "Platform Admins"),
                        new IdentityProviderDirectory.IdpGroup("everyone", "Everyone"))));
        when(directoryGroupConfirmation.keysAwaitingConfirmation("acme")).thenReturn(Set.of("platform-admins"));
        var binding = SubjectBinding.builder().id(1L).organizationId("acme").subjectType("GROUP")
                .subjectId("platform-admins").subjectLabel("Platform Admins").roleId(2L).build();
        when(bindingStore.listIn("acme")).thenReturn(List.of(binding));

        // when
        var actual = sut.list();

        // then - without this flag the release valve has no entry point in the console
        assertThat(actual.groups()).filteredOn(v -> "platform-admins".equals(v.groupId())).singleElement()
                .satisfies(v -> assertThat(v.awaitingConfirmation()).isTrue());
        assertThat(actual.groups()).filteredOn(v -> "everyone".equals(v.groupId())).singleElement()
                .satisfies(v -> assertThat(v.awaitingConfirmation()).isFalse());
    }

    @Test
    void list_valueAuthoredBindingWhoseGroupIsHeld_isFlaggedToo() {

        // given - the binding was authored before the directory pushed anything, and the push is now being held
        when(organization.organizationId()).thenReturn("acme");
        when(directory.listGroups("acme"))
                .thenReturn(new IdentityProviderDirectory.DirectoryListing(GroupSource.NONE, List.of()));
        when(directoryGroupConfirmation.keysAwaitingConfirmation("acme")).thenReturn(Set.of("platform-admins"));
        var binding = SubjectBinding.builder().id(1L).organizationId("acme").subjectType("GROUP")
                .subjectId("platform-admins").subjectLabel("Platform Admins").roleId(2L).build();
        when(bindingStore.listIn("acme")).thenReturn(List.of(binding));

        // when
        var actual = sut.list();

        // then
        assertThat(actual.groups()).singleElement().satisfies(v -> assertThat(v.awaitingConfirmation()).isTrue());
    }

    @Test
    void list_userBindingsAreExcluded() {

        // given
        when(organization.organizationId()).thenReturn("acme");
        when(directory.listGroups("acme")).thenReturn(new IdentityProviderDirectory.DirectoryListing(GroupSource.SCIM,
                List.of(new IdentityProviderDirectory.IdpGroup("grp-dev", "Dev Group"))));
        var userBinding = SubjectBinding.builder().id(1L).organizationId("acme").subjectType("USER")
                .subjectId("grp-dev").roleId(1L).build();
        when(bindingStore.listIn("acme")).thenReturn(List.of(userBinding));

        // when
        var actual = sut.list();

        // then
        assertThat(actual.groups()).hasSize(1);
        assertThat(actual.groups().get(0).bindings()).isEmpty();
    }

    @Test
    void list_whereGroupsDoNotApply_isNotFoundAndNeverCallsDirectory() {

        // given - an Organization with no directory, so there is no group listing there to serve
        when(organization.organizationId()).thenReturn("rust-hub");
        groupsApplyHere = false;

        // when / then
        assertThatThrownBy(() -> sut.list()).isInstanceOf(NotFoundException.class);
        verify(directory, never()).listGroups(any());
    }

    @Test
    void list_forbidden_propagatesAndNeverCallsDirectory() {

        // given
        doThrow(new ForbiddenException("forbidden")).when(gate).requirePermission(Permission.ROLE_READ);

        // when / then
        assertThatThrownBy(() -> sut.list()).isInstanceOf(ForbiddenException.class);
        verify(directory, never()).listGroups(any());
    }
}
