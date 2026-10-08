package dev.axonia.identity.scim;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import dev.axonia.identity.SuccessionAdoption;
import dev.axonia.identity.internal.UserEntity;
import dev.axonia.identity.internal.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class ScimUserServiceTest {

    private static final ScimPrincipal PRINCIPAL = new ScimPrincipal("tenant-1");

    @Mock
    UserRepository userRepository;

    @Mock
    ScimUserMapper mapper;

    @Mock
    SuccessionAdoption successions;

    @InjectMocks
    ScimUserService sut;

    // --- create ---

    @Test
    void create_insertsWithOrgActiveAndScimId_andReturnsScimUser() {

        // given
        ScimUser in = scimUserInput("alice@acme.test", "ext-001", "Alice");
        when(mapper.getUserName(in)).thenReturn("alice@acme.test");
        when(mapper.getExternalId(in)).thenReturn("ext-001");
        when(mapper.getDisplayName(in)).thenReturn("Alice");
        when(userRepository.findByOrganizationIdAndExternalId("tenant-1", "ext-001")).thenReturn(Optional.empty());
        when(userRepository.findByOrganizationIdAndEmail("tenant-1", "alice@acme.test")).thenReturn(Optional.empty());

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        UserEntity saved = UserEntity.builder().id(1L).organizationId("tenant-1").scimId("any-uuid")
                .externalId("ext-001").displayName("Alice").email("alice@acme.test").active(true).build();
        when(userRepository.save(captor.capture())).thenReturn(saved);

        ScimUser expected = scimUserOutput("any-uuid");
        when(mapper.toScim(saved)).thenReturn(expected);

        // when
        ScimUser result = sut.create(PRINCIPAL, in);

        // then
        assertThat(result).isSameAs(expected);

        UserEntity captured = captor.getValue();
        assertThat(captured.getOrganizationId()).isEqualTo("tenant-1");
        assertThat(captured.getActive()).isTrue();
        assertThat(captured.getScimId()).isNotNull().isNotBlank();
        assertThat(captured.getEmail()).isEqualTo("alice@acme.test");
        assertThat(captured.getExternalId()).isEqualTo("ext-001");
        assertThat(captured.getUserId()).isNull();
    }

    @Test
    void create_withExistingSub_returnsScimUser() {

        // given
        ScimUser in = scimUserInput("bob@acme.test", "ext-002", "Bob");
        when(mapper.getUserName(in)).thenReturn("bob@acme.test");
        when(mapper.getExternalId(in)).thenReturn("ext-002");
        when(mapper.getDisplayName(in)).thenReturn("Bob");
        when(userRepository.findByOrganizationIdAndExternalId("tenant-1", "ext-002")).thenReturn(Optional.empty());
        when(userRepository.findByOrganizationIdAndEmail("tenant-1", "bob@acme.test")).thenReturn(Optional.empty());

        UserEntity savedWithSub = UserEntity.builder().id(2L).organizationId("tenant-1").scimId("uuid-2")
                .externalId("ext-002").displayName("Bob").email("bob@acme.test").active(true).userId("sub-bob").build();
        when(userRepository.save(any())).thenReturn(savedWithSub);
        when(mapper.toScim(savedWithSub)).thenReturn(scimUserOutput("uuid-2"));

        // when
        var result = sut.create(PRINCIPAL, in);

        // then
        assertThat(result).isEqualTo(scimUserOutput("uuid-2"));
    }

    @Test
    void create_withNullExternalId_skipsExternalIdCheck() {

        // given
        // externalId == null → skip the findByOrganizationIdAndExternalId check
        ScimUser in = scimUserInput("jane@acme.test", null, "Jane");
        when(mapper.getUserName(in)).thenReturn("jane@acme.test");
        when(mapper.getExternalId(in)).thenReturn(null);
        when(mapper.getDisplayName(in)).thenReturn("Jane");
        when(userRepository.findByOrganizationIdAndEmail("tenant-1", "jane@acme.test")).thenReturn(Optional.empty());

        UserEntity saved = UserEntity.builder().id(3L).organizationId("tenant-1").scimId("uuid-jane")
                .displayName("Jane").email("jane@acme.test").active(true).build();
        when(userRepository.save(any())).thenReturn(saved);
        when(mapper.toScim(saved)).thenReturn(scimUserOutput("uuid-jane"));

        // when
        sut.create(PRINCIPAL, in);

        // then
        verify(userRepository, never()).findByOrganizationIdAndExternalId(anyString(), any());
    }

    @Test
    void create_duplicateExternalId_throwsConflict() {

        // given
        ScimUser in = scimUserInput("carol@acme.test", "ext-dup", "Carol");
        when(mapper.getUserName(in)).thenReturn("carol@acme.test");
        when(mapper.getExternalId(in)).thenReturn("ext-dup");
        when(userRepository.findByOrganizationIdAndExternalId("tenant-1", "ext-dup"))
                .thenReturn(Optional.of(existingEntity("ext-dup", "carol@acme.test")));

        // then
        assertThatThrownBy(() -> sut.create(PRINCIPAL, in)).isInstanceOf(ScimException.class)
                .satisfies(ex -> assertThat(((ScimException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(userRepository, never()).save(any());
    }

    @Test
    void create_duplicateEmail_throwsConflict() {

        // given
        ScimUser in = scimUserInput("dave@acme.test", "ext-003", "Dave");
        when(mapper.getUserName(in)).thenReturn("dave@acme.test");
        when(mapper.getExternalId(in)).thenReturn("ext-003");
        when(userRepository.findByOrganizationIdAndExternalId("tenant-1", "ext-003")).thenReturn(Optional.empty());
        when(userRepository.findByOrganizationIdAndEmail("tenant-1", "dave@acme.test"))
                .thenReturn(Optional.of(existingEntity("ext-other", "dave@acme.test")));

        // then
        assertThatThrownBy(() -> sut.create(PRINCIPAL, in)).isInstanceOf(ScimException.class)
                .satisfies(ex -> assertThat(((ScimException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(userRepository, never()).save(any());
    }

    @Test
    void create_onDuplicateUserName_carriesScimTypeUniquenessSoTheIdpCanReconcile() {

        // given
        when(mapper.getUserName(any())).thenReturn("taken@acme.test");
        when(userRepository.findByOrganizationIdAndEmail(PRINCIPAL.organizationId(), "taken@acme.test"))
                .thenReturn(Optional.of(UserEntity.builder().id(1L).build()));

        // when / then
        assertThatThrownBy(() -> sut.create(PRINCIPAL, ScimUser.builder().userName("taken@acme.test").build()))
                .isInstanceOf(ScimException.class).satisfies(ex -> {
                    assertThat(((ScimException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(((ScimException) ex).getScimType()).isEqualTo("uniqueness");
                });
    }

    /**
     * The directory door of a connection succession. A replacement directory issues a fresh {@code externalId} for
     * everybody, so without this the roster doubles before anybody has signed in.
     */
    @Test
    void create_underAnOpenSuccession_answersWithTheResourceTheMemberAlreadyWas() {

        // given
        ScimUser in = scimUserInput("ada@new.ch", "entra-1", "Ada");
        when(mapper.getUserName(in)).thenReturn("ada@new.ch");
        when(mapper.getExternalId(in)).thenReturn("entra-1");
        when(userRepository.findByOrganizationIdAndExternalId("tenant-1", "entra-1")).thenReturn(Optional.empty());
        UserEntity adopted = UserEntity.builder().id(7L).organizationId("tenant-1").scimId("scim-old")
                .externalId("entra-1").displayName("Ada").email("ada@new.ch").active(true).build();
        when(successions.adopt(eq("tenant-1"), any())).thenReturn(Optional.of(adopted));
        when(mapper.toScim(adopted)).thenReturn(scimUserOutput("scim-old"));

        // when
        ScimUser result = sut.create(PRINCIPAL, in);

        // then
        assertThat(result.id()).isEqualTo("scim-old");
        verify(userRepository, never()).save(any());
        verify(userRepository, never()).findByOrganizationIdAndEmail(anyString(), anyString());
    }

    @Test
    void create_underAnOpenSuccession_carriesTheWholePushOntoTheAdoptedRow() {

        // given
        ScimUser in = scimUserInput("ada@new.ch", "entra-1", "Ada");
        when(mapper.getUserName(in)).thenReturn("ada@new.ch");
        when(mapper.getExternalId(in)).thenReturn("entra-1");
        when(mapper.getDisplayName(in)).thenReturn("Ada");
        when(mapper.getBusinessUnit(in)).thenReturn("Engineering");
        when(mapper.getDepartment(in)).thenReturn("Platform");
        when(mapper.getTeam(in)).thenReturn("Identity");
        when(userRepository.findByOrganizationIdAndExternalId("tenant-1", "entra-1")).thenReturn(Optional.empty());
        ArgumentCaptor<SuccessionAdoption.DirectoryPush> captor = ArgumentCaptor
                .forClass(SuccessionAdoption.DirectoryPush.class);
        when(successions.adopt(anyString(), captor.capture())).thenReturn(Optional.empty());
        when(userRepository.findByOrganizationIdAndEmail("tenant-1", "ada@new.ch")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenReturn(UserEntity.builder().id(8L).build());

        // when
        sut.create(PRINCIPAL, in);

        // then
        assertThat(captor.getValue()).isEqualTo(
                SuccessionAdoption.DirectoryPush.builder().externalId("entra-1").email("ada@new.ch").displayName("Ada")
                        .businessUnit("Engineering").department("Platform").team("Identity").build());
    }

    /**
     * A directory pushing an {@code externalId} it already provisioned is not migrating anybody, so that conflict is
     * answered before adoption is asked anything. It also keeps adoption from ever stamping a second row's
     * {@code externalId} onto the row it adopts, which {@code uq_identity_user_organization_external} would refuse as a
     * constraint violation rather than as the {@code uniqueness} conflict the directory can act on.
     */
    @Test
    void create_onADuplicateExternalId_conflictsWithoutAskingAboutAdoption() {

        // given
        ScimUser in = scimUserInput("ada@new.ch", "entra-1", "Ada");
        when(mapper.getUserName(in)).thenReturn("ada@new.ch");
        when(mapper.getExternalId(in)).thenReturn("entra-1");
        when(userRepository.findByOrganizationIdAndExternalId("tenant-1", "entra-1"))
                .thenReturn(Optional.of(existingEntity("entra-1", "someone@new.ch")));

        // when / then
        assertThatThrownBy(() -> sut.create(PRINCIPAL, in)).isInstanceOf(ScimException.class);

        verify(successions, never()).adopt(anyString(), any());
    }

    // --- get ---

    @Test
    void get_unknown_throwsNotFound() {

        // given
        when(userRepository.findByOrganizationIdAndScimId("tenant-1", "ghost")).thenReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> sut.get(PRINCIPAL, "ghost")).isInstanceOf(ScimException.class)
                .satisfies(ex -> assertThat(((ScimException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void get_known_returnsMappedScimUser() {

        // given
        UserEntity entity = existingEntity("ext-x", "x@acme.test");
        when(userRepository.findByOrganizationIdAndScimId("tenant-1", "scim-x")).thenReturn(Optional.of(entity));
        ScimUser expected = scimUserOutput("scim-x");
        when(mapper.toScim(entity)).thenReturn(expected);

        // then
        assertThat(sut.get(PRINCIPAL, "scim-x")).isSameAs(expected);
    }

    @Test
    void get_crossOrgIsolation_throwsNotFound_whenScimIdBelongsToOtherOrg() {

        // given
        // scimId "scim-org-a" was provisioned for org 10 (org A); principal is for org 42 (org B)
        ScimPrincipal orgBPrincipal = new ScimPrincipal("tenant-1");
        when(userRepository.findByOrganizationIdAndScimId("tenant-1", "scim-org-a")).thenReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> sut.get(orgBPrincipal, "scim-org-a")).isInstanceOf(ScimException.class)
                .satisfies(ex -> assertThat(((ScimException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- list ---

    @Test
    void list_noFilter_returnsAllTenantUsers() {

        // given
        UserEntity e1 = existingEntity("ext-1", "a@acme.test");
        UserEntity e2 = existingEntity("ext-2", "b@acme.test");
        when(userRepository.findByOrganizationId(eq("tenant-1"), any(Pageable.class))).thenReturn(List.of(e1, e2));
        when(mapper.toScim(e1)).thenReturn(scimUserOutput("s1"));
        when(mapper.toScim(e2)).thenReturn(scimUserOutput("s2"));

        // when
        ScimListResponse<ScimUser> response = sut.list(PRINCIPAL, null);

        // then
        assertThat(response.totalResults()).isEqualTo(2);
        assertThat(response.startIndex()).isEqualTo(1);
        assertThat(response.resources()).hasSize(2);
    }

    @Test
    void list_userNameEqFilter_queriesByEmail() {

        // given
        UserEntity entity = existingEntity("ext-1", "alice@acme.test");
        when(userRepository.findByOrganizationIdAndEmail("tenant-1", "alice@acme.test"))
                .thenReturn(Optional.of(entity));
        when(mapper.toScim(entity)).thenReturn(scimUserOutput("scim-1"));

        // when
        ScimListResponse<ScimUser> response = sut.list(PRINCIPAL, "userName eq \"alice@acme.test\"");

        // then
        assertThat(response.totalResults()).isEqualTo(1);
        assertThat(response.resources()).hasSize(1);
        verify(userRepository, never()).findByOrganizationId(anyString(), any(Pageable.class));
    }

    @Test
    void list_externalIdEqFilter_queriesByExternalId() {

        // given
        UserEntity entity = existingEntity("ext-okta-1", "alice@acme.test");
        when(userRepository.findByOrganizationIdAndExternalId("tenant-1", "ext-okta-1"))
                .thenReturn(Optional.of(entity));
        when(mapper.toScim(entity)).thenReturn(scimUserOutput("scim-1"));

        // when
        ScimListResponse<ScimUser> response = sut.list(PRINCIPAL, "externalId eq \"ext-okta-1\"");

        // then
        assertThat(response.totalResults()).isEqualTo(1);
        assertThat(response.resources()).hasSize(1);
    }

    @Test
    void list_blankFilter_treatedAsNoFilter() {

        // given
        when(userRepository.findByOrganizationId(eq("tenant-1"), any(Pageable.class))).thenReturn(List.of());

        // when
        ScimListResponse<ScimUser> response = sut.list(PRINCIPAL, "  ");

        // then
        assertThat(response.totalResults()).isZero();
        verify(userRepository).findByOrganizationId(eq("tenant-1"), any(Pageable.class));
    }

    @Test
    void list_unsupportedFilter_returnsEmptyList() {

        // when
        ScimListResponse<ScimUser> response = sut.list(PRINCIPAL, "title eq \"Engineer\"");

        // then
        assertThat(response.totalResults()).isZero();
        assertThat(response.resources()).isEmpty();
        verify(userRepository, never()).findByOrganizationId(anyString(), any(Pageable.class));
    }

    @Test
    void list_malformedFilter_returnsEmptyList() {

        // when
        // Filter that does not match the EQ_FILTER regex → applyFilter returns List.of() immediately
        ScimListResponse<ScimUser> response = sut.list(PRINCIPAL, "not-a-valid-filter");

        // then
        assertThat(response.totalResults()).isZero();
        assertThat(response.resources()).isEmpty();
        verify(userRepository, never()).findByOrganizationId(anyString(), any(Pageable.class));
    }

    // --- patch ---

    @Test
    void patch_activeFalse_deactivates_whenSubPresent() {

        // given
        UserEntity existing = UserEntity.builder().id(10L).organizationId("tenant-1").scimId("scim-10")
                .externalId("ext-10").displayName("Eve").email("eve@acme.test").active(true).userId("sub-eve").build();
        when(userRepository.findByOrganizationIdAndScimId("tenant-1", "scim-10")).thenReturn(Optional.of(existing));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        UserEntity saved = existing.toBuilder().active(false).build();
        when(userRepository.save(captor.capture())).thenReturn(saved);
        when(mapper.toScim(saved)).thenReturn(scimUserOutput("scim-10"));

        ScimPatchRequest patchRequest = ScimPatchRequest
                .of(List.of(new ScimPatchOperation("replace", "active", false)));

        // when
        sut.patch(PRINCIPAL, "scim-10", patchRequest);

        // then — active=false must be persisted
        assertThat(captor.getValue().getActive()).isFalse();
    }

    @Test
    void patch_activeFalse_deactivates_whenSubAbsent() {

        // given
        UserEntity existing = UserEntity.builder().id(11L).organizationId("tenant-1").scimId("scim-11")
                .externalId("ext-11").displayName("Frank").email("frank@acme.test").active(true).userId(null).build();
        when(userRepository.findByOrganizationIdAndScimId("tenant-1", "scim-11")).thenReturn(Optional.of(existing));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        UserEntity saved = existing.toBuilder().active(false).build();
        when(userRepository.save(captor.capture())).thenReturn(saved);
        when(mapper.toScim(saved)).thenReturn(scimUserOutput("scim-11"));

        ScimPatchRequest patchRequest = ScimPatchRequest
                .of(List.of(new ScimPatchOperation("replace", "active", false)));

        // when
        sut.patch(PRINCIPAL, "scim-11", patchRequest);

        // then — active=false must be persisted even when userId is absent
        assertThat(captor.getValue().getActive()).isFalse();
    }

    @Test
    void patch_displayName_updatesDisplayName() {

        // given
        UserEntity existing = UserEntity.builder().id(12L).organizationId("tenant-1").scimId("scim-12")
                .externalId("ext-12").displayName("Old Name").email("g@acme.test").active(true).build();
        when(userRepository.findByOrganizationIdAndScimId("tenant-1", "scim-12")).thenReturn(Optional.of(existing));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        UserEntity saved = existing.toBuilder().displayName("New Name").build();
        when(userRepository.save(captor.capture())).thenReturn(saved);
        when(mapper.toScim(saved)).thenReturn(scimUserOutput("scim-12"));

        ScimPatchRequest patchRequest = ScimPatchRequest
                .of(List.of(new ScimPatchOperation("replace", "displayName", "New Name")));

        // when
        sut.patch(PRINCIPAL, "scim-12", patchRequest);

        // then
        assertThat(captor.getValue().getDisplayName()).isEqualTo("New Name");
    }

    // --- delete ---

    @Test
    void delete_deactivates_whenSubPresent() {

        // given
        UserEntity existing = UserEntity.builder().id(20L).organizationId("tenant-1").scimId("scim-20")
                .externalId("ext-20").displayName("Grace").email("grace@acme.test").active(true).userId("sub-grace")
                .build();
        when(userRepository.findByOrganizationIdAndScimId("tenant-1", "scim-20")).thenReturn(Optional.of(existing));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        UserEntity saved = existing.toBuilder().active(false).build();
        when(userRepository.save(captor.capture())).thenReturn(saved);

        // when
        sut.delete(PRINCIPAL, "scim-20");

        // then — active=false must be persisted
        assertThat(captor.getValue().getActive()).isFalse();
    }

    @Test
    void delete_deactivates_whenSubAbsent() {

        // given
        UserEntity existing = UserEntity.builder().id(21L).organizationId("tenant-1").scimId("scim-21")
                .externalId("ext-21").displayName("Hank").email("hank@acme.test").active(true).userId(null).build();
        when(userRepository.findByOrganizationIdAndScimId("tenant-1", "scim-21")).thenReturn(Optional.of(existing));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        UserEntity saved = existing.toBuilder().active(false).build();
        when(userRepository.save(captor.capture())).thenReturn(saved);

        // when
        sut.delete(PRINCIPAL, "scim-21");

        // then — active=false must be persisted even when userId is absent
        assertThat(captor.getValue().getActive()).isFalse();
    }

    @Test
    void delete_unknown_throwsNotFound() {

        // given
        when(userRepository.findByOrganizationIdAndScimId("tenant-1", "ghost")).thenReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> sut.delete(PRINCIPAL, "ghost")).isInstanceOf(ScimException.class)
                .satisfies(ex -> assertThat(((ScimException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void patch_unknown_throwsNotFound() {

        // given
        when(userRepository.findByOrganizationIdAndScimId("tenant-1", "ghost")).thenReturn(Optional.empty());

        ScimPatchRequest patchRequest = ScimPatchRequest.of(List.of());

        // then
        assertThatThrownBy(() -> sut.patch(PRINCIPAL, "ghost", patchRequest)).isInstanceOf(ScimException.class)
                .satisfies(ex -> assertThat(((ScimException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void patch_nonReplaceOp_isSkipped() {

        // given
        UserEntity existing = UserEntity.builder().id(13L).organizationId("tenant-1").scimId("scim-13").active(true)
                .build();
        when(userRepository.findByOrganizationIdAndScimId("tenant-1", "scim-13")).thenReturn(Optional.of(existing));
        UserEntity saved = existing.toBuilder().build();
        when(userRepository.save(any())).thenReturn(saved);
        when(mapper.toScim(saved)).thenReturn(scimUserOutput("scim-13"));

        // "add" is not "replace" → continue → no field updates
        ScimPatchRequest patchRequest = ScimPatchRequest.of(List.of(new ScimPatchOperation("add", "active", true)));

        // when
        sut.patch(PRINCIPAL, "scim-13", patchRequest);

        // then — save is still called (no-op patch), entity state unchanged
        verify(userRepository).save(any());
    }

    @Test
    void patch_unknownPath_isIgnored() {

        // given
        UserEntity existing = UserEntity.builder().id(14L).organizationId("tenant-1").scimId("scim-14").active(true)
                .build();
        when(userRepository.findByOrganizationIdAndScimId("tenant-1", "scim-14")).thenReturn(Optional.of(existing));
        UserEntity saved = existing.toBuilder().build();
        when(userRepository.save(any())).thenReturn(saved);
        when(mapper.toScim(saved)).thenReturn(scimUserOutput("scim-14"));

        // "email" is neither "active" nor "displayName" → silently ignored
        ScimPatchRequest patchRequest = ScimPatchRequest
                .of(List.of(new ScimPatchOperation("replace", "email", "new@acme.test")));

        // when
        sut.patch(PRINCIPAL, "scim-14", patchRequest);

        // then — save is still called, entity state unchanged
        verify(userRepository).save(any());
    }

    @Test
    void patch_setActiveTrueWhenWasActive_noChange() {

        // given
        UserEntity existing = UserEntity.builder().id(15L).organizationId("tenant-1").scimId("scim-15").active(true)
                .userId("sub-15").build();
        when(userRepository.findByOrganizationIdAndScimId("tenant-1", "scim-15")).thenReturn(Optional.of(existing));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        UserEntity saved = existing.toBuilder().build();
        when(userRepository.save(captor.capture())).thenReturn(saved);
        when(mapper.toScim(saved)).thenReturn(scimUserOutput("scim-15"));

        // active stays true → no state change
        ScimPatchRequest patchRequest = ScimPatchRequest.of(List.of(new ScimPatchOperation("replace", "active", true)));

        // when
        sut.patch(PRINCIPAL, "scim-15", patchRequest);

        // then — active remains true
        assertThat(captor.getValue().getActive()).isTrue();
    }

    @Test
    void patch_displayName_withNullValue_setsNullDisplayName() {

        // given
        UserEntity existing = UserEntity.builder().id(16L).organizationId("tenant-1").scimId("scim-16").active(true)
                .displayName("Old").build();
        when(userRepository.findByOrganizationIdAndScimId("tenant-1", "scim-16")).thenReturn(Optional.of(existing));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        UserEntity saved = existing.toBuilder().displayName(null).build();
        when(userRepository.save(captor.capture())).thenReturn(saved);
        when(mapper.toScim(saved)).thenReturn(scimUserOutput("scim-16"));

        // null value for displayName path → builder.displayName(null)
        ScimPatchRequest patchRequest = ScimPatchRequest
                .of(List.of(new ScimPatchOperation("replace", "displayName", null)));

        // when
        sut.patch(PRINCIPAL, "scim-16", patchRequest);

        // then
        assertThat(captor.getValue().getDisplayName()).isNull();
    }

    @Test
    void patch_activeFalse_withStringValue_deactivates() {

        // given
        UserEntity existing = UserEntity.builder().id(17L).organizationId("tenant-1").scimId("scim-17").active(true)
                .userId("sub-17").build();
        when(userRepository.findByOrganizationIdAndScimId("tenant-1", "scim-17")).thenReturn(Optional.of(existing));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        UserEntity saved = existing.toBuilder().active(false).build();
        when(userRepository.save(captor.capture())).thenReturn(saved);
        when(mapper.toScim(saved)).thenReturn(scimUserOutput("scim-17"));

        // String "false" triggers toBoolean non-Boolean path: Boolean.parseBoolean("false") = false
        ScimPatchRequest patchRequest = ScimPatchRequest
                .of(List.of(new ScimPatchOperation("replace", "active", "false")));

        // when
        sut.patch(PRINCIPAL, "scim-17", patchRequest);

        // then — active=false persisted despite String value (toBoolean coercion)
        assertThat(captor.getValue().getActive()).isFalse();
    }

    @Test
    void patch_activeNull_noDeactivation() {

        // given
        // User WITH a userId so the test is meaningful: null active → state unchanged → active stays true.
        UserEntity existing = UserEntity.builder().id(18L).organizationId("tenant-1").scimId("scim-18").active(true)
                .userId("sub-18").build();
        when(userRepository.findByOrganizationIdAndScimId("tenant-1", "scim-18")).thenReturn(Optional.of(existing));
        // Null active value → builder is never told to set active → saved entity stays active=true
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        UserEntity saved = existing.toBuilder().build();
        when(userRepository.save(captor.capture())).thenReturn(saved);
        when(mapper.toScim(saved)).thenReturn(scimUserOutput("scim-18"));

        ScimPatchRequest patchRequest = ScimPatchRequest.of(List.of(new ScimPatchOperation("replace", "active", null)));

        // when
        sut.patch(PRINCIPAL, "scim-18", patchRequest);

        // then — null active must not change the flag (RFC 7644 §3.5.2)
        assertThat(captor.getValue().getActive()).isTrue();
    }

    @Test
    void patch_activeFalse_reconciled_deactivates() {

        // given
        UserEntity existing = UserEntity.builder().id(19L).organizationId("tenant-1").scimId("scim-19").active(true)
                .userId("sub-19").build();
        when(userRepository.findByOrganizationIdAndScimId("tenant-1", "scim-19")).thenReturn(Optional.of(existing));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        UserEntity saved = existing.toBuilder().active(false).build();
        when(userRepository.save(captor.capture())).thenReturn(saved);
        when(mapper.toScim(saved)).thenReturn(scimUserOutput("scim-19"));

        // when
        sut.patch(PRINCIPAL, "scim-19",
                ScimPatchRequest.of(List.of(new ScimPatchOperation("replace", "active", false))));

        // then
        assertThat(captor.getValue().getActive()).isFalse();
    }

    @Test
    void patch_activeTrue_reactivates() {

        // given
        // User was inactive (active=false) and has a known sub → active:true must persist active=true
        UserEntity existing = UserEntity.builder().id(20L).organizationId("tenant-1").scimId("scim-20").active(false)
                .userId("sub-20").build();
        when(userRepository.findByOrganizationIdAndScimId("tenant-1", "scim-20")).thenReturn(Optional.of(existing));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        UserEntity saved = existing.toBuilder().active(true).build();
        when(userRepository.save(captor.capture())).thenReturn(saved);
        when(mapper.toScim(saved)).thenReturn(scimUserOutput("scim-20"));

        // when
        sut.patch(PRINCIPAL, "scim-20",
                ScimPatchRequest.of(List.of(new ScimPatchOperation("replace", "active", true))));

        // then — active=true persisted
        assertThat(captor.getValue().getActive()).isTrue();
    }

    // --- replace ---

    @Test
    void replace_unknown_throwsNotFound() {

        // given
        when(userRepository.findByOrganizationIdAndScimId("tenant-1", "ghost")).thenReturn(Optional.empty());

        ScimUser in = scimUserInput("x@acme.test", "ext-x", "X");

        // then
        assertThatThrownBy(() -> sut.replace(PRINCIPAL, "ghost", in)).isInstanceOf(ScimException.class)
                .satisfies(ex -> assertThat(((ScimException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void replace_keepActive_noDeactivation() {

        // given
        UserEntity existing = UserEntity.builder().id(31L).organizationId("tenant-1").scimId("scim-31").active(true)
                .userId("sub-31").build();
        when(userRepository.findByOrganizationIdAndScimId("tenant-1", "scim-31")).thenReturn(Optional.of(existing));

        ScimUser replacement = scimUserInput("k@acme.test", "ext-31", "Kay");
        when(mapper.getDisplayName(replacement)).thenReturn("Kay");
        when(mapper.getUserName(replacement)).thenReturn("k@acme.test");
        when(mapper.getExternalId(replacement)).thenReturn("ext-31");
        when(mapper.getActive(replacement)).thenReturn(true); // still active

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        UserEntity saved = existing.toBuilder().displayName("Kay").build();
        when(userRepository.save(captor.capture())).thenReturn(saved);
        when(mapper.toScim(saved)).thenReturn(scimUserOutput("scim-31"));

        // when
        sut.replace(PRINCIPAL, "scim-31", replacement);

        // then — active stays true
        assertThat(captor.getValue().getActive()).isTrue();
    }

    @Test
    void replace_fullUpdate_deactivatesWhenActiveFalse() {

        // given
        UserEntity existing = UserEntity.builder().id(30L).organizationId("tenant-1").scimId("scim-30")
                .externalId("ext-30").displayName("Ivy").email("ivy@acme.test").active(true).userId("sub-ivy").build();
        when(userRepository.findByOrganizationIdAndScimId("tenant-1", "scim-30")).thenReturn(Optional.of(existing));

        ScimUser replacement = scimUserInput("ivy-new@acme.test", "ext-30-new", "Ivy New");
        when(mapper.getDisplayName(replacement)).thenReturn("Ivy New");
        when(mapper.getUserName(replacement)).thenReturn("ivy-new@acme.test");
        when(mapper.getExternalId(replacement)).thenReturn("ext-30-new");
        when(mapper.getActive(replacement)).thenReturn(false);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        UserEntity saved = existing.toBuilder().displayName("Ivy New").email("ivy-new@acme.test")
                .externalId("ext-30-new").active(false).build();
        when(userRepository.save(captor.capture())).thenReturn(saved);
        when(mapper.toScim(saved)).thenReturn(scimUserOutput("scim-30"));

        // when
        sut.replace(PRINCIPAL, "scim-30", replacement);

        // then
        UserEntity captured = captor.getValue();
        assertThat(captured.getDisplayName()).isEqualTo("Ivy New");
        assertThat(captured.getEmail()).isEqualTo("ivy-new@acme.test");
        assertThat(captured.getActive()).isFalse();
    }

    @Test
    void create_persistsOrgUnitLabelsFromEnterpriseExtension() {

        // given
        ScimUser in = scimUserInput("alice@acme.test", "ext-ou", "Alice");
        when(mapper.getUserName(in)).thenReturn("alice@acme.test");
        when(mapper.getExternalId(in)).thenReturn("ext-ou");
        when(mapper.getDisplayName(in)).thenReturn("Alice");
        when(mapper.getBusinessUnit(in)).thenReturn("Acme Group");
        when(mapper.getDepartment(in)).thenReturn("Platform");
        when(mapper.getTeam(in)).thenReturn("Payments");
        when(userRepository.findByOrganizationIdAndExternalId("tenant-1", "ext-ou")).thenReturn(Optional.empty());
        when(userRepository.findByOrganizationIdAndEmail("tenant-1", "alice@acme.test")).thenReturn(Optional.empty());

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        when(userRepository.save(captor.capture()))
                .thenReturn(UserEntity.builder().id(1L).organizationId("tenant-1").build());
        when(mapper.toScim(any())).thenReturn(scimUserOutput("any-uuid"));

        // when
        sut.create(PRINCIPAL, in);

        // then
        UserEntity captured = captor.getValue();
        assertThat(captured.getBusinessUnit()).isEqualTo("Acme Group");
        assertThat(captured.getDepartment()).isEqualTo("Platform");
        assertThat(captured.getTeam()).isEqualTo("Payments");
    }

    @Test
    void replace_clearsOrgUnitLabelsWhenExtensionAbsent() {

        // given — existing user has a unit; the PUT body has none → replace semantics clear it
        UserEntity existing = UserEntity.builder().id(7L).organizationId("tenant-1").scimId("scim-7")
                .businessUnit("Acme Group").department("Platform").team("Payments").build();
        when(userRepository.findByOrganizationIdAndScimId("tenant-1", "scim-7")).thenReturn(Optional.of(existing));

        ScimUser in = scimUserInput("alice@acme.test", "ext-ou", "Alice");
        when(mapper.getActive(in)).thenReturn(true);
        when(mapper.getDisplayName(in)).thenReturn("Alice");
        when(mapper.getUserName(in)).thenReturn("alice@acme.test");
        when(mapper.getExternalId(in)).thenReturn("ext-ou");
        when(mapper.getBusinessUnit(in)).thenReturn(null);
        when(mapper.getDepartment(in)).thenReturn(null);
        when(mapper.getTeam(in)).thenReturn(null);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toScim(any())).thenReturn(scimUserOutput("scim-7"));

        // when
        sut.replace(PRINCIPAL, "scim-7", in);

        // then
        UserEntity captured = captor.getValue();
        assertThat(captured.getBusinessUnit()).isNull();
        assertThat(captured.getDepartment()).isNull();
        assertThat(captured.getTeam()).isNull();
    }

    @Test
    void patch_replaceOnEnterpriseDepartmentPath_updatesTeamColumn() {

        // given
        UserEntity existing = UserEntity.builder().id(8L).organizationId("tenant-1").scimId("scim-8").build();
        when(userRepository.findByOrganizationIdAndScimId("tenant-1", "scim-8")).thenReturn(Optional.of(existing));

        ScimPatchRequest patch = new ScimPatchRequest(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"),
                List.of(new ScimPatchOperation("replace",
                        "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:department", "Checkout")));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toScim(any())).thenReturn(scimUserOutput("scim-8"));

        // when
        sut.patch(PRINCIPAL, "scim-8", patch);

        // then — SCIM department maps to Axonia team
        assertThat(captor.getValue().getTeam()).isEqualTo("Checkout");
    }

    @Test
    void patch_replaceOnEnterpriseOrganizationPath_updatesBusinessUnitColumn() {

        // given
        UserEntity existing = UserEntity.builder().id(9L).organizationId("tenant-1").scimId("scim-9").build();
        when(userRepository.findByOrganizationIdAndScimId("tenant-1", "scim-9")).thenReturn(Optional.of(existing));

        ScimPatchRequest patch = new ScimPatchRequest(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"),
                List.of(new ScimPatchOperation("replace",
                        "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:organization", "Acme Group")));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toScim(any())).thenReturn(scimUserOutput("scim-9"));

        // when
        sut.patch(PRINCIPAL, "scim-9", patch);

        // then — SCIM organization maps to Axonia business_unit
        assertThat(captor.getValue().getBusinessUnit()).isEqualTo("Acme Group");
    }

    @Test
    void patch_replaceOnEnterpriseDivisionPath_updatesDepartmentColumn() {

        // given
        UserEntity existing = UserEntity.builder().id(10L).organizationId("tenant-1").scimId("scim-10").build();
        when(userRepository.findByOrganizationIdAndScimId("tenant-1", "scim-10")).thenReturn(Optional.of(existing));

        ScimPatchRequest patch = new ScimPatchRequest(List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"),
                List.of(new ScimPatchOperation("replace",
                        "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:division", "Platform")));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toScim(any())).thenReturn(scimUserOutput("scim-10"));

        // when
        sut.patch(PRINCIPAL, "scim-10", patch);

        // then — SCIM division maps to Axonia department
        assertThat(captor.getValue().getDepartment()).isEqualTo("Platform");
    }

    // --- helpers ---

    private ScimUser scimUserInput(String email, String externalId, String displayName) {

        return ScimUser.builder().externalId(externalId).userName(email).displayName(displayName)
                .emails(List.of(new ScimEmail(email, "work", true))).active(true).build();
    }

    private ScimUser scimUserOutput(String id) {

        return ScimUser.builder().id(id).externalId("ext").userName("user@acme.test").displayName("User")
                .emails(List.of(new ScimEmail("user@acme.test", "work", true))).active(true).build();
    }

    private UserEntity existingEntity(String externalId, String email) {

        return UserEntity.builder().id(99L).organizationId("tenant-1").scimId("scim-existing").externalId(externalId)
                .displayName("Existing").email(email).active(true).build();
    }
}
