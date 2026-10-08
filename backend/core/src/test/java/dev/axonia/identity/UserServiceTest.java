package dev.axonia.identity;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.UsageCounters;
import dev.axonia.identity.internal.AvatarSyncService;
import dev.axonia.identity.internal.UserEntity;
import dev.axonia.identity.internal.UserRepository;
import dev.axonia.identity.internal.UserTimeZoneRow;
import dev.axonia.identity.internal.UserUpsert;
import dev.axonia.identity.mother.DirectoryUserMother;
import dev.axonia.identity.mother.UserEntityMother;
import dev.axonia.identity.mother.UserMother;
import dev.axonia.media.MediaLinks;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class UserServiceTest {

    private static final IdentityClaims CLAIMS = IdentityClaims.builder().userId("11111111-1111-1111-1111-111111111111")
            .organizationId("default").displayName("Alice Anderson").email("alice@axonia.example")
            .avatarUrl("https://cdn.example.com/alice.png").build();

    private static final Instant SEEN_AT = Instant.parse("2026-07-25T12:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClaimsReader claimsReader;

    @Mock
    private AvatarSyncService avatarSyncService;

    @Mock
    private SignInProviderResolver signInProviderResolver;

    @Mock
    private Clock clock;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    /**
     * The real counters rather than a mock, so the assertions below read the series a dashboard panel reads. A
     * {@code @Spy} is how {@code @InjectMocks} accepts a real collaborator at all.
     */
    @Spy
    private UsageCounters usageCounters = new UsageCounters(meters);

    @Spy
    private MediaLinks mediaLinks = new MediaLinks("https://media.example.test");

    @InjectMocks
    private UserService sut;

    @BeforeEach
    void stubClock() {

        // Most tests here provision a user and never care about the exact instant; the few that do override this.
        lenient().when(clock.instant()).thenReturn(SEEN_AT);
    }

    @Test
    void jitProvisioningStampsLastSeen() {

        // given
        var claims = IdentityClaims.builder().organizationId("acme").userId("u-new").displayName("Ada Lovelace")
                .email("ada@acme.test").build();
        when(userRepository.findByOrganizationIdAndUserId("acme", "u-new")).thenReturn(Optional.of(UserEntity.builder()
                .organizationId("acme").userId("u-new").displayName("Ada Lovelace").email("ada@acme.test").build()));

        // when
        sut.provision(claims);

        // then
        verifyUpsertWithGeneratedScimId(UserUpsert.builder().organizationId("acme").userId("u-new")
                .displayName("Ada Lovelace").email("ada@acme.test").build(), SEEN_AT);
    }

    /**
     * A row still at its first version is one the upsert just inserted, which is the only thing on this path that is a
     * sign-up: the ON CONFLICT branch raises the version, so a returning member never reaches zero.
     */
    @Test
    void aFirstSignInThatInsertsTheRowCountsOneSignUp() {

        var claims = IdentityClaims.builder().organizationId("acme").userId("u-new").displayName("Ada Lovelace")
                .email("ada@acme.test").build();
        when(userRepository.findByOrganizationIdAndUserId("acme", "u-new")).thenReturn(Optional.of(UserEntity.builder()
                .organizationId("acme").userId("u-new").displayName("Ada Lovelace").version(0L).build()));

        sut.provision(claims);

        assertThat(signUps()).isEqualTo(1.0);
    }

    @Test
    void aReturningMemberCountsNoSignUp() {

        var claims = IdentityClaims.builder().organizationId("acme").userId("u-old").displayName("Ada Lovelace")
                .email("ada@acme.test").build();
        when(userRepository.findByOrganizationIdAndUserId("acme", "u-old")).thenReturn(Optional.of(UserEntity.builder()
                .organizationId("acme").userId("u-old").displayName("Ada Lovelace").version(4L).build()));

        sut.provision(claims);

        assertThat(signUps()).isZero();
    }

    private double signUps() {

        return meters.get("axonia.usage.events").tag("event", "sign-up").counter().count();
    }

    @Test
    void aRepeatProvisionMovesLastSeenForward() {

        // given
        var first = Instant.parse("2026-07-25T12:00:00Z");
        var second = Instant.parse("2026-07-25T12:05:00Z");
        when(clock.instant()).thenReturn(first, second);
        var claims = IdentityClaims.builder().organizationId("acme").userId("u-new").displayName("Ada Lovelace")
                .email("ada@acme.test").build();
        when(userRepository.findByOrganizationIdAndUserId("acme", "u-new")).thenReturn(Optional.of(UserEntity.builder()
                .organizationId("acme").userId("u-new").displayName("Ada Lovelace").email("ada@acme.test").build()));

        // when
        sut.provision(claims);
        sut.provision(claims);

        // then
        var seenAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(userRepository, times(2)).upsert(any(), seenAtCaptor.capture());
        assertThat(seenAtCaptor.getAllValues()).containsExactly(first, second);
    }

    @Test
    void provision() {

        // given
        when(userRepository.findByOrganizationIdAndUserId("default", "11111111-1111-1111-1111-111111111111"))
                .thenReturn(Optional.of(UserEntityMother.any().id(1L).build()));

        // when
        var actual = sut.provision(CLAIMS);

        // then
        verifyUpsertWithGeneratedScimId(
                UserUpsert.builder().organizationId("default").userId("11111111-1111-1111-1111-111111111111")
                        .displayName("Alice Anderson").email("alice@axonia.example").build(),
                SEEN_AT);
        assertThat(actual).usingRecursiveAssertion().isEqualTo(UserMother.any().build());
    }

    @Test
    void provision_neverLeaksTheClaimAvatarUrlIntoTheReturnedProfile() {

        // given - the token asserts an IdP-hosted picture, but this member already has a mirrored avatar.
        // No findActiveSublessByEmail stub: a member who already has a row under this subject never reaches the
        // sub-less lookup, and strict stubs would fail on one.
        when(userRepository.findByOrganizationIdAndUserId("t1", "u1"))
                .thenReturn(Optional.of(UserEntity.builder().organizationId("t1").userId("u1").displayName("Alex")
                        .email("a@x.com").avatarMediaKey("avatars/t1/u1.png").build()));
        var claims = IdentityClaims.builder().userId("u1").organizationId("t1").displayName("Alex").email("a@x.com")
                .avatarUrl("https://img/a.png").build();

        // when
        var actual = sut.provision(claims);

        // then - the upsert carries no avatar at all, and the returned avatar is our own, not the claim's
        verifyUpsertWithGeneratedScimId(
                UserUpsert.builder().organizationId("t1").userId("u1").displayName("Alex").email("a@x.com").build(),
                SEEN_AT);
        assertThat(actual.avatarUrl()).isEqualTo("https://media.example.test/avatars/t1/u1.png");
    }

    @Test
    void provision_blowsUpRatherThanSilentlyServingAStaleProfile_whenTheJustWrittenRowIsNotReadableBack() {

        // given - the upsert reports success, but the immediate read-back finds nothing: this must never happen in
        // practice (upsert is a single race-safe statement), but a member who just signed in must never be served a
        // wrong or stale profile if it somehow did
        when(userRepository.findByOrganizationIdAndUserId("default", "11111111-1111-1111-1111-111111111111"))
                .thenReturn(Optional.empty());

        // when / then
        assertThatIllegalStateException().isThrownBy(() -> sut.provision(CLAIMS)).withMessageContaining("default")
                .withMessageContaining("11111111-1111-1111-1111-111111111111");
    }

    @Test
    void provision_triggersAvatarReconciliation() {

        // given
        when(userRepository.findByOrganizationIdAndUserId("default", "11111111-1111-1111-1111-111111111111"))
                .thenReturn(Optional.of(UserEntityMother.any().id(1L).build()));

        // when
        sut.provision(CLAIMS);

        // then
        verify(avatarSyncService).maybeSync("default", "11111111-1111-1111-1111-111111111111",
                "https://cdn.example.com/alice.png");
    }

    @Test
    void me() {

        // given
        var entity = UserEntityMother.any().id(1L).build();
        when(claimsReader.read()).thenReturn(CLAIMS);
        when(userRepository.findByOrganizationIdAndUserId("default", "11111111-1111-1111-1111-111111111111"))
                .thenReturn(Optional.of(entity));

        // when
        var actual = sut.me();

        // then
        verify(userRepository, never()).upsert(any(), any());
        assertThat(actual).usingRecursiveAssertion().isEqualTo(UserMother.any().build());
    }

    @Test
    void me_provisionsWhenAbsent() {

        // given
        when(claimsReader.read()).thenReturn(CLAIMS);
        when(userRepository.findByOrganizationIdAndUserId("default", "11111111-1111-1111-1111-111111111111"))
                .thenReturn(Optional.empty(), Optional.of(UserEntityMother.any().id(1L).build()));

        // when
        var actual = sut.me();

        // then
        verifyUpsertWithGeneratedScimId(
                UserUpsert.builder().organizationId("default").userId("11111111-1111-1111-1111-111111111111")
                        .displayName("Alice Anderson").email("alice@axonia.example").build(),
                SEEN_AT);
        assertThat(actual).usingRecursiveAssertion().isEqualTo(UserMother.any().build());
    }

    @Test
    void upsertProfile_generatesAScimIdSoTheUserIsPublishableOverScim() {

        // given
        when(claimsReader.read()).thenReturn(CLAIMS);
        when(userRepository.findByOrganizationIdAndUserId("default", "11111111-1111-1111-1111-111111111111"))
                .thenReturn(Optional.empty(), Optional.of(UserEntityMother.any().id(1L).build()));

        // when
        sut.me();

        // then
        var captor = ArgumentCaptor.forClass(UserUpsert.class);
        verify(userRepository).upsert(captor.capture(), any());
        assertThat(captor.getValue().scimId()).isNotBlank();
    }

    @Test
    void me_servesClaimsOnlyProfileWhenTokenHasNoOrganization() {

        // given - a fresh social sign-in: valid identity, no org claim, and a third-party picture URL
        when(claimsReader.read()).thenReturn(IdentityClaims.builder().userId("sub-9").organizationId(null)
                .displayName("Ada Lovelace").email("ada@example.dev").avatarUrl("https://pic").build());

        // when
        var actual = sut.me();

        // then - profile comes from the token, but never the claim's avatar: there is no row to mirror one into
        // or read one back from, so a third-party URL must never reach the response
        assertThat(actual).usingRecursiveComparison().isEqualTo(User.builder().id("sub-9").displayName("Ada Lovelace")
                .email("ada@example.dev").memberStatus(UserDirectory.MemberStatus.PRESENT).build());
        assertThat(actual.avatarUrl()).isNull();
        verifyNoInteractions(userRepository);
    }

    @Test
    void me_carriesTheResolvedSignInProviderOnTheOrgLessBranch() {

        // given - a fresh social sign-in with no org claim
        when(claimsReader.read()).thenReturn(
                IdentityClaims.builder().userId("sub-9").organizationId(null).displayName("Ada Lovelace").build());
        when(signInProviderResolver.resolve(null)).thenReturn("Google");

        // when
        var actual = sut.me();

        // then
        assertThat(actual.signInProvider()).isEqualTo("Google");
    }

    @Test
    void me_carriesTheResolvedSignInProviderOnTheExistingProfileBranch() {

        // given - a returning member whose token names the provider that vouched for this sign-in
        var entity = UserEntityMother.any().id(1L).build();
        when(claimsReader.read()).thenReturn(CLAIMS);
        when(userRepository.findByOrganizationIdAndUserId("default", "11111111-1111-1111-1111-111111111111"))
                .thenReturn(Optional.of(entity));
        when(signInProviderResolver.resolve("default")).thenReturn("Acme SSO, via Microsoft Entra ID");

        // when
        var actual = sut.me();

        // then
        assertThat(actual.signInProvider()).isEqualTo("Acme SSO, via Microsoft Entra ID");
    }

    @Test
    void me_carriesTheResolvedSignInProviderWhenProvisioningOnTheSpot() {

        // given - a first-ever sign-in that JIT-provisions a new profile
        when(claimsReader.read()).thenReturn(CLAIMS);
        when(userRepository.findByOrganizationIdAndUserId("default", "11111111-1111-1111-1111-111111111111"))
                .thenReturn(Optional.empty(), Optional.of(UserEntityMother.any().id(1L).build()));
        when(signInProviderResolver.resolve("default")).thenReturn("GitHub");

        // when
        var actual = sut.me();

        // then
        assertThat(actual.signInProvider()).isEqualTo("GitHub");
    }

    @Test
    void me_reportsDeactivatedStatusForADeprovisionedProfile() {

        // given - SCIM deprovisioned this member, but their session is still valid until it expires
        var entity = UserEntityMother.any().id(1L).active(false).build();
        when(claimsReader.read()).thenReturn(CLAIMS);
        when(userRepository.findByOrganizationIdAndUserId("default", "11111111-1111-1111-1111-111111111111"))
                .thenReturn(Optional.of(entity));

        // when
        var actual = sut.me();

        // then
        assertThat(actual.memberStatus()).isEqualTo(UserDirectory.MemberStatus.DEACTIVATED);
    }

    @Test
    void resolve_orgLessTokenResolvesNothing() {

        // given - an org-less (fresh social) token: there are no per-org profiles to resolve
        when(claimsReader.read()).thenReturn(
                IdentityClaims.builder().userId("sub-9").organizationId(null).displayName("Ada Lovelace").build());

        // when
        var actual = sut.resolve(List.of("sub-9", "other"));

        // then - explicit empty result, no repository query at all
        assertThat(actual).isEmpty();
        verifyNoInteractions(userRepository);
    }

    @Test
    void resolve() {

        // given
        var alice = UserEntityMother.any().id(1L).build();
        var bob = UserEntityMother.any().id(2L).userId("22222222-2222-2222-2222-222222222222").displayName("Bob Brandt")
                .email("bob@axonia.example").build();
        var ids = List.of(alice.getUserId(), bob.getUserId());

        when(claimsReader.read()).thenReturn(CLAIMS);
        when(userRepository.findByOrganizationIdAndUserIdIn("default", ids)).thenReturn(List.of(alice, bob));

        // when
        var actual = sut.resolve(ids);

        // then both come back present
        assertThat(actual).usingRecursiveFieldByFieldElementComparator().containsExactly(
                DirectoryUserMother.any().build(),
                DirectoryUserMother.any().id("22222222-2222-2222-2222-222222222222").displayName("Bob Brandt").build());
    }

    @Test
    void resolve_unknownIdsComeBackAsErasedEntriesRatherThanBeingOmitted() {

        // given
        var alice = UserEntityMother.any().id(1L).build();
        var ids = List.of(alice.getUserId(), "unknown-sub");

        when(claimsReader.read()).thenReturn(CLAIMS);
        when(userRepository.findByOrganizationIdAndUserIdIn("default", ids)).thenReturn(List.of(alice));

        // when
        var actual = sut.resolve(ids);

        // then - the batch stays the same size; the unresolvable id carries the port's neutral label and ERASED status
        assertThat(actual).hasSize(2);
        assertThat(actual).filteredOn(u -> "unknown-sub".equals(u.id())).singleElement().satisfies(u -> {
            assertThat(u.displayName()).isEqualTo(UserDirectory.DirectoryUser.DELETED_MEMBER_LABEL);
            assertThat(u.memberStatus()).isEqualTo(UserDirectory.MemberStatus.ERASED);
            assertThat(u.avatarUrl()).isNull();
        });
    }

    @Test
    void resolve_collapsesADuplicateInputIdToOneEntry() {

        // given - the query only ever sees the deduplicated id, and answers with one row
        var alice = UserEntityMother.any().id(1L).build();
        when(claimsReader.read()).thenReturn(CLAIMS);
        when(userRepository.findByOrganizationIdAndUserIdIn("default", List.of(alice.getUserId())))
                .thenReturn(List.of(alice));

        // when - the caller asks for the same id twice
        var actual = sut.resolve(List.of(alice.getUserId(), alice.getUserId()));

        // then - one entry, not two
        assertThat(actual).singleElement().extracting(DirectoryUser::id).isEqualTo(alice.getUserId());
    }

    @Test
    void resolve_reportsDeactivatedStatusForADeprovisionedMember() {

        // given
        var entity = UserEntityMother.any().active(false).build();
        when(claimsReader.read()).thenReturn(CLAIMS);
        when(userRepository.findByOrganizationIdAndUserIdIn("default", List.of(entity.getUserId())))
                .thenReturn(List.of(entity));

        // when
        var actual = sut.resolve(List.of(entity.getUserId()));

        // then
        assertThat(actual).singleElement().extracting(DirectoryUser::memberStatus)
                .isEqualTo(UserDirectory.MemberStatus.DEACTIVATED);
    }

    @Test
    void resolve_exposesOrgAttributesAndAvatar() {

        var entity = UserEntity.builder().userId("u1").displayName("Alex").email("a@x.com")
                .avatarMediaKey("avatars/default/a.png").department("Platform").businessUnit("Acme").team("Payments")
                .build();
        when(claimsReader.read()).thenReturn(CLAIMS);
        when(userRepository.findByOrganizationIdAndUserIdIn("default", List.of("u1"))).thenReturn(List.of(entity));
        var users = sut.resolve(List.of("u1"));
        assertThat(users).singleElement().satisfies(u -> {
            assertThat(u.avatarUrl()).isEqualTo("https://media.example.test/avatars/default/a.png");
            assertThat(u.department()).isEqualTo("Platform");
            assertThat(u.businessUnit()).isEqualTo("Acme");
            assertThat(u.team()).isEqualTo("Payments");
        });
    }

    @Test
    void resolve_neverExposesEmail() {

        // given
        var entity = UserEntityMother.any().organizationId("default").userId("alice").email("alice@example.com")
                .build();
        when(claimsReader.read())
                .thenReturn(IdentityClaims.builder().userId("caller").organizationId("default").displayName("Caller")
                        .email("caller@axonia.example").avatarUrl("https://cdn.example.com/caller.png").build());
        when(userRepository.findByOrganizationIdAndUserIdIn("default", List.of("alice"))).thenReturn(List.of(entity));

        // when
        var actual = sut.resolve(List.of("alice"));

        // then - the directory projection carries no email field at all
        assertThat(actual).first().satisfies(u -> {
            assertThat(u.id()).isEqualTo("alice");
            assertThat(u).hasNoNullFieldsOrPropertiesExcept("avatarUrl", "department", "businessUnit", "team");
        });
    }

    @Test
    void displayName_returnsDisplayNameWhenFound() {

        var entity = UserEntityMother.any().build();
        when(userRepository.findByOrganizationIdAndUserId("default", entity.getUserId()))
                .thenReturn(Optional.of(entity));

        var result = sut.displayName("default", entity.getUserId());

        assertThat(result).hasValue("Alice Anderson");
    }

    @Test
    void displayName_returnsEmptyWhenNotFound() {

        when(userRepository.findByOrganizationIdAndUserId("default", "unknown")).thenReturn(Optional.empty());

        assertThat(sut.displayName("default", "unknown")).isEmpty();
    }

    @Test
    void displayNames_returnsMapOfDisplayNames() {

        var alice = UserEntityMother.any().build();
        var bob = UserEntityMother.any().userId("bob-id").displayName("Bob Brandt").email("bob@axonia.example").build();
        var ids = List.of(alice.getUserId(), bob.getUserId());
        when(userRepository.findByOrganizationIdAndUserIdIn("default", ids)).thenReturn(List.of(alice, bob));

        var result = sut.displayNames("default", ids);

        assertThat(result).containsExactlyInAnyOrderEntriesOf(
                Map.of(alice.getUserId(), "Alice Anderson", "bob-id", "Bob Brandt"));
    }

    @Test
    void theDeclaredClockWinsOverTheObservedOne() {

        var observed = UserEntity.builder().userId("alice").displayName("Alice").active(true)
                .observedTimeZone("Europe/Brussels").build();
        var declared = observed.toBuilder().declaredTimeZone("America/New_York").build();

        assertThat(sut.toUser(observed).timeZone()).isEqualTo("Europe/Brussels");
        assertThat(sut.toUser(declared).timeZone()).isEqualTo("America/New_York");
    }

    /**
     * The observation is on the profile beside the effective clock so the frontend can tell a drifted browser from an
     * agreeing one and report only on drift. A declaration hides the observation from every other reader, not from this
     * one.
     */
    @Test
    void theObservationIsCarriedBesideADeclarationThatOverridesIt() {

        var entity = UserEntity.builder().userId("alice").displayName("Alice").active(true)
                .observedTimeZone("Europe/Brussels").declaredTimeZone("America/New_York").build();

        var user = sut.toUser(entity);

        assertThat(user.observedTimeZone()).isEqualTo("Europe/Brussels");
        assertThat(user.timeZoneIsOwn()).isTrue();
    }

    @Test
    void anUnmeasuredMemberCarriesNoClockRatherThanADefault() {

        var entity = UserEntity.builder().userId("alice").displayName("Alice").active(true).build();

        var user = sut.toUser(entity);

        assertThat(user.timeZone()).isNull();
        assertThat(user.observedTimeZone()).isNull();
        assertThat(user.timeZoneIsOwn()).isFalse();
    }

    @Test
    void declareTimeZone_writesTheDeclarationAndReturnsTheRefreshedProfile() {

        when(claimsReader.read()).thenReturn(CLAIMS);
        when(userRepository.findByOrganizationIdAndUserId("default", CLAIMS.userId()))
                .thenReturn(Optional.of(UserEntityMother.any().active(true).declaredTimeZone("Asia/Kolkata").build()));

        var user = sut.declareTimeZone("Asia/Kolkata");

        verify(userRepository).updateDeclaredTimeZone("default", CLAIMS.userId(), "Asia/Kolkata", SEEN_AT);
        assertThat(user.timeZone()).isEqualTo("Asia/Kolkata");
        assertThat(user.timeZoneIsOwn()).isTrue();
    }

    @Test
    void clearDeclaredTimeZone_dropsItAndLandsOnTheObservation() {

        when(claimsReader.read()).thenReturn(CLAIMS);
        when(userRepository.findByOrganizationIdAndUserId("default", CLAIMS.userId())).thenReturn(
                Optional.of(UserEntityMother.any().active(true).observedTimeZone("Europe/Brussels").build()));

        var user = sut.clearDeclaredTimeZone();

        verify(userRepository).clearDeclaredTimeZone("default", CLAIMS.userId(), SEEN_AT);
        assertThat(user.timeZone()).isEqualTo("Europe/Brussels");
        assertThat(user.timeZoneIsOwn()).isFalse();
    }

    @Test
    void observeTimeZone_recordsWhatTheBrowserReported() {

        when(claimsReader.read()).thenReturn(CLAIMS);
        when(userRepository.findByOrganizationIdAndUserId("default", CLAIMS.userId())).thenReturn(
                Optional.of(UserEntityMother.any().active(true).observedTimeZone("Europe/Brussels").build()));

        sut.observeTimeZone("Europe/Brussels");

        verify(userRepository).updateObservedTimeZone("default", CLAIMS.userId(), "Europe/Brussels", SEEN_AT);
    }

    @Test
    void timeZones_omitsAMemberWithNoClock() {

        when(userRepository.findTimeZones("default", List.of("alice", "bob")))
                .thenReturn(List.of(new UserTimeZoneRow("alice", "Europe/Berlin")));

        assertThat(sut.timeZones("default", List.of("alice", "bob")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("alice", "Europe/Berlin"));
    }

    /** {@code IN ()} is not valid SQL, so the guard is correctness rather than a saved round trip. */
    @Test
    void timeZones_neverAsksTheDatabaseForNobody() {

        assertThat(sut.timeZones("default", List.of())).isEmpty();
        verifyNoInteractions(userRepository);
    }

    @Test
    void theProfileCarriesTenureAndRecency() {

        var created = Instant.parse("2026-03-04T10:15:30Z");
        var seen = Instant.parse("2026-08-14T08:00:00Z");
        var entity = UserEntity.builder().userId("alice").displayName("Alice").active(true).createdAt(created)
                .lastSeenAt(seen).build();

        var user = sut.toUser(entity);

        assertThat(user.memberSince()).isEqualTo(created);
        assertThat(user.lastActive()).isEqualTo(seen);
    }

    @Test
    void aMirroredOnlyAvatarIsNotTheMembersOwn() {

        // given
        var mirrored = UserEntity.builder().userId("alice").displayName("Alice").active(true)
                .avatarMediaKey("avatars/default/mirrored.jpg").build();
        var own = mirrored.toBuilder().avatarOverrideKey("avatars/default/own.png").build();

        // when / then
        assertThat(sut.toUser(mirrored).avatarIsOwn()).isFalse();
        assertThat(sut.toUser(own).avatarIsOwn()).isTrue();
    }

    @Test
    void aClaimsOnlyProfileCarriesNeither() {

        // given - me()'s org-less branch: no row to read memberSince/lastActive back from
        var claims = IdentityClaims.builder().userId("alice").displayName("Alice").build();
        when(claimsReader.read()).thenReturn(claims);

        // when
        var user = sut.me();

        // then
        assertThat(user.memberSince()).isNull();
        assertThat(user.lastActive()).isNull();
    }

    @Test
    void overrideWinsOverTheMirroredAvatar() {

        // given
        var entity = UserEntity.builder().userId("alice").displayName("Alice").active(true)
                .avatarMediaKey("avatars/default/mirrored.jpg").avatarOverrideKey("avatars/default/own.jpg").build();

        // when
        var actual = sut.toUser(entity);

        // then
        assertThat(actual.avatarUrl()).isEqualTo("https://media.example.test/avatars/default/own.jpg");
    }

    @Test
    void mirroredAvatarServesWhenThereIsNoOverride() {

        // given
        var entity = UserEntity.builder().userId("alice").displayName("Alice").active(true)
                .avatarMediaKey("avatars/default/mirrored.jpg").build();

        // when
        var actual = sut.toUser(entity);

        // then
        assertThat(actual.avatarUrl()).isEqualTo("https://media.example.test/avatars/default/mirrored.jpg");
    }

    @Test
    void aMemberWithNoStoredPictureHasNoAvatarAddress() {

        // given
        var entity = UserEntity.builder().userId("alice").displayName("Alice").active(true).build();

        // when
        var actual = sut.toUser(entity);

        // then
        assertThat(actual.avatarUrl()).isNull();
        assertThat(actual.avatarIsOwn()).isFalse();
    }

    @Test
    void directoryUserRespectsOverrideWhenPresent() {

        // given
        var entity = UserEntity.builder().userId("alice").displayName("Alice").active(true)
                .avatarMediaKey("avatars/default/mirrored.jpg").avatarOverrideKey("avatars/default/own.jpg").build();

        // when
        var actual = sut.toDirectoryUser(entity);

        // then
        assertThat(actual.avatarUrl()).isEqualTo("https://media.example.test/avatars/default/own.jpg");
    }

    @Test
    void provision_reconciliationBranchRespectsAvatarOverride() {

        var recordWithOverride = UserEntity.builder().id(1L).organizationId("t1").userId("existing-user")
                .displayName("Alice").email("alice@example.com").active(true).avatarMediaKey("avatars/t1/mirrored.jpg")
                .avatarOverrideKey("avatars/t1/own.jpg").build();
        when(userRepository.findActiveSublessByEmail("t1", "alice@example.com"))
                .thenReturn(Optional.of(recordWithOverride));

        var claims = IdentityClaims.builder().userId("new-sub-id").organizationId("t1").displayName("Alice")
                .email("alice@example.com").build();

        var actual = sut.provision(claims);

        assertThat(actual.avatarUrl()).isEqualTo("https://media.example.test/avatars/t1/own.jpg");
    }

    @Test
    void provision_reconciliationBranchCarriesTenureRecencyAndAvatarOwnership() {

        // given - the reconciled record already carries a creation date and an override; the branch attaches the
        // sub and stamps last-seen with the same clock read the JIT-upsert branch would have used
        var created = Instant.parse("2026-01-10T09:00:00Z");
        var recordWithOverride = UserEntity.builder().id(1L).organizationId("t1").userId("existing-user")
                .displayName("Alice").email("alice@example.com").active(true).createdAt(created)
                .avatarMediaKey("avatars/t1/mirrored.jpg").avatarOverrideKey("avatars/t1/own.jpg").build();
        when(userRepository.findActiveSublessByEmail("t1", "alice@example.com"))
                .thenReturn(Optional.of(recordWithOverride));

        var claims = IdentityClaims.builder().userId("new-sub-id").organizationId("t1").displayName("Alice")
                .email("alice@example.com").build();

        // when
        var actual = sut.provision(claims);

        // then - memberSince survives from the original row, lastActive is the freshly stamped instant (not the
        // record's now-stale one), and avatarIsOwn agrees with the avatarUrl this same response already serves
        assertThat(actual.memberSince()).isEqualTo(created);
        assertThat(actual.lastActive()).isEqualTo(SEEN_AT);
        assertThat(actual.avatarIsOwn()).isTrue();
    }

    /**
     * Every upsert now carries a freshly generated {@code scimId}, so a full-equality {@code verify} would fail on that
     * one field alone. Captures the call, checks everything else exactly, and checks the id separately.
     */
    private void verifyUpsertWithGeneratedScimId(UserUpsert expectedWithoutScimId, Instant seenAt) {

        var captor = ArgumentCaptor.forClass(UserUpsert.class);
        verify(userRepository).upsert(captor.capture(), eq(seenAt));
        assertThat(captor.getValue()).usingRecursiveComparison().ignoringFields("scimId")
                .isEqualTo(expectedWithoutScimId);
        assertThat(captor.getValue().scimId()).isNotBlank();
    }
}
