package dev.axonia.identity.internal;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.web.client.HttpClientErrorException;

import dev.axonia.identity.IdpProvider;
import dev.axonia.identity.OrganizationIdp;
import dev.axonia.identity.OrganizationIdpResolver;
import dev.axonia.identity.mother.UserEntityMother;
import dev.axonia.media.MediaStorage;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class AvatarSyncServiceTest {

    private static final String ORG = "acme";
    private static final String TENANT = "tenant-acme";
    private static final String USER = "alice-sub";
    private static final String EXTERNAL_ID = "entra-1";
    private static final String CLAIM_URL = "https://lh3.googleusercontent.com/a/abc=s96-c";
    private static final byte[] UNCHANGED_BYTES = {4, 5, 6};
    private static final String UNCHANGED_CONTENT_HASH = AvatarSyncService.contentHash(UNCHANGED_BYTES);
    private static final Instant NOW = Instant.parse("2026-07-07T12:00:00Z");
    private static final Instant STALE = NOW.minusSeconds(60 * 60 * 25);
    private static final Instant FRESH = NOW.minusSeconds(60 * 60);

    @Mock
    private UserRepository userRepository;

    @Mock
    private EntraPhotoSource photoSource;

    @Mock
    private ClaimUrlPhotoSource claimUrlPhotoSource;

    @Mock
    private MediaStorage mediaStorage;

    @Mock
    private AvatarSyncExecutor executor;

    @Mock
    private Clock clock;

    @Mock
    private OrganizationIdpResolver resolver;

    private AvatarSyncService sut;

    @BeforeEach
    void setUp() {

        lenient().when(clock.instant()).thenReturn(NOW);
        // Run submitted tasks inline so assertions see the sync outcome synchronously.
        lenient().when(executor.trySubmit(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return true;
        });
        lenient().when(resolver.resolve(ORG))
                .thenReturn(Optional.of(new OrganizationIdp(IdpProvider.ENTRA, TENANT, true)));
        sut = new AvatarSyncService(userRepository, photoSource, claimUrlPhotoSource, mediaStorage, resolver, executor,
                clock);
    }

    private UserEntity.UserEntityBuilder entraUser() {

        return UserEntityMother.any().organizationId(ORG).userId(USER).externalId(EXTERNAL_ID).avatarMediaKey(null)
                .avatarEtag(null).avatarSyncedAt(null);
    }

    private UserEntity userWith(String externalId, String avatarMediaKey, String avatarEtag) {

        return UserEntityMother.any().organizationId(ORG).userId(USER).externalId(externalId)
                .avatarMediaKey(avatarMediaKey).avatarEtag(avatarEtag).avatarSyncedAt(null).build();
    }

    /** A member who wears their own upload; no mirror has ever been stored underneath it yet. */
    private void seedUserWithOverride(String organizationId, String userId, String overrideKey) {

        when(userRepository.findByOrganizationIdAndUserId(organizationId, userId))
                .thenReturn(Optional.of(entraUser().organizationId(organizationId).userId(userId).avatarSyncedAt(STALE)
                        .avatarOverrideKey(overrideKey).build()));
    }

    /** A member who wears their own upload, with a stale mirror already sitting underneath it. */
    private void seedUserWithMirrorAndOverride(String organizationId, String userId, String mirroredKey,
            String overrideKey) {

        when(userRepository.findByOrganizationIdAndUserId(organizationId, userId)).thenReturn(
                Optional.of(entraUser().organizationId(organizationId).userId(userId).avatarMediaKey(mirroredKey)
                        .avatarEtag("W/\"etag-1\"").avatarSyncedAt(STALE).avatarOverrideKey(overrideKey).build()));
    }

    @Test
    void maybeSync_skipsOrganizationWhoseProviderHasNoDirectoryToRead() {

        // given - a connection exists, but its provider federates no Microsoft directory: no tenant to read photos from
        when(userRepository.findByOrganizationIdAndUserId("kc-org", USER))
                .thenReturn(Optional.of(entraUser().organizationId("kc-org").build()));
        when(resolver.resolve("kc-org"))
                .thenReturn(Optional.of(new OrganizationIdp(IdpProvider.GENERIC_OIDC, null, false)));

        // when - no claim url either
        sut.maybeSync("kc-org", USER, null);

        // then
        verifyNoInteractions(photoSource, mediaStorage, executor);
    }

    @Test
    void maybeSync_entraOrganizationWithoutTenant_doesNotReachGraph() {

        // given - a migrated connection left on provider ENTRA with no aad_tenant_id: there is no tenant to read from
        when(userRepository.findByOrganizationIdAndUserId("no-tenant-org", USER))
                .thenReturn(Optional.of(entraUser().organizationId("no-tenant-org").build()));
        when(resolver.resolve("no-tenant-org"))
                .thenReturn(Optional.of(new OrganizationIdp(IdpProvider.ENTRA, null, false)));

        // when - no claim url either
        sut.maybeSync("no-tenant-org", USER, null);

        // then
        verifyNoInteractions(photoSource, mediaStorage, executor);
    }

    @Test
    void maybeSync_entraOrganizationThatHasNotProvedItOwnsTheTenant_doesNotReachGraph() {

        // given - a well-formed tenant that passes every check on the administrator's own typing, and that nobody has
        // shown this Organization owns. Photos are people, so reading them out of it is worse than listing its groups.
        when(userRepository.findByOrganizationIdAndUserId("unproved-org", USER))
                .thenReturn(Optional.of(entraUser().organizationId("unproved-org").build()));
        when(resolver.resolve("unproved-org"))
                .thenReturn(Optional.of(new OrganizationIdp(IdpProvider.ENTRA, "tenant-somebody-else", false)));

        // when - no claim url either, so nothing at all should happen
        sut.maybeSync("unproved-org", USER, null);

        // then
        verifyNoInteractions(photoSource, mediaStorage, executor);
    }

    @Test
    void maybeSync_twoEntraOrganizations_readsEachPhotoFromItsOwnTenant() {

        // given - two Entra customers, each with its own tenant on its own SSO connection
        when(resolver.resolve("globex"))
                .thenReturn(Optional.of(new OrganizationIdp(IdpProvider.ENTRA, "tenant-globex", true)));
        when(userRepository.findByOrganizationIdAndUserId(ORG, USER))
                .thenReturn(Optional.of(entraUser().avatarSyncedAt(STALE).build()));
        when(userRepository.findByOrganizationIdAndUserId("globex", USER))
                .thenReturn(Optional.of(UserEntityMother.any().organizationId("globex").userId(USER)
                        .externalId("entra-2").avatarMediaKey(null).avatarEtag(null).avatarSyncedAt(STALE).build()));
        when(photoSource.photoMetadata(anyString(), anyString())).thenReturn(Optional.empty());

        // when
        sut.maybeSync(ORG, USER, null);
        sut.maybeSync("globex", USER, null);

        // then - each Graph read targeted its own Organization's tenant, never the other's
        verify(photoSource).photoMetadata("entra-1", TENANT);
        verify(photoSource).photoMetadata("entra-2", "tenant-globex");
        verify(photoSource, never()).photoMetadata("entra-1", "tenant-globex");
        verify(photoSource, never()).photoMetadata("entra-2", TENANT);
    }

    @Test
    void maybeSync_skipsUserWithoutExternalId() {

        // given
        when(userRepository.findByOrganizationIdAndUserId(ORG, USER))
                .thenReturn(Optional.of(entraUser().externalId(null).build()));

        // when
        sut.maybeSync(ORG, USER, null);

        // then
        verifyNoInteractions(photoSource, executor);
    }

    @Test
    void maybeSync_skipsFreshlySyncedUser() {

        // given
        when(userRepository.findByOrganizationIdAndUserId(ORG, USER))
                .thenReturn(Optional.of(entraUser().avatarSyncedAt(FRESH).build()));

        // when - a claim url is supplied too, to prove the freshness gate wins over either source
        sut.maybeSync(ORG, USER, CLAIM_URL);

        // then
        verifyNoInteractions(photoSource, claimUrlPhotoSource, executor);
    }

    @Test
    void maybeSync_staleUser_changedEtag_storesPhotoAndUpdatesRow() {

        // given
        when(userRepository.findByOrganizationIdAndUserId(ORG, USER))
                .thenReturn(Optional.of(entraUser().avatarSyncedAt(STALE).build()));
        when(photoSource.photoMetadata("entra-1", TENANT))
                .thenReturn(Optional.of(new EntraPhotoSource.PhotoMetadata("W/\"etag-2\"")));
        when(photoSource.fetchPhoto("entra-1", TENANT))
                .thenReturn(Optional.of(new AvatarPhoto(new byte[]{1, 2}, "image/jpeg")));

        // when
        sut.maybeSync(ORG, USER, null);

        // then - stored under a fresh random key and the row carries key/etag/stamp
        var keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(mediaStorage).store(keyCaptor.capture(), any(InputStream.class), eq("image/jpeg"), eq(2L));
        assertThat(keyCaptor.getValue()).startsWith("avatars/acme/").endsWith(".jpg");

        var updateCaptor = ArgumentCaptor.forClass(AvatarSyncUpdate.class);
        verify(userRepository).updateSyncedAvatar(updateCaptor.capture());
        assertThat(updateCaptor.getValue().organizationId()).isEqualTo(ORG);
        assertThat(updateCaptor.getValue().userId()).isEqualTo(USER);
        assertThat(updateCaptor.getValue().avatarMediaKey()).isEqualTo(keyCaptor.getValue());
        assertThat(updateCaptor.getValue().avatarEtag()).isEqualTo("W/\"etag-2\"");
        assertThat(updateCaptor.getValue().avatarSyncedAt()).isEqualTo(NOW);
    }

    @Test
    void maybeSync_refreshedPhoto_deletesThePreviousBlob() {

        // given - an owned avatar exists and the photo changed
        when(userRepository.findByOrganizationIdAndUserId(ORG, USER)).thenReturn(Optional.of(entraUser()
                .avatarMediaKey("avatars/acme/old.jpg").avatarEtag("W/\"etag-1\"").avatarSyncedAt(STALE).build()));
        when(photoSource.photoMetadata("entra-1", TENANT))
                .thenReturn(Optional.of(new EntraPhotoSource.PhotoMetadata("W/\"etag-2\"")));
        when(photoSource.fetchPhoto("entra-1", TENANT))
                .thenReturn(Optional.of(new AvatarPhoto(new byte[]{1}, "image/png")));

        // when
        sut.maybeSync(ORG, USER, null);

        // then
        verify(userRepository).updateSyncedAvatar(any(AvatarSyncUpdate.class));
        verify(mediaStorage).delete("avatars/acme/old.jpg");
    }

    @Test
    void maybeSync_unchangedEtag_onlyStampsTheCheck() {

        // given
        when(userRepository.findByOrganizationIdAndUserId(ORG, USER)).thenReturn(Optional.of(entraUser()
                .avatarMediaKey("avatars/acme/old.jpg").avatarEtag("W/\"etag-1\"").avatarSyncedAt(STALE).build()));
        when(photoSource.photoMetadata("entra-1", TENANT))
                .thenReturn(Optional.of(new EntraPhotoSource.PhotoMetadata("W/\"etag-1\"")));

        // when
        sut.maybeSync(ORG, USER, null);

        // then
        verify(userRepository).touchAvatarSyncedAt(ORG, USER, NOW);
        verify(photoSource, never()).fetchPhoto(anyString(), anyString());
        verifyNoInteractions(mediaStorage);
    }

    @Test
    void aSyncRefreshesTheMirrorAndLeavesTheOverrideAlone() {

        // given - the member wears their own upload; the mirror underneath is stale and about to refresh
        seedUserWithOverride(ORG, USER, "avatars/" + ORG + "/own.png");
        when(photoSource.photoMetadata(EXTERNAL_ID, TENANT))
                .thenReturn(Optional.of(new EntraPhotoSource.PhotoMetadata("W/\"etag-2\"")));
        when(photoSource.fetchPhoto(EXTERNAL_ID, TENANT))
                .thenReturn(Optional.of(new AvatarPhoto("fresh-bytes".getBytes(UTF_8), "image/jpeg")));

        // when
        sut.maybeSync(ORG, USER, null);

        // then - the mirror moved to the fresh photo; the override was never read, written, or deleted
        var keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(mediaStorage).store(keyCaptor.capture(), any(InputStream.class), eq("image/jpeg"), anyLong());
        var updateCaptor = ArgumentCaptor.forClass(AvatarSyncUpdate.class);
        verify(userRepository).updateSyncedAvatar(updateCaptor.capture());
        assertThat(updateCaptor.getValue().avatarMediaKey()).isEqualTo(keyCaptor.getValue());
        verify(userRepository, never()).updateAvatarOverride(any(), any());
        verify(userRepository, never()).clearAvatarOverride(any(), any(), any());
        verify(mediaStorage, never()).delete("avatars/" + ORG + "/own.png");
    }

    @Test
    void aSourcePhotoThatVanishesClearsTheMirrorAndLeavesTheOverrideAlone() {

        // given - a mirrored avatar and an override both exist; the directory no longer reports a photo
        seedUserWithMirrorAndOverride(ORG, USER, "avatars/" + ORG + "/mirrored.jpg", "avatars/" + ORG + "/own.png");
        when(photoSource.photoMetadata(EXTERNAL_ID, TENANT)).thenReturn(Optional.empty());

        // when
        sut.maybeSync(ORG, USER, null);

        // then - only the mirror is cleared and its blob deleted; the override is untouched
        verify(userRepository).clearSyncedAvatar(ORG, USER, NOW);
        verify(userRepository, never()).updateAvatarOverride(any(), any());
        verify(userRepository, never()).clearAvatarOverride(any(), any(), any());
        verify(mediaStorage).delete("avatars/" + ORG + "/mirrored.jpg");
        verify(mediaStorage, never()).delete("avatars/" + ORG + "/own.png");
    }

    @Test
    void maybeSync_noPhotoInGraph_withOwnedAvatar_clearsAndDeletesBlob() {

        // given
        when(userRepository.findByOrganizationIdAndUserId(ORG, USER)).thenReturn(Optional.of(entraUser()
                .avatarMediaKey("avatars/acme/old.jpg").avatarEtag("W/\"etag-1\"").avatarSyncedAt(STALE).build()));
        when(photoSource.photoMetadata("entra-1", TENANT)).thenReturn(Optional.empty());

        // when
        sut.maybeSync(ORG, USER, null);

        // then
        verify(userRepository).clearSyncedAvatar(ORG, USER, NOW);
        verify(mediaStorage).delete("avatars/acme/old.jpg");
    }

    @Test
    void maybeSync_noPhotoInGraph_withoutAvatar_onlyStampsTheCheck() {

        // given
        when(userRepository.findByOrganizationIdAndUserId(ORG, USER))
                .thenReturn(Optional.of(entraUser().avatarSyncedAt(STALE).build()));
        when(photoSource.photoMetadata("entra-1", TENANT)).thenReturn(Optional.empty());

        // when
        sut.maybeSync(ORG, USER, null);

        // then
        verify(userRepository).touchAvatarSyncedAt(ORG, USER, NOW);
        verifyNoInteractions(mediaStorage);
    }

    @Test
    void maybeSync_photoVanishesBetweenMetadataAndBytes_treatedAsNoPhoto() {

        // given
        when(userRepository.findByOrganizationIdAndUserId(ORG, USER))
                .thenReturn(Optional.of(entraUser().avatarSyncedAt(STALE).build()));
        when(photoSource.photoMetadata("entra-1", TENANT))
                .thenReturn(Optional.of(new EntraPhotoSource.PhotoMetadata("W/\"etag-2\"")));
        when(photoSource.fetchPhoto("entra-1", TENANT)).thenReturn(Optional.empty());

        // when
        sut.maybeSync(ORG, USER, null);

        // then
        verify(userRepository).touchAvatarSyncedAt(ORG, USER, NOW);
        verifyNoInteractions(mediaStorage);
    }

    @Test
    void maybeSync_graphRefusesWithForbidden_warnsAboutConsentAndLeavesSyncedAtUnstamped() {

        // given - the app registration lacks ProfilePhoto.Read.All (or consent was never granted)
        when(userRepository.findByOrganizationIdAndUserId(ORG, USER))
                .thenReturn(Optional.of(entraUser().avatarSyncedAt(STALE).build()));
        when(photoSource.photoMetadata("entra-1", TENANT)).thenThrow(HttpClientErrorException
                .create(org.springframework.http.HttpStatus.FORBIDDEN, "Forbidden", null, null, null));

        // when - must not throw
        sut.maybeSync(ORG, USER, null);

        // then - no stamp, so a later window retries once consent is granted
        verify(userRepository).findByOrganizationIdAndUserId(ORG, USER);
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(mediaStorage);
    }

    @Test
    void maybeSync_graphFailure_leavesSyncedAtUnstamped() {

        // given - Graph down: warn and leave avatar_synced_at alone so a later window retries
        when(userRepository.findByOrganizationIdAndUserId(ORG, USER))
                .thenReturn(Optional.of(entraUser().avatarSyncedAt(STALE).build()));
        when(photoSource.photoMetadata("entra-1", TENANT)).thenThrow(new IllegalStateException("Graph down"));

        // when - must not throw
        sut.maybeSync(ORG, USER, null);

        // then
        verify(userRepository).findByOrganizationIdAndUserId(ORG, USER);
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(mediaStorage);
    }

    @Test
    void maybeSync_blobDeleteFailureAfterRefresh_isTolerated() {

        // given - the refresh succeeds but deleting the previous blob fails (orphan, not an error)
        when(userRepository.findByOrganizationIdAndUserId(ORG, USER)).thenReturn(Optional.of(entraUser()
                .avatarMediaKey("avatars/acme/old.jpg").avatarEtag("W/\"etag-1\"").avatarSyncedAt(STALE).build()));
        when(photoSource.photoMetadata("entra-1", TENANT))
                .thenReturn(Optional.of(new EntraPhotoSource.PhotoMetadata("W/\"etag-2\"")));
        when(photoSource.fetchPhoto("entra-1", TENANT))
                .thenReturn(Optional.of(new AvatarPhoto(new byte[]{1}, "image/jpeg")));
        doThrow(new IllegalStateException("object store down")).when(mediaStorage).delete("avatars/acme/old.jpg");

        // when - must not throw
        sut.maybeSync(ORG, USER, null);

        // then - the row update happened despite the failed delete
        verify(userRepository).updateSyncedAvatar(any(AvatarSyncUpdate.class));
    }

    @Test
    void maybeSync_alreadyInFlight_isNotSubmittedTwice() {

        // given - the first submit is held (not run), so the in-flight marker stays set. doAnswer (not
        // when(...).thenAnswer) so re-stubbing does not fire the setUp inline-runner with a null Runnable.
        var held = new java.util.concurrent.atomic.AtomicReference<Runnable>();
        doAnswer(invocation -> {
            held.set(invocation.getArgument(0, Runnable.class));
            return true;
        }).when(executor).trySubmit(any());
        when(userRepository.findByOrganizationIdAndUserId(ORG, USER))
                .thenReturn(Optional.of(entraUser().avatarSyncedAt(STALE).build()));
        lenient().when(photoSource.photoMetadata("entra-1", TENANT)).thenReturn(Optional.empty());

        // when
        sut.maybeSync(ORG, USER, null);
        sut.maybeSync(ORG, USER, null);

        // then - one submission; after the held task runs, the marker clears and a resubmit works
        verify(executor, times(1)).trySubmit(any());
        held.get().run();
        sut.maybeSync(ORG, USER, null);
        verify(executor, times(2)).trySubmit(any());
    }

    @Test
    void maybeSync_queueFull_dropsAndClearsTheInFlightMarker() {

        // given - doReturn (not when(...).thenReturn) so re-stubbing does not fire the setUp inline-runner.
        doReturn(false).when(executor).trySubmit(any());
        when(userRepository.findByOrganizationIdAndUserId(ORG, USER))
                .thenReturn(Optional.of(entraUser().avatarSyncedAt(STALE).build()));

        // when - must not throw
        sut.maybeSync(ORG, USER, null);
        sut.maybeSync(ORG, USER, null);

        // then - the marker was cleared each time, so both attempts reached the executor
        verify(executor, times(2)).trySubmit(any());
        verifyNoInteractions(photoSource, mediaStorage);
    }

    @Test
    void maybeSync_memberHasNoExternalId_mirrorsTheClaimUrl() {

        given(userRepository.findByOrganizationIdAndUserId(ORG, USER))
                .willReturn(Optional.of(userWith(null, null, null)));
        given(claimUrlPhotoSource.fetch(CLAIM_URL))
                .willReturn(Optional.of(new AvatarPhoto(new byte[]{1, 2, 3}, "image/png")));

        sut.maybeSync(ORG, USER, CLAIM_URL);

        var keyCaptor = ArgumentCaptor.forClass(String.class);
        then(mediaStorage).should().store(keyCaptor.capture(), any(), any(), anyLong());
        then(userRepository).should()
                .updateSyncedAvatar(argThat(update -> update.avatarMediaKey().equals(keyCaptor.getValue())));
    }

    @Test
    void maybeSync_graphAndClaimUrlBothAvailable_prefersGraph() {

        given(resolver.resolve(ORG)).willReturn(Optional.of(new OrganizationIdp(IdpProvider.ENTRA, TENANT, true)));
        given(userRepository.findByOrganizationIdAndUserId(ORG, USER))
                .willReturn(Optional.of(userWith(EXTERNAL_ID, null, null)));
        given(photoSource.photoMetadata(EXTERNAL_ID, TENANT))
                .willReturn(Optional.of(new EntraPhotoSource.PhotoMetadata("etag-1")));
        given(photoSource.fetchPhoto(EXTERNAL_ID, TENANT))
                .willReturn(Optional.of(new AvatarPhoto(new byte[]{9}, "image/jpeg")));

        sut.maybeSync(ORG, USER, CLAIM_URL);

        then(claimUrlPhotoSource).should(never()).fetch(any());
    }

    @Test
    void maybeSync_neitherAGraphSourceNorAClaimUrl_doesNothing() {

        given(userRepository.findByOrganizationIdAndUserId(ORG, USER))
                .willReturn(Optional.of(userWith(null, null, null)));

        sut.maybeSync(ORG, USER, null);

        then(mediaStorage).shouldHaveNoInteractions();
        then(executor).shouldHaveNoInteractions();
        then(claimUrlPhotoSource).shouldHaveNoInteractions();
    }

    @Test
    void maybeSync_externalIdInNonEntraOrganization_fallsBackToTheClaimUrl() {

        given(userRepository.findByOrganizationIdAndUserId(ORG, USER))
                .willReturn(Optional.of(userWith(EXTERNAL_ID, null, null)));
        given(resolver.resolve(ORG))
                .willReturn(Optional.of(new OrganizationIdp(IdpProvider.GENERIC_OIDC, null, false)));
        given(claimUrlPhotoSource.fetch(CLAIM_URL))
                .willReturn(Optional.of(new AvatarPhoto(new byte[]{1, 2, 3}, "image/png")));

        sut.maybeSync(ORG, USER, CLAIM_URL);

        then(photoSource).shouldHaveNoInteractions();
        then(userRepository).should().updateSyncedAvatar(any());
    }

    @Test
    void maybeSync_claimFetchThrows_leavesTheRowAlone() {

        given(userRepository.findByOrganizationIdAndUserId(ORG, USER))
                .willReturn(Optional.of(userWith(null, "avatars/org/existing.png", "hash")));
        given(claimUrlPhotoSource.fetch(CLAIM_URL)).willThrow(new AvatarFetchFailedException("boom", null));

        sut.maybeSync(ORG, USER, CLAIM_URL);

        then(userRepository).should(never()).clearSyncedAvatar(any(), any(), any());
        then(userRepository).should(never()).touchAvatarSyncedAt(any(), any(), any());
        then(mediaStorage).should(never()).delete(any());
    }

    @Test
    void maybeSync_claimPhotoContentUnchanged_touchesTheStampWithoutStoring() {

        given(userRepository.findByOrganizationIdAndUserId(ORG, USER))
                .willReturn(Optional.of(userWith(null, "avatars/org/existing.png", UNCHANGED_CONTENT_HASH)));
        given(claimUrlPhotoSource.fetch(CLAIM_URL))
                .willReturn(Optional.of(new AvatarPhoto(UNCHANGED_BYTES, "image/png")));

        sut.maybeSync(ORG, USER, CLAIM_URL);

        then(mediaStorage).should(never()).store(any(), any(), any(), anyLong());
        then(userRepository).should().touchAvatarSyncedAt(eq(ORG), eq(USER), any());
    }

    @Test
    void maybeSync_claimUrlNoLongerYieldsAPhoto_clearsTheMirroredAvatar() {

        given(userRepository.findByOrganizationIdAndUserId(ORG, USER))
                .willReturn(Optional.of(userWith(null, "avatars/org/existing.png", "hash")));
        given(claimUrlPhotoSource.fetch(CLAIM_URL)).willReturn(Optional.empty());

        sut.maybeSync(ORG, USER, CLAIM_URL);

        then(userRepository).should().clearSyncedAvatar(eq(ORG), eq(USER), any());
    }
}
