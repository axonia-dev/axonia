package dev.axonia.access;

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

import dev.axonia.NotFoundException;
import dev.axonia.access.internal.ApplicablePermissions;
import dev.axonia.access.internal.RoleBindingEntity;
import dev.axonia.access.internal.RoleBindingRepository;
import dev.axonia.access.internal.RoleEntity;
import dev.axonia.access.internal.RolePermissionEntity;
import dev.axonia.access.internal.RoleRepository;
import dev.axonia.access.mother.RoleEntityMother;

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
    private RoleRepository roleRepository;

    @Mock
    private RoleBindingRepository bindingRepository;

    private AccessSeeder sut;

    @BeforeEach
    void setUp() {

        PermissionCatalogue narrowerForOneOrganization = organizationId -> "rust-practitioners".equals(organizationId)
                ? Optional.of(Set.of(Permission.ROLE_WRITE, Permission.MEMBER_READ))
                : Optional.empty();
        sut = new AccessSeeder(roleRepository, bindingRepository,
                new ApplicablePermissions(List.of(narrowerForOneOrganization), new KnownPermissions(List.of())));
    }

    @Test
    void seedAccess_givesTheOwnerWhatAppliesInTheOrganizationAndAnEmptyMember() {
        // given
        when(roleRepository.save(any(RoleEntity.class)))
                .thenAnswer(inv -> ((RoleEntity) inv.getArgument(0)).toBuilder().id(41L).build());
        when(bindingRepository.save(any(RoleBindingEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        sut.seedAccess("rust-practitioners", new FoundingRoles(new OwnerBinding.User("sub-123", "Ada Lovelace"),
                "Full control of this place", "Baseline membership of this place"));

        // then
        var roles = ArgumentCaptor.forClass(RoleEntity.class);
        verify(roleRepository, times(2)).save(roles.capture());
        var owner = roles.getAllValues().get(0);
        var member = roles.getAllValues().get(1);
        assertThat(owner.getSlug()).isEqualTo("owner");
        assertThat(owner.isSystem()).isTrue();
        assertThat(owner.getDescription()).isEqualTo("Full control of this place");
        assertThat(owner.getPermissions()).extracting(RolePermissionEntity::getPermission)
                .containsExactlyInAnyOrder("role:write", "member:read");
        assertThat(member.getSlug()).isEqualTo("member");
        assertThat(member.getDescription()).isEqualTo("Baseline membership of this place");
        assertThat(member.getPermissions()).isEmpty();

        var binding = ArgumentCaptor.forClass(RoleBindingEntity.class);
        verify(bindingRepository).save(binding.capture());
        assertThat(binding.getValue().getSubjectType()).isEqualTo("USER");
        assertThat(binding.getValue().getSubjectId()).isEqualTo("sub-123");
        assertThat(binding.getValue().getRoleId()).isEqualTo(41L);
    }

    @Test
    void seedEnterpriseAccess_bindsOwnerToTheBootstrapAdministratorAsAUser() {
        // given
        when(roleRepository.save(any(RoleEntity.class))).thenReturn(RoleEntityMother.any().id(1L).build());

        // when
        sut.seedEnterpriseAccess("acme", new OwnerBinding.User("kc-user-id-1", "admin@acme.example"));

        // then
        var binding = ArgumentCaptor.forClass(RoleBindingEntity.class);
        verify(bindingRepository).save(binding.capture());
        assertThat(binding.getValue().getSubjectType()).isEqualTo("USER");
        assertThat(binding.getValue().getSubjectId()).isEqualTo("kc-user-id-1");
        assertThat(binding.getValue().getSubjectLabel()).isEqualTo("admin@acme.example");
        assertThat(binding.getValue().getOrganizationId()).isEqualTo("acme");
        assertThat(binding.getValue().getRoleId()).isEqualTo(1L);
    }

    @Test
    void seedEnterpriseAccess_givesTheOwnerEveryPermissionWhenNoCatalogueNarrowsIt() {
        // given
        when(roleRepository.save(any(RoleEntity.class))).thenReturn(RoleEntityMother.any().id(1L).build());

        // when
        sut.seedEnterpriseAccess("acme", new OwnerBinding.User("kc-user-id-1", "admin@acme.example"));

        // then
        var roles = ArgumentCaptor.forClass(RoleEntity.class);
        verify(roleRepository, times(2)).save(roles.capture());
        var ownerPermissions = roles.getAllValues().getFirst().getPermissions().stream()
                .map(RolePermissionEntity::getPermission).collect(Collectors.toSet());
        assertThat(ownerPermissions)
                .containsExactlyInAnyOrderElementsOf(Permission.OPEN.stream().map(Permission::code).toList());
    }

    @Test
    void seedEnterpriseAccess_describesTheRolesOfAnOrganization() {
        // given
        when(roleRepository.save(any(RoleEntity.class))).thenReturn(RoleEntityMother.any().id(1L).build());

        // when
        sut.seedEnterpriseAccess("acme", new OwnerBinding.User("kc-user-id-1", "admin@acme.example"));

        // then
        var roles = ArgumentCaptor.forClass(RoleEntity.class);
        verify(roleRepository, times(2)).save(roles.capture());
        assertThat(roles.getAllValues()).extracting(RoleEntity::getDescription).containsExactly(
                "Full control of the organization, including deletion and ownership transfer.",
                "Baseline membership: browse and participate in communities");
    }

    @Test
    void seedEnterpriseAccess_alsoSeedsAMemberRole() {
        // given
        when(roleRepository.save(any(RoleEntity.class))).thenReturn(RoleEntityMother.any().id(1L).build());

        // when
        sut.seedEnterpriseAccess("acme", new OwnerBinding.User("kc-user-id-1", "admin@acme.example"));

        // then
        var roles = ArgumentCaptor.forClass(RoleEntity.class);
        verify(roleRepository, times(2)).save(roles.capture());
        assertThat(roles.getAllValues()).extracting(RoleEntity::getSlug).containsExactly("owner", "member");
    }

    @Test
    void seedEnterpriseAccess_aGroupOwnerIsBoundAsAGroup() {
        // given
        when(roleRepository.save(any(RoleEntity.class))).thenReturn(RoleEntityMother.any().id(1L).build());

        // when
        sut.seedEnterpriseAccess("acme", new OwnerBinding.Group("8f1c-owners", "Owners"));

        // then
        var binding = ArgumentCaptor.forClass(RoleBindingEntity.class);
        verify(bindingRepository).save(binding.capture());
        assertThat(binding.getValue().getSubjectType()).isEqualTo("GROUP");
        assertThat(binding.getValue().getSubjectId()).isEqualTo("8f1c-owners");
        assertThat(binding.getValue().getSubjectLabel()).isEqualTo("Owners");
    }

    @Test
    void seedEnterpriseAccess_aUserOwnerIsBoundAsAUser() {
        // given
        when(roleRepository.save(any(RoleEntity.class))).thenReturn(RoleEntityMother.any().id(1L).build());

        // when
        sut.seedEnterpriseAccess("acme", new OwnerBinding.User("3b7a-user-uuid", "admin@acme.example"));

        // then
        var binding = ArgumentCaptor.forClass(RoleBindingEntity.class);
        verify(bindingRepository).save(binding.capture());
        assertThat(binding.getValue().getSubjectType()).isEqualTo("USER");
    }

    @Test
    void bindMemberIfAbsent_bindsFirstTimeJoiner() {
        // given
        when(bindingRepository.findByOrganizationId("hub")).thenReturn(List.of());
        when(roleRepository.findByOrganizationIdAndName("hub", "Member"))
                .thenReturn(Optional.of(RoleEntity.builder().id(7L).organizationId("hub").name("Member").build()));
        when(bindingRepository.save(any(RoleBindingEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.bindMemberIfAbsent("hub", "sub-9", "Joiner");

        // then
        assertThat(actual).isTrue();
    }

    @Test
    void bindMemberIfAbsent_isNoOpForExistingMember() {
        // given
        when(bindingRepository.findByOrganizationId("hub")).thenReturn(List.of(RoleBindingEntity.builder()
                .organizationId("hub").subjectType("USER").subjectId("sub-9").roleId(7L).build()));

        // when
        var actual = sut.bindMemberIfAbsent("hub", "sub-9", "Joiner");

        // then
        assertThat(actual).isFalse();
    }

    @Test
    void bindMemberIfAbsent_failsLoudlyWhenMemberRoleMissing() {
        // given
        when(bindingRepository.findByOrganizationId("broken-hub")).thenReturn(List.of());
        when(roleRepository.findByOrganizationIdAndName("broken-hub", "Member")).thenReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> sut.bindMemberIfAbsent("broken-hub", "sub-9", "Joiner"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void unbindUser_deletesEveryUserBindingOfTheSubject() {
        // given: the subject holds two bindings (e.g. Member + a custom role); another subject's binding survives,
        // and a GROUP binding that happens to share the same subjectId survives too (it isn't a USER binding).
        var memberBinding = RoleBindingEntity.builder().organizationId("rust-hub").subjectType("USER")
                .subjectId("sub-2").subjectLabel("Bob").roleId(2L).build();
        var extraBinding = RoleBindingEntity.builder().organizationId("rust-hub").subjectType("USER").subjectId("sub-2")
                .subjectLabel("Bob").roleId(3L).build();
        var otherSubject = RoleBindingEntity.builder().organizationId("rust-hub").subjectType("USER").subjectId("sub-9")
                .subjectLabel("Eve").roleId(2L).build();
        var groupBinding = RoleBindingEntity.builder().organizationId("rust-hub").subjectType("GROUP")
                .subjectId("sub-2").subjectLabel("Not a user").roleId(4L).build();
        when(bindingRepository.findByOrganizationId("rust-hub"))
                .thenReturn(List.of(memberBinding, extraBinding, otherSubject, groupBinding));

        // when
        var removed = sut.unbindUser("rust-hub", "sub-2");

        // then
        assertThat(removed).isTrue();
        verify(bindingRepository).delete(memberBinding);
        verify(bindingRepository).delete(extraBinding);
        verify(bindingRepository, never()).delete(otherSubject);
        verify(bindingRepository, never()).delete(groupBinding);
    }

    @Test
    void unbindUser_noBindingIsNoMembership() {
        // given
        when(bindingRepository.findByOrganizationId("rust-hub")).thenReturn(List.of());

        // when / then
        assertThat(sut.unbindUser("rust-hub", "sub-2")).isFalse();
        verify(bindingRepository, never()).delete(any());
    }

    @Test
    void ensureGroupOwner_bindsTheGroupToTheOwnerRoleAndTouchesNoOtherBinding() {
        // given
        when(roleRepository.findByOrganizationIdAndName("acme", "Owner"))
                .thenReturn(Optional.of(RoleEntity.builder().id(41L).organizationId("acme").name("Owner").build()));
        when(bindingRepository.existsByOrganizationIdAndSubjectTypeAndSubjectIdAndRoleId("acme", "GROUP",
                "platform-admins", 41L)).thenReturn(false);

        // when
        var actual = sut.ensureGroupOwner("acme", new OwnerBinding.Group("platform-admins", "Owners"));

        // then
        assertThat(actual).isTrue();
        var binding = ArgumentCaptor.forClass(RoleBindingEntity.class);
        verify(bindingRepository).existsByOrganizationIdAndSubjectTypeAndSubjectIdAndRoleId("acme", "GROUP",
                "platform-admins", 41L);
        verify(bindingRepository).save(binding.capture());
        verifyNoMoreInteractions(bindingRepository);
        assertThat(binding.getValue())
                .extracting(RoleBindingEntity::getOrganizationId, RoleBindingEntity::getSubjectType,
                        RoleBindingEntity::getSubjectId, RoleBindingEntity::getSubjectLabel,
                        RoleBindingEntity::getRoleId)
                .containsExactly("acme", "GROUP", "platform-admins", "Owners", 41L);
    }

    @Test
    void ensureGroupOwner_writesNothingWhenTheGroupIsAlreadyOwner() {
        // given
        when(roleRepository.findByOrganizationIdAndName("acme", "Owner"))
                .thenReturn(Optional.of(RoleEntity.builder().id(41L).organizationId("acme").name("Owner").build()));
        when(bindingRepository.existsByOrganizationIdAndSubjectTypeAndSubjectIdAndRoleId("acme", "GROUP",
                "axonia-owners", 41L)).thenReturn(true);

        // when
        var actual = sut.ensureGroupOwner("acme", new OwnerBinding.Group("axonia-owners", "Owners"));

        // then
        assertThat(actual).isFalse();
        verify(bindingRepository, never()).save(any());
    }

    @Test
    void ensureGroupOwner_refusesAnOrganizationWithNoOwnerRole() {
        // given
        when(roleRepository.findByOrganizationIdAndName("acme", "Owner")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.ensureGroupOwner("acme", new OwnerBinding.Group("axonia-owners", "Owners")))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("acme");
        verifyNoMoreInteractions(bindingRepository);
    }
}
