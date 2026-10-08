package dev.axonia.identity.scim;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

import dev.axonia.identity.DirectoryGroupClaims;
import dev.axonia.identity.IdpProvider;
import dev.axonia.identity.OrganizationIdp;
import dev.axonia.identity.OrganizationIdpResolver;
import dev.axonia.identity.internal.DirectoryGroupEntity;
import dev.axonia.identity.internal.DirectoryGroupMemberEntity;
import dev.axonia.identity.internal.DirectoryGroupMemberRepository;
import dev.axonia.identity.internal.DirectoryGroupRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class ScimGroupServiceTest {

    private static final ScimPrincipal PRINCIPAL = new ScimPrincipal("tenant-1");
    private static final Instant NOW = Instant.parse("2026-08-27T09:30:00Z");

    @Mock
    DirectoryGroupRepository groupRepository;

    @Mock
    DirectoryGroupMemberRepository memberRepository;

    @Mock
    ScimGroupMapper mapper;

    @Mock
    OrganizationIdpResolver resolver;

    @Mock
    DirectoryGroupClaims directoryGroupClaims;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private ScimGroupService sut;

    /**
     * The hold policy is wired in for real, not mocked: the behaviour under test is which groups arrive confirmed, and
     * a mocked collaborator would only assert that the service asked something.
     */
    @BeforeEach
    void buildService() {

        sut = new ScimGroupService(groupRepository, memberRepository, mapper, resolver,
                new DirectoryGroupHold(directoryGroupClaims, clock));
    }

    // --- create: confirmation ---

    /**
     * The SCIM client chooses its own externalId and GROUP role bindings match on it, so a push under a key somebody
     * already bound would otherwise confer that binding's role. The group is still created, because refusing it would
     * break the ordinary "bind first, provision second" onboarding order; it simply confers nothing until a role:assign
     * holder says it is the group they meant.
     */
    @Test
    void aScimGroupWhoseKeyARoleBindingIsWaitingOnIsCreatedUnconfirmed() {

        // given
        when(resolver.resolve("acme")).thenReturn(Optional.empty());
        when(directoryGroupClaims.isClaimedByARoleBinding("acme", "Platform Admins")).thenReturn(true);
        when(groupRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        sut.create(new ScimPrincipal("acme"), ScimGroup.builder().displayName("Platform Admins").build());

        // then
        var captor = ArgumentCaptor.forClass(DirectoryGroupEntity.class);
        verify(groupRepository).save(captor.capture());
        assertThat(captor.getValue().getConfirmedAt()).isNull();
    }

    /** A group nobody has pre-bound is ordinary provisioning and is confirmed on creation. */
    @Test
    void aScimGroupNoBindingIsWaitingOnIsConfirmedImmediately() {

        // given
        when(resolver.resolve("acme")).thenReturn(Optional.empty());
        when(groupRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        sut.create(new ScimPrincipal("acme"), ScimGroup.builder().displayName("Everyone").build());

        // then - stamped from the service's Clock, never from the database
        var captor = ArgumentCaptor.forClass(DirectoryGroupEntity.class);
        verify(groupRepository).save(captor.capture());
        assertThat(captor.getValue().getConfirmedAt()).isEqualTo(NOW);
    }

    /**
     * The same guard on the rename path. Without it the hold is one PATCH away from being bypassed: provision a group
     * under a key nobody wants, take the confirmation that earns, then rename it onto the key the binding names.
     */
    @Test
    void aRenameOntoAKeyARoleBindingIsWaitingOnDropsTheConfirmation() {

        // given - an ordinary confirmed group, renamed onto a pre-bound key (non-Entra, so the key follows the name)
        var existing = groupEntityWithId(41L, "sg-rekey", "Everyone").toBuilder().confirmedAt(NOW).build();
        when(groupRepository.findByOrganizationIdAndScimId("tenant-1", "sg-rekey")).thenReturn(Optional.of(existing));
        when(directoryGroupClaims.isClaimedByARoleBinding("tenant-1", "Platform Admins")).thenReturn(true);
        when(groupRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(memberRepository.findByOrganizationIdAndGroupId(any(), any())).thenReturn(List.of());
        when(mapper.toScim(any(), any())).thenReturn(scimGroupOutput("sg-rekey"));

        var patch = ScimPatchRequest.of(List.of(new ScimPatchOperation("replace", "displayName", "Platform Admins")));

        // when
        sut.patch(PRINCIPAL, "sg-rekey", patch);

        // then
        verify(groupRepository)
                .save(argThat(e -> "Platform Admins".equals(e.getReconciliationKey()) && e.getConfirmedAt() == null));
    }

    /** A rename that leaves the reconciliation key alone changes nothing about which bindings match, so it holds. */
    @Test
    void aRenameThatKeepsTheReconciliationKeyKeepsTheConfirmation() {

        // given - Entra reconciles by the external object id, which a displayName rename must not touch
        var existing = DirectoryGroupEntity.builder().id(77L).organizationId("tenant-1").scimId("sg-entra")
                .displayName("Old").reconciliationKey("entra-obj-guid").externalId("entra-obj-guid").confirmedAt(NOW)
                .build();
        when(resolver.resolve("tenant-1"))
                .thenReturn(Optional.of(new OrganizationIdp(IdpProvider.ENTRA, "aad-1", true)));
        when(groupRepository.findByOrganizationIdAndScimId("tenant-1", "sg-entra")).thenReturn(Optional.of(existing));
        when(groupRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(memberRepository.findByOrganizationIdAndGroupId(any(), any())).thenReturn(List.of());
        when(mapper.toScim(any(), any())).thenReturn(scimGroupOutput("sg-entra"));

        var patch = ScimPatchRequest.of(List.of(new ScimPatchOperation("replace", "displayName", "New")));

        // when
        sut.patch(PRINCIPAL, "sg-entra", patch);

        // then - and the claims port is never asked, because no new key is being claimed
        verify(groupRepository).save(argThat(e -> NOW.equals(e.getConfirmedAt())));
        verifyNoInteractions(directoryGroupClaims);
    }

    // --- create: reconciliationKey ---

    @Test
    void create_entraOrganization_usesExternalIdAsReconciliationKey() {

        // given
        when(resolver.resolve("acme"))
                .thenReturn(Optional.of(new OrganizationIdp(IdpProvider.ENTRA, "tenant-acme", true)));
        when(groupRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var in = ScimGroup.builder().externalId("00000000-1111-2222-3333-444444444444").displayName("Engineering")
                .build();

        // when
        sut.create(new ScimPrincipal("acme"), in);

        // then
        var captor = ArgumentCaptor.forClass(DirectoryGroupEntity.class);
        verify(groupRepository).save(captor.capture());
        assertThat(captor.getValue().getReconciliationKey()).isEqualTo("00000000-1111-2222-3333-444444444444");
    }

    @Test
    void create_nonEntraOrganization_usesDisplayNameAsReconciliationKey() {

        // given
        when(resolver.resolve("acme"))
                .thenReturn(Optional.of(new OrganizationIdp(IdpProvider.GENERIC_OIDC, null, false)));
        when(groupRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var in = ScimGroup.builder().externalId("00000000-1111-2222-3333-444444444444").displayName("Engineering")
                .build();

        // when
        sut.create(new ScimPrincipal("acme"), in);

        // then
        var captor = ArgumentCaptor.forClass(DirectoryGroupEntity.class);
        verify(groupRepository).save(captor.capture());
        assertThat(captor.getValue().getReconciliationKey()).isEqualTo("Engineering");
    }

    @Test
    void create_organizationWithoutSsoConnection_usesDisplayNameAsReconciliationKey() {

        // given
        when(resolver.resolve("acme")).thenReturn(Optional.empty());
        when(groupRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var in = ScimGroup.builder().externalId("00000000-1111-2222-3333-444444444444").displayName("Engineering")
                .build();

        // when
        sut.create(new ScimPrincipal("acme"), in);

        // then
        var captor = ArgumentCaptor.forClass(DirectoryGroupEntity.class);
        verify(groupRepository).save(captor.capture());
        assertThat(captor.getValue().getReconciliationKey()).isEqualTo("Engineering");
    }

    @Test
    void create_whenTheReconciliationKeyIsTaken_conflictsRatherThanDuplicating() {

        // given
        when(resolver.resolve(PRINCIPAL.organizationId())).thenReturn(Optional.empty());
        when(groupRepository.findByOrganizationIdAndReconciliationKey(PRINCIPAL.organizationId(), "Platform Guild"))
                .thenReturn(Optional.of(DirectoryGroupEntity.builder().id(7L).build()));

        // when / then
        assertThatThrownBy(() -> sut.create(PRINCIPAL, ScimGroup.builder().displayName("Platform Guild").build()))
                .isInstanceOf(ScimException.class)
                .satisfies(ex -> assertThat(((ScimException) ex).getScimType()).isEqualTo("uniqueness"));
        verify(groupRepository, never()).save(any());
    }

    @Test
    void create_entraOrganizationWithTheExternalIdAlreadyTaken_conflictsRatherThanDuplicating() {

        // given - Entra keys on externalId, which is the arm the customer this guard exists for actually takes
        when(resolver.resolve(PRINCIPAL.organizationId()))
                .thenReturn(Optional.of(new OrganizationIdp(IdpProvider.ENTRA, "tenant-e", true)));
        when(groupRepository.findByOrganizationIdAndReconciliationKey(PRINCIPAL.organizationId(), "entra-obj-guid"))
                .thenReturn(Optional.of(DirectoryGroupEntity.builder().id(3L).build()));
        ScimGroup in = ScimGroup.builder().externalId("entra-obj-guid").displayName("Platform Guild").build();

        // when / then
        assertThatThrownBy(() -> sut.create(PRINCIPAL, in)).isInstanceOf(ScimException.class)
                .satisfies(ex -> assertThat(((ScimException) ex).getScimType()).isEqualTo("uniqueness"));
        verify(groupRepository, never()).save(any());
    }

    @Test
    void create_whenTheUniquenessConstraintRejectsARacingCreate_conflictsRatherThanFailing() {

        // given - two provisioning calls race, both read no existing group, and the database settles it
        when(resolver.resolve(PRINCIPAL.organizationId())).thenReturn(Optional.empty());
        when(groupRepository.save(any()))
                .thenThrow(new DuplicateKeyException("uq_directory_group_organization_reckey"));

        // when / then
        assertThatThrownBy(() -> sut.create(PRINCIPAL, ScimGroup.builder().displayName("Platform Guild").build()))
                .isInstanceOf(ScimException.class)
                .satisfies(ex -> assertThat(((ScimException) ex).getScimType()).isEqualTo("uniqueness"))
                .hasMessageContaining("Platform Guild");
    }

    @Test
    void create_entraOrganizationWithoutExternalId_isRefusedWithAnActionableBadRequest() {

        // given - directory_group.reconciliation_key is NOT NULL, so a null key is a 500 waiting to happen
        when(resolver.resolve("tenant-1"))
                .thenReturn(Optional.of(new OrganizationIdp(IdpProvider.ENTRA, "tenant-e", true)));

        ScimGroup in = ScimGroup.builder().displayName("No External Id").build();

        // when / then
        assertThatThrownBy(() -> sut.create(PRINCIPAL, in)).isInstanceOf(ScimException.class)
                .satisfies(ex -> assertThat(((ScimException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("externalId");
        verify(groupRepository, never()).save(any());
    }

    @Test
    void create_nonEntraOrganizationWithoutDisplayName_isRefusedWithAnActionableBadRequest() {

        // given - nothing validates the request body, and reconciliation_key is NOT NULL on the other arm too
        when(resolver.resolve("tenant-1")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.create(PRINCIPAL, ScimGroup.builder().externalId("ext-1").build()))
                .isInstanceOf(ScimException.class)
                .satisfies(ex -> assertThat(((ScimException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("displayName");
        verify(groupRepository, never()).save(any());
    }

    @Test
    void create_withInitialMembers_insertsGroupMemberRows() {

        // given
        ScimGroup in = ScimGroup.builder().displayName("Devs")
                .members(
                        List.of(new ScimGroupMember("user-scim-1", "Alice"), new ScimGroupMember("user-scim-2", "Bob")))
                .build();

        DirectoryGroupEntity saved = groupEntity("sg-m", "Devs", null);
        saved = DirectoryGroupEntity.builder().id(10L).organizationId("tenant-1").scimId("sg-m").displayName("Devs")
                .reconciliationKey("Devs").build();
        when(groupRepository.save(any())).thenReturn(saved);
        when(memberRepository.findByOrganizationIdAndGroupId(any(), any())).thenReturn(List.of());
        when(mapper.toScim(any(), any())).thenReturn(scimGroupOutput("sg-m"));

        // when
        sut.create(PRINCIPAL, in);

        // then
        verify(memberRepository).insertIgnoreDuplicate("tenant-1", 10L, "user-scim-1");
        verify(memberRepository).insertIgnoreDuplicate("tenant-1", 10L, "user-scim-2");
    }

    // --- get ---

    @Test
    void get_unknown_throwsNotFound() {

        // given
        when(groupRepository.findByOrganizationIdAndScimId("tenant-1", "ghost")).thenReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> sut.get(PRINCIPAL, "ghost")).isInstanceOf(ScimException.class)
                .satisfies(ex -> assertThat(((ScimException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void get_known_returnsMappedGroup() {

        // given
        DirectoryGroupEntity entity = groupEntity("sg-x", "Engineering", null);
        when(groupRepository.findByOrganizationIdAndScimId("tenant-1", "sg-x")).thenReturn(Optional.of(entity));
        when(memberRepository.findByOrganizationIdAndGroupId(any(), any())).thenReturn(List.of());
        ScimGroup expected = scimGroupOutput("sg-x");
        when(mapper.toScim(eq(entity), any())).thenReturn(expected);

        // then
        assertThat(sut.get(PRINCIPAL, "sg-x")).isSameAs(expected);
    }

    // --- patch: add members ---

    @Test
    void patch_addMembers_insertsMemberRows() {

        // given
        DirectoryGroupEntity existing = groupEntityWithId(5L, "sg-patch", "QA");
        when(groupRepository.findByOrganizationIdAndScimId("tenant-1", "sg-patch")).thenReturn(Optional.of(existing));
        when(memberRepository.findByOrganizationIdAndGroupId(any(), any())).thenReturn(List.of());
        when(mapper.toScim(any(), any())).thenReturn(scimGroupOutput("sg-patch"));

        List<Map<String, Object>> membersList = List.of(Map.of("value", "user-a"), Map.of("value", "user-b"));
        ScimPatchRequest patch = ScimPatchRequest.of(List.of(new ScimPatchOperation("add", "members", membersList)));

        // when
        sut.patch(PRINCIPAL, "sg-patch", patch);

        // then
        verify(memberRepository).insertIgnoreDuplicate("tenant-1", 5L, "user-a");
        verify(memberRepository).insertIgnoreDuplicate("tenant-1", 5L, "user-b");
    }

    @Test
    void patch_addMember_alreadyExists_isIdempotent() {

        // given
        // insertIgnoreDuplicate is a void method; default mock does nothing (no exception),
        // which proves ON CONFLICT DO NOTHING semantics are delegated to the repository method.
        DirectoryGroupEntity existing = groupEntityWithId(9L, "sg-idem", "Ops");
        when(groupRepository.findByOrganizationIdAndScimId("tenant-1", "sg-idem")).thenReturn(Optional.of(existing));
        when(memberRepository.findByOrganizationIdAndGroupId(any(), any())).thenReturn(List.of());
        when(mapper.toScim(any(), any())).thenReturn(scimGroupOutput("sg-idem"));

        ScimPatchRequest patch = ScimPatchRequest
                .of(List.of(new ScimPatchOperation("add", "members", List.of(Map.of("value", "already-there")))));

        // then
        assertThatCode(() -> sut.patch(PRINCIPAL, "sg-idem", patch)).doesNotThrowAnyException();
        verify(memberRepository).insertIgnoreDuplicate("tenant-1", 9L, "already-there");
    }

    // --- patch: remove members ---

    @Test
    void patch_removeMembers_deletesMemberRows() {

        // given
        DirectoryGroupEntity existing = groupEntityWithId(6L, "sg-rm", "Security");
        when(groupRepository.findByOrganizationIdAndScimId("tenant-1", "sg-rm")).thenReturn(Optional.of(existing));
        when(memberRepository.findByOrganizationIdAndGroupId(any(), any())).thenReturn(List.of());
        when(mapper.toScim(any(), any())).thenReturn(scimGroupOutput("sg-rm"));

        List<Map<String, Object>> membersList = List.of(Map.of("value", "user-x"));
        ScimPatchRequest patch = ScimPatchRequest.of(List.of(new ScimPatchOperation("remove", "members", membersList)));

        // when
        sut.patch(PRINCIPAL, "sg-rm", patch);

        // then
        verify(memberRepository).deleteByOrganizationIdAndGroupIdAndUserScimId("tenant-1", 6L, "user-x");
        verify(memberRepository, never()).save(any());
    }

    @Test
    void patch_unknownGroup_throwsNotFound() {

        // given
        when(groupRepository.findByOrganizationIdAndScimId("tenant-1", "ghost")).thenReturn(Optional.empty());

        ScimPatchRequest patch = ScimPatchRequest.of(List.of());

        // then
        assertThatThrownBy(() -> sut.patch(PRINCIPAL, "ghost", patch)).isInstanceOf(ScimException.class)
                .satisfies(ex -> assertThat(((ScimException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void patch_displayName_replace_updatesDisplayNameAndReconciliationKey_nonEntra() {

        // given
        DirectoryGroupEntity existing = groupEntityWithId(7L, "sg-rename", "Ops");
        DirectoryGroupEntity saved = groupEntityWithId(7L, "sg-rename", "NewOps");
        when(groupRepository.findByOrganizationIdAndScimId("tenant-1", "sg-rename")).thenReturn(Optional.of(existing));
        when(groupRepository.save(argThat(e -> "NewOps".equals(e.getDisplayName())))).thenReturn(saved);
        when(memberRepository.findByOrganizationIdAndGroupId(any(), any())).thenReturn(List.of());
        when(mapper.toScim(eq(saved), any())).thenReturn(scimGroupOutput("sg-rename"));

        ScimPatchRequest patch = ScimPatchRequest
                .of(List.of(new ScimPatchOperation("replace", "displayName", "NewOps")));

        // when
        sut.patch(PRINCIPAL, "sg-rename", patch);

        // then
        // For non-ENTRA providers the reconciliation key must follow displayName
        verify(groupRepository)
                .save(argThat(e -> "NewOps".equals(e.getDisplayName()) && "NewOps".equals(e.getReconciliationKey())));
        verify(memberRepository, never()).insertIgnoreDuplicate(any(), any(), any());
    }

    @Test
    void patch_displayName_replace_keepsReconciliationKey_forEntra() {

        // given
        DirectoryGroupEntity existing = DirectoryGroupEntity.builder().id(77L).organizationId("tenant-1")
                .scimId("sg-entra").displayName("Old").reconciliationKey("entra-obj-guid").externalId("entra-obj-guid")
                .build();
        DirectoryGroupEntity saved = existing.toBuilder().displayName("New").build();
        when(resolver.resolve("tenant-1"))
                .thenReturn(Optional.of(new OrganizationIdp(IdpProvider.ENTRA, "aad-1", true)));
        when(groupRepository.findByOrganizationIdAndScimId("tenant-1", "sg-entra")).thenReturn(Optional.of(existing));
        when(groupRepository.save(any())).thenReturn(saved);
        when(memberRepository.findByOrganizationIdAndGroupId(any(), any())).thenReturn(List.of());
        when(mapper.toScim(eq(saved), any())).thenReturn(scimGroupOutput("sg-entra"));

        ScimPatchRequest patch = ScimPatchRequest.of(List.of(new ScimPatchOperation("replace", "displayName", "New")));

        // when
        sut.patch(PRINCIPAL, "sg-entra", patch);

        // then
        // For ENTRA the reconciliation key must remain the external object-id, not the displayName
        verify(groupRepository).save(
                argThat(e -> "New".equals(e.getDisplayName()) && "entra-obj-guid".equals(e.getReconciliationKey())));
    }

    @Test
    void patch_displayName_replace_collidesWithAnExistingGroup() {

        // given - a non-Entra rename rewrites the reconciliation key, which is the other write the uniqueness
        // constraint can now refuse. Without translation the administrator would see a 500 for renaming a group.
        DirectoryGroupEntity existing = groupEntityWithId(31L, "sg-clash", "Ops");
        when(groupRepository.findByOrganizationIdAndScimId("tenant-1", "sg-clash")).thenReturn(Optional.of(existing));
        when(groupRepository.save(any()))
                .thenThrow(new DuplicateKeyException("uq_directory_group_organization_reckey"));

        ScimPatchRequest patch = ScimPatchRequest
                .of(List.of(new ScimPatchOperation("replace", "displayName", "Platform Guild")));

        // when / then
        assertThatThrownBy(() -> sut.patch(PRINCIPAL, "sg-clash", patch)).isInstanceOf(ScimException.class)
                .satisfies(ex -> assertThat(((ScimException) ex).getScimType()).isEqualTo("uniqueness"))
                .hasMessageContaining("Platform Guild");
    }

    @Test
    void patch_unsupportedPath_isSkipped_memberOpsStillApplied() {

        // given
        DirectoryGroupEntity existing = groupEntityWithId(8L, "sg-skip", "Ops");
        when(groupRepository.findByOrganizationIdAndScimId("tenant-1", "sg-skip")).thenReturn(Optional.of(existing));
        when(memberRepository.findByOrganizationIdAndGroupId(any(), any())).thenReturn(List.of());
        when(mapper.toScim(any(), any())).thenReturn(scimGroupOutput("sg-skip"));

        // "emails" path is not supported → skipped; the "members" add in same request must still execute
        List<Map<String, Object>> membersList = List.of(Map.of("value", "user-ok"));
        ScimPatchRequest patch = ScimPatchRequest.of(List.of(new ScimPatchOperation("add", "members", membersList),
                new ScimPatchOperation("replace", "emails", "x@y.com")));

        // when
        sut.patch(PRINCIPAL, "sg-skip", patch);

        // then
        verify(memberRepository).insertIgnoreDuplicate("tenant-1", 8L, "user-ok");
        verify(groupRepository, never()).save(any());
    }

    @Test
    void patch_nonAddRemoveOp_isIgnored() {

        // given
        DirectoryGroupEntity existing = groupEntityWithId(8L, "sg-noop", "Devs");
        when(groupRepository.findByOrganizationIdAndScimId("tenant-1", "sg-noop")).thenReturn(Optional.of(existing));
        when(memberRepository.findByOrganizationIdAndGroupId(any(), any())).thenReturn(List.of());
        when(mapper.toScim(any(), any())).thenReturn(scimGroupOutput("sg-noop"));

        // "replace" with path "members" is neither "add" nor "remove" → should be silently ignored
        ScimPatchRequest patch = ScimPatchRequest.of(List.of(new ScimPatchOperation("replace", "members", List.of())));

        // when
        sut.patch(PRINCIPAL, "sg-noop", patch);

        // then
        verify(memberRepository, never()).insertIgnoreDuplicate(any(), any(), any());
        verify(memberRepository, never()).deleteByOrganizationIdAndGroupIdAndUserScimId(any(), any(), any());
    }

    @Test
    void patch_memberValueNull_isFiltered() {

        // given
        DirectoryGroupEntity existing = groupEntityWithId(9L, "sg-null", "Security");
        when(groupRepository.findByOrganizationIdAndScimId("tenant-1", "sg-null")).thenReturn(Optional.of(existing));
        when(memberRepository.findByOrganizationIdAndGroupId(any(), any())).thenReturn(List.of());
        when(mapper.toScim(any(), any())).thenReturn(scimGroupOutput("sg-null"));

        // map with null "value" → filtered out → insertIgnoreDuplicate never called
        Map<String, Object> nullValueMap = new HashMap<>();
        nullValueMap.put("value", null);
        ScimPatchRequest patch = ScimPatchRequest
                .of(List.of(new ScimPatchOperation("add", "members", List.of(nullValueMap))));

        // when
        sut.patch(PRINCIPAL, "sg-null", patch);

        // then
        verify(memberRepository, never()).insertIgnoreDuplicate(any(), any(), any());
    }

    @Test
    void patch_withNonListValue_extractsEmptyList() {

        // given
        DirectoryGroupEntity existing = groupEntityWithId(10L, "sg-str", "Ops");
        when(groupRepository.findByOrganizationIdAndScimId("tenant-1", "sg-str")).thenReturn(Optional.of(existing));
        when(memberRepository.findByOrganizationIdAndGroupId(any(), any())).thenReturn(List.of());
        when(mapper.toScim(any(), any())).thenReturn(scimGroupOutput("sg-str"));

        // string value is not a List → extractMemberValues returns List.of()
        ScimPatchRequest patch = ScimPatchRequest.of(List.of(new ScimPatchOperation("add", "members", "not-a-list")));

        // when
        sut.patch(PRINCIPAL, "sg-str", patch);

        // then
        verify(memberRepository, never()).insertIgnoreDuplicate(any(), any(), any());
    }

    @Test
    void create_withNullMembers_doesNotInsert() {

        // given
        ScimGroup in = ScimGroup.builder().externalId("ext-1").displayName("Ops").build();

        DirectoryGroupEntity saved = groupEntity("sg-null-m", "Ops", "ext-1");
        when(groupRepository.save(any())).thenReturn(saved);
        when(memberRepository.findByOrganizationIdAndGroupId(any(), any())).thenReturn(List.of());
        when(mapper.toScim(any(), any())).thenReturn(scimGroupOutput("sg-null-m"));

        // when
        sut.create(PRINCIPAL, in);

        // then
        verify(memberRepository, never()).insertIgnoreDuplicate(any(), any(), any());
    }

    @Test
    void get_returnsGroupWithLoadedMembers() {

        // given
        DirectoryGroupEntity entity = groupEntityWithId(11L, "sg-with-m", "Platform");
        DirectoryGroupMemberEntity member = DirectoryGroupMemberEntity.builder().organizationId("tenant-1").groupId(11L)
                .userScimId("user-scim-a").build();
        when(groupRepository.findByOrganizationIdAndScimId("tenant-1", "sg-with-m")).thenReturn(Optional.of(entity));
        when(memberRepository.findByOrganizationIdAndGroupId("tenant-1", 11L)).thenReturn(List.of(member));
        when(mapper.toScim(eq(entity), any())).thenReturn(scimGroupOutput("sg-with-m"));

        // when
        sut.get(PRINCIPAL, "sg-with-m");

        // then
        // verify mapper was called with the mapped member (ScimGroupMember with the user's scimId)
        verify(mapper).toScim(eq(entity), eq(List.of(new ScimGroupMember("user-scim-a", null))));
    }

    // --- delete ---

    @Test
    void delete_known_deletesGroup() {

        // given
        DirectoryGroupEntity existing = groupEntityWithId(7L, "sg-del", "Archived");
        when(groupRepository.findByOrganizationIdAndScimId("tenant-1", "sg-del")).thenReturn(Optional.of(existing));

        // when
        sut.delete(PRINCIPAL, "sg-del");

        // then
        verify(groupRepository).delete(existing);
    }

    @Test
    void delete_unknown_throwsNotFound() {

        // given
        when(groupRepository.findByOrganizationIdAndScimId("tenant-1", "ghost")).thenReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> sut.delete(PRINCIPAL, "ghost")).isInstanceOf(ScimException.class)
                .satisfies(ex -> assertThat(((ScimException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- list ---

    @Test
    void list_noFilter_returnsAllGroups() {

        // given
        DirectoryGroupEntity entity = groupEntity("sg1", "Eng", null);
        when(groupRepository.findByOrganizationId(eq("tenant-1"), any())).thenReturn(List.of(entity));
        when(memberRepository.findByOrganizationIdAndGroupId(any(), any())).thenReturn(List.of());
        when(mapper.toScim(eq(entity), any())).thenReturn(scimGroupOutput("sg1"));

        // when
        var result = sut.list(PRINCIPAL, null);

        // then
        assertThat(result.totalResults()).isEqualTo(1);
    }

    @Test
    void list_blankFilter_treatedAsNoFilter() {

        // given
        when(groupRepository.findByOrganizationId(eq("tenant-1"), any())).thenReturn(List.of());

        // when
        var result = sut.list(PRINCIPAL, "  ");

        // then
        assertThat(result.totalResults()).isEqualTo(0);
    }

    @Test
    void list_displayNameFilter_returnsFilteredGroups() {

        // given
        DirectoryGroupEntity entity = groupEntity("sg2", "Platform", null);
        when(groupRepository.findByOrganizationIdAndDisplayName("tenant-1", "Platform")).thenReturn(List.of(entity));
        when(memberRepository.findByOrganizationIdAndGroupId(any(), any())).thenReturn(List.of());
        when(mapper.toScim(eq(entity), any())).thenReturn(scimGroupOutput("sg2"));

        // when
        var result = sut.list(PRINCIPAL, "displayName eq \"Platform\"");

        // then
        assertThat(result.totalResults()).isEqualTo(1);
    }

    @Test
    void list_unknownFilterAttr_returnsEmpty() {

        // when
        var result = sut.list(PRINCIPAL, "id eq \"some-id\"");

        // then
        assertThat(result.totalResults()).isEqualTo(0);
    }

    @Test
    void list_malformedFilter_returnsEmpty() {

        // when
        var result = sut.list(PRINCIPAL, "not-valid-filter");

        // then
        assertThat(result.totalResults()).isEqualTo(0);
    }

    // --- replace ---

    @Test
    void replace_known_updatesAndReplacesMembers() {

        // given
        DirectoryGroupEntity existing = groupEntityWithId(20L, "sg-r", "OldName");
        DirectoryGroupEntity saved = groupEntityWithId(20L, "sg-r", "NewName");
        ScimGroup expected = scimGroupOutput("sg-r");
        when(groupRepository.findByOrganizationIdAndScimId("tenant-1", "sg-r")).thenReturn(Optional.of(existing));
        when(groupRepository.save(any())).thenReturn(saved);
        when(memberRepository.findByOrganizationIdAndGroupId(any(), any())).thenReturn(List.of());
        when(mapper.toScim(eq(saved), any())).thenReturn(expected);

        ScimGroup in = ScimGroup.builder().id("sg-r").displayName("NewName")
                .members(List.of(new ScimGroupMember("user-1", null))).build();

        // when
        ScimGroup result = sut.replace(PRINCIPAL, "sg-r", in);

        // then
        assertThat(result).isSameAs(expected);
        verify(memberRepository).deleteAllByOrganizationIdAndGroupId("tenant-1", 20L);
        verify(memberRepository).insertIgnoreDuplicate("tenant-1", 20L, "user-1");
    }

    @Test
    void replace_withNullMembers_skipsInsert() {

        // given
        DirectoryGroupEntity existing = groupEntityWithId(21L, "sg-r2", "Name");
        DirectoryGroupEntity saved = groupEntityWithId(21L, "sg-r2", "Name");
        when(groupRepository.findByOrganizationIdAndScimId("tenant-1", "sg-r2")).thenReturn(Optional.of(existing));
        when(groupRepository.save(any())).thenReturn(saved);
        when(memberRepository.findByOrganizationIdAndGroupId(any(), any())).thenReturn(List.of());
        when(mapper.toScim(any(), any())).thenReturn(scimGroupOutput("sg-r2"));

        ScimGroup in = ScimGroup.builder().id("sg-r2").displayName("Name").build();

        // when
        sut.replace(PRINCIPAL, "sg-r2", in);

        // then
        verify(memberRepository).deleteAllByOrganizationIdAndGroupId("tenant-1", 21L);
        verify(memberRepository, never()).insertIgnoreDuplicate(any(), any(), any());
    }

    @Test
    void replace_unknown_throwsNotFound() {

        // given
        when(groupRepository.findByOrganizationIdAndScimId("tenant-1", "ghost")).thenReturn(Optional.empty());

        ScimGroup in = ScimGroup.builder().displayName("X").build();

        // then
        assertThatThrownBy(() -> sut.replace(PRINCIPAL, "ghost", in)).isInstanceOf(ScimException.class)
                .satisfies(ex -> assertThat(((ScimException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- helpers ---

    private DirectoryGroupEntity groupEntity(String scimId, String displayName, String externalId) {

        return DirectoryGroupEntity.builder().organizationId("tenant-1").scimId(scimId).displayName(displayName)
                .externalId(externalId).reconciliationKey(displayName != null ? displayName : externalId).build();
    }

    private DirectoryGroupEntity groupEntityWithId(Long id, String scimId, String displayName) {

        return DirectoryGroupEntity.builder().id(id).organizationId("tenant-1").scimId(scimId).displayName(displayName)
                .reconciliationKey(displayName).build();
    }

    private ScimGroup scimGroupOutput(String id) {

        return ScimGroup.builder().id(id).displayName("Engineers").members(List.of()).build();
    }
}
