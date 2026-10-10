package dev.axonia.access.application;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.access.api.FoundingRoles;
import dev.axonia.access.api.OwnerBinding;
import dev.axonia.access.api.Permission;
import dev.axonia.access.api.spi.PermissionCatalogue;
import dev.axonia.access.application.port.out.RoleBindingStore;
import dev.axonia.access.application.port.out.RoleStore;
import dev.axonia.access.domain.RoleDefinition;
import dev.axonia.access.domain.RoleDefinitionMother;
import dev.axonia.access.domain.SubjectBinding;
import dev.axonia.shared.api.error.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessSeederTest {

    @Mock
    private RoleStore roleStore;

    @Mock
    private RoleBindingStore bindingStore;

    private AccessSeeder sut;

    @BeforeEach
    void setUp() {

        PermissionCatalogue narrowerForOneOrganization = organizationId -> "rust-practitioners".equals(organizationId)
                ? Optional.of(Set.of(Permission.ROLE_WRITE, Permission.MEMBER_READ))
                : Optional.empty();
        sut = new AccessSeeder(roleStore, bindingStore,
                new ApplicablePermissions(List.of(narrowerForOneOrganization), new KnownPermissions(List.of())));
    }

    @Test
    void seedAccess_givesTheOwnerWhatAppliesInTheOrganizationAndAnEmptyMember() {
        // given
        when(roleStore.save(any(RoleDefinition.class)))
                .thenAnswer(inv -> ((RoleDefinition) inv.getArgument(0)).toBuilder().id(41L).build());
        when(bindingStore.save(any(SubjectBinding.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        sut.seedAccess("rust-practitioners", new FoundingRoles(new OwnerBinding.User("sub-123", "Ada Lovelace"),
                "Full control of this place", "Baseline membership of this place"));

        // then
        var roles = ArgumentCaptor.forClass(RoleDefinition.class);
        verify(roleStore, times(2)).save(roles.capture());
        var owner = roles.getAllValues().get(0);
        var member = roles.getAllValues().get(1);
        assertThat(owner.slug()).isEqualTo("owner");
        assertThat(owner.system()).isTrue();
        assertThat(owner.description()).isEqualTo("Full control of this place");
        assertThat(owner.permissions()).containsExactlyInAnyOrder("role:write", "member:read");
        assertThat(member.slug()).isEqualTo("member");
        assertThat(member.description()).isEqualTo("Baseline membership of this place");
        assertThat(member.permissions()).isEmpty();

        var binding = ArgumentCaptor.forClass(SubjectBinding.class);
        verify(bindingStore).save(binding.capture());
        assertThat(binding.getValue().subjectType()).isEqualTo("USER");
        assertThat(binding.getValue().subjectId()).isEqualTo("sub-123");
        assertThat(binding.getValue().roleId()).isEqualTo(41L);
    }

    @Test
    void seedEnterpriseAccess_bindsOwnerToTheBootstrapAdministratorAsAUser() {
        // given
        when(roleStore.save(any(RoleDefinition.class))).thenReturn(RoleDefinitionMother.any().id(1L).build());

        // when
        sut.seedEnterpriseAccess("acme", new OwnerBinding.User("kc-user-id-1", "admin@acme.example"));

        // then
        var binding = ArgumentCaptor.forClass(SubjectBinding.class);
        verify(bindingStore).save(binding.capture());
        assertThat(binding.getValue().subjectType()).isEqualTo("USER");
        assertThat(binding.getValue().subjectId()).isEqualTo("kc-user-id-1");
        assertThat(binding.getValue().subjectLabel()).isEqualTo("admin@acme.example");
        assertThat(binding.getValue().organizationId()).isEqualTo("acme");
        assertThat(binding.getValue().roleId()).isEqualTo(1L);
    }

    @Test
    void seedEnterpriseAccess_givesTheOwnerEveryPermissionWhenNoCatalogueNarrowsIt() {
        // given
        when(roleStore.save(any(RoleDefinition.class))).thenReturn(RoleDefinitionMother.any().id(1L).build());

        // when
        sut.seedEnterpriseAccess("acme", new OwnerBinding.User("kc-user-id-1", "admin@acme.example"));

        // then
        var roles = ArgumentCaptor.forClass(RoleDefinition.class);
        verify(roleStore, times(2)).save(roles.capture());
        var ownerPermissions = roles.getAllValues().getFirst().permissions().stream().collect(Collectors.toSet());
        assertThat(ownerPermissions)
                .containsExactlyInAnyOrderElementsOf(Permission.OPEN.stream().map(Permission::code).toList());
    }

    @Test
    void seedEnterpriseAccess_describesTheRolesOfAnOrganization() {
        // given
        when(roleStore.save(any(RoleDefinition.class))).thenReturn(RoleDefinitionMother.any().id(1L).build());

        // when
        sut.seedEnterpriseAccess("acme", new OwnerBinding.User("kc-user-id-1", "admin@acme.example"));

        // then
        var roles = ArgumentCaptor.forClass(RoleDefinition.class);
        verify(roleStore, times(2)).save(roles.capture());
        assertThat(roles.getAllValues()).extracting(RoleDefinition::description).containsExactly(
                "Full control of the organization, including deletion and ownership transfer.",
                "Baseline membership: browse and participate in communities");
    }

    @Test
    void seedEnterpriseAccess_alsoSeedsAMemberRole() {
        // given
        when(roleStore.save(any(RoleDefinition.class))).thenReturn(RoleDefinitionMother.any().id(1L).build());

        // when
        sut.seedEnterpriseAccess("acme", new OwnerBinding.User("kc-user-id-1", "admin@acme.example"));

        // then
        var roles = ArgumentCaptor.forClass(RoleDefinition.class);
        verify(roleStore, times(2)).save(roles.capture());
        assertThat(roles.getAllValues()).extracting(RoleDefinition::slug).containsExactly("owner", "member");
    }

    @Test
    void seedEnterpriseAccess_aGroupOwnerIsBoundAsAGroup() {
        // given
        when(roleStore.save(any(RoleDefinition.class))).thenReturn(RoleDefinitionMother.any().id(1L).build());

        // when
        sut.seedEnterpriseAccess("acme", new OwnerBinding.Group("8f1c-owners", "Owners"));

        // then
        var binding = ArgumentCaptor.forClass(SubjectBinding.class);
        verify(bindingStore).save(binding.capture());
        assertThat(binding.getValue().subjectType()).isEqualTo("GROUP");
        assertThat(binding.getValue().subjectId()).isEqualTo("8f1c-owners");
        assertThat(binding.getValue().subjectLabel()).isEqualTo("Owners");
    }

    @Test
    void seedEnterpriseAccess_aUserOwnerIsBoundAsAUser() {
        // given
        when(roleStore.save(any(RoleDefinition.class))).thenReturn(RoleDefinitionMother.any().id(1L).build());

        // when
        sut.seedEnterpriseAccess("acme", new OwnerBinding.User("3b7a-user-uuid", "admin@acme.example"));

        // then
        var binding = ArgumentCaptor.forClass(SubjectBinding.class);
        verify(bindingStore).save(binding.capture());
        assertThat(binding.getValue().subjectType()).isEqualTo("USER");
    }

    @Test
    void bindMemberIfAbsent_bindsFirstTimeJoiner() {
        // given
        when(bindingStore.listIn("hub")).thenReturn(List.of());
        when(roleStore.findByName("hub", "Member"))
                .thenReturn(Optional.of(RoleDefinition.builder().id(7L).organizationId("hub").name("Member").build()));
        when(bindingStore.save(any(SubjectBinding.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.bindMemberIfAbsent("hub", "sub-9", "Joiner");

        // then
        assertThat(actual).isTrue();
    }

    @Test
    void bindMemberIfAbsent_isNoOpForExistingMember() {
        // given
        when(bindingStore.listIn("hub")).thenReturn(List.of(SubjectBinding.builder().organizationId("hub")
                .subjectType("USER").subjectId("sub-9").roleId(7L).build()));

        // when
        var actual = sut.bindMemberIfAbsent("hub", "sub-9", "Joiner");

        // then
        assertThat(actual).isFalse();
    }

    @Test
    void bindMemberIfAbsent_failsLoudlyWhenMemberRoleMissing() {
        // given
        when(bindingStore.listIn("broken-hub")).thenReturn(List.of());
        when(roleStore.findByName("broken-hub", "Member")).thenReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> sut.bindMemberIfAbsent("broken-hub", "sub-9", "Joiner"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void unbindUser_deletesEveryUserBindingOfTheSubject() {
        // given: the subject holds two bindings (e.g. Member + a custom role); another subject's binding survives,
        // and a GROUP binding that happens to share the same subjectId survives too (it isn't a USER binding).
        var memberBinding = SubjectBinding.builder().organizationId("rust-hub").subjectType("USER").subjectId("sub-2")
                .subjectLabel("Bob").roleId(2L).build();
        var extraBinding = SubjectBinding.builder().organizationId("rust-hub").subjectType("USER").subjectId("sub-2")
                .subjectLabel("Bob").roleId(3L).build();
        var otherSubject = SubjectBinding.builder().organizationId("rust-hub").subjectType("USER").subjectId("sub-9")
                .subjectLabel("Eve").roleId(2L).build();
        var groupBinding = SubjectBinding.builder().organizationId("rust-hub").subjectType("GROUP").subjectId("sub-2")
                .subjectLabel("Not a user").roleId(4L).build();
        when(bindingStore.listIn("rust-hub"))
                .thenReturn(List.of(memberBinding, extraBinding, otherSubject, groupBinding));

        // when
        var removed = sut.unbindUser("rust-hub", "sub-2");

        // then
        assertThat(removed).isTrue();
        verify(bindingStore).delete(memberBinding);
        verify(bindingStore).delete(extraBinding);
        verify(bindingStore, never()).delete(otherSubject);
        verify(bindingStore, never()).delete(groupBinding);
    }

    @Test
    void unbindUser_noBindingIsNoMembership() {
        // given
        when(bindingStore.listIn("rust-hub")).thenReturn(List.of());

        // when / then
        assertThat(sut.unbindUser("rust-hub", "sub-2")).isFalse();
        verify(bindingStore, never()).delete(any());
    }

    @Test
    void ensureGroupOwner_bindsTheGroupToTheOwnerRoleAndTouchesNoOtherBinding() {
        // given
        when(roleStore.findByName("acme", "Owner"))
                .thenReturn(Optional.of(RoleDefinition.builder().id(41L).organizationId("acme").name("Owner").build()));
        when(bindingStore.exists("acme", "GROUP", "platform-admins", 41L)).thenReturn(false);

        // when
        var actual = sut.ensureGroupOwner("acme", new OwnerBinding.Group("platform-admins", "Owners"));

        // then
        assertThat(actual).isTrue();
        var binding = ArgumentCaptor.forClass(SubjectBinding.class);
        verify(bindingStore).exists("acme", "GROUP", "platform-admins", 41L);
        verify(bindingStore).save(binding.capture());
        verifyNoMoreInteractions(bindingStore);
        assertThat(binding.getValue())
                .extracting(SubjectBinding::organizationId, SubjectBinding::subjectType, SubjectBinding::subjectId,
                        SubjectBinding::subjectLabel, SubjectBinding::roleId)
                .containsExactly("acme", "GROUP", "platform-admins", "Owners", 41L);
    }

    @Test
    void ensureGroupOwner_writesNothingWhenTheGroupIsAlreadyOwner() {
        // given
        when(roleStore.findByName("acme", "Owner"))
                .thenReturn(Optional.of(RoleDefinition.builder().id(41L).organizationId("acme").name("Owner").build()));
        when(bindingStore.exists("acme", "GROUP", "axonia-owners", 41L)).thenReturn(true);

        // when
        var actual = sut.ensureGroupOwner("acme", new OwnerBinding.Group("axonia-owners", "Owners"));

        // then
        assertThat(actual).isFalse();
        verify(bindingStore, never()).save(any());
    }

    @Test
    void ensureGroupOwner_refusesAnOrganizationWithNoOwnerRole() {
        // given
        when(roleStore.findByName("acme", "Owner")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.ensureGroupOwner("acme", new OwnerBinding.Group("axonia-owners", "Owners")))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("acme");
        verifyNoMoreInteractions(bindingStore);
    }
}
