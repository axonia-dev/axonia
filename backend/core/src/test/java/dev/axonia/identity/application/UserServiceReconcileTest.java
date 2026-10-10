package dev.axonia.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.identity.api.ClaimsReader;
import dev.axonia.identity.api.IdentityClaims;
import dev.axonia.identity.application.port.out.UserStore;
import dev.axonia.identity.domain.UserAccount;
import dev.axonia.identity.domain.UserUpsert;
import dev.axonia.media.api.MediaLinks;
import dev.axonia.shared.api.metrics.UsageCounters;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class UserServiceReconcileTest {

    private static final IdentityClaims CLAIMS = IdentityClaims.builder().userId("sub-123").organizationId("default")
            .displayName("Alice").email("alice@acme.test").avatarUrl("https://cdn.example.com/alice.png").build();

    private static final Instant SEEN_AT = Instant.parse("2026-07-25T12:00:00Z");

    @Mock
    UserStore userStore;
    @Mock
    ClaimsReader claimsReader;
    @Mock
    AvatarSyncService avatarSyncService;
    @Mock
    Clock clock;

    final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    @Spy
    UsageCounters usageCounters = new UsageCounters(meters);

    @Spy
    MediaLinks mediaLinks = new MediaLinks("https://media.example.test");

    @InjectMocks
    UserService sut;

    @BeforeEach
    void stubClock() {

        lenient().when(clock.instant()).thenReturn(SEEN_AT);
    }

    @Test
    void reconcilingASublessRowStampsLastSeen() {

        // given a SCIM row that has never signed in
        when(userStore.findActiveSublessByEmail("acme", "ada@acme.test")).thenReturn(
                Optional.of(UserAccount.builder().id(7L).displayName("Ada Lovelace").email("ada@acme.test").build()));

        // when she signs in for the first time
        sut.provision(IdentityClaims.builder().organizationId("acme").userId("u-ada").displayName("Ada Lovelace")
                .email("ada@acme.test").build());

        // then the row is reconciled - not inserted afresh - and stamped with the instant read from the clock
        verify(userStore).attachSub(7L, "u-ada", SEEN_AT);
        verify(userStore, never()).upsert(any(), any());
    }

    /**
     * Reconciliation attaches a subject to a row SCIM already created, so no {@code identity_user} row comes into
     * existence and nothing is a sign-up. Counting it would make one directory push followed by everybody's first
     * sign-in read as a second wave of sign-ups the same size as the first.
     */
    @Test
    void reconcilingASublessRowCountsNoSignUp() {

        when(userStore.findActiveSublessByEmail("acme", "ada@acme.test")).thenReturn(
                Optional.of(UserAccount.builder().id(7L).displayName("Ada Lovelace").email("ada@acme.test").build()));

        sut.provision(IdentityClaims.builder().organizationId("acme").userId("u-ada").displayName("Ada Lovelace")
                .email("ada@acme.test").build());

        assertThat(meters.get("axonia.usage.events").tag("event", "sign-up").counter().count()).isZero();
    }

    /**
     * The service half of the collision {@code UserServiceProvisionJdbcTest} proves against the real constraint: a
     * member who already has a row under this subject must not have it dragged onto the sub-less row a replacement
     * directory pushed at their new address. The pushed row is stubbed {@code lenient()} precisely because a correct
     * service never looks for it here - the stub states what the database holds, not what this path is expected to
     * read.
     */
    @Test
    void aMemberWhoAlreadyHasARowUnderThisSubjectIsNotReconciledOntoAnother() {

        when(userStore.findByOrganizationIdAndUserId("acme", "u-ada"))
                .thenReturn(Optional.of(UserAccount.builder().id(3L).organizationId("acme").userId("u-ada")
                        .displayName("Ada Lovelace").email("ada@old.test").version(4L).build()));
        lenient().when(userStore.findActiveSublessByEmail("acme", "ada@new.test")).thenReturn(
                Optional.of(UserAccount.builder().id(9L).displayName("Ada Lovelace").email("ada@new.test").build()));

        sut.provision(IdentityClaims.builder().organizationId("acme").userId("u-ada").displayName("Ada Lovelace")
                .email("ada@new.test").build());

        verify(userStore, never()).attachSub(any(), any(), any());
        verify(userStore).upsert(any(), eq(SEEN_AT));
    }

    @Test
    void reconcilesSublessRecord_returnsTheRecordsOwnAvatarNotTheClaim() {

        // given - the pre-provisioned record already carries a previously mirrored avatar
        when(userStore.findActiveSublessByEmail("default", "alice@acme.test")).thenReturn(Optional
                .of(UserAccount.builder().id(7L).displayName("Alice").avatarMediaKey("avatars/default/a.jpg").build()));

        // when - the token asserts a different (IdP-hosted) picture
        var user = sut.provision(CLAIMS);

        // then - attachSub carries no avatar, and the returned avatar is the record's own, not the claim's
        verify(userStore).attachSub(7L, "sub-123", SEEN_AT);
        verify(userStore, never()).upsert(any(), any());
        assertThat(user.avatarUrl()).isEqualTo("https://media.example.test/avatars/default/a.jpg");
    }

    /**
     * The genuine fall-through, and the only place the just-in-time payload is pinned exactly: nothing under this
     * subject when the guard looks, and no sub-less row at this address either. The guard and the post-upsert read-back
     * are the same query asked twice on this path and it must answer differently each time - empty for the guard, then
     * the row the upsert has just written.
     */
    @Test
    void fallsThroughToJitUpsert_whenNoScimRecord() {

        // given
        when(userStore.findByOrganizationIdAndUserId("default", "sub-123")).thenReturn(Optional.empty(), Optional
                .of(UserAccount.builder().userId("sub-123").displayName("Alice").email("alice@acme.test").build()));
        when(userStore.findActiveSublessByEmail("default", "alice@acme.test")).thenReturn(Optional.empty());

        // when
        sut.provision(CLAIMS);

        // then - carries a freshly generated scimId, so everything else is checked exactly and the id separately
        var captor = ArgumentCaptor.forClass(UserUpsert.class);
        verify(userStore).upsert(captor.capture(), eq(SEEN_AT));
        assertThat(captor.getValue()).usingRecursiveComparison().ignoringFields("scimId").isEqualTo(UserUpsert.builder()
                .organizationId("default").userId("sub-123").displayName("Alice").email("alice@acme.test").build());
        assertThat(captor.getValue().scimId()).isNotBlank();
    }

    /**
     * The guard's false branch on the ordinary path, which is every sign-in a member ever makes after their first: the
     * row under this subject is found, so the sub-less lookup is never reached and the just-in-time arm refreshes the
     * row they already have. The absent {@code findActiveSublessByEmail} stub is the evidence of the short-circuit -
     * strict stubs would reject one.
     */
    @Test
    void aReturningMemberRefreshesTheirOwnRowWithoutConsultingTheSublessLookup() {

        // given
        when(userStore.findByOrganizationIdAndUserId("default", "sub-123")).thenReturn(Optional
                .of(UserAccount.builder().userId("sub-123").displayName("Alice").email("alice@acme.test").build()));

        // when
        sut.provision(CLAIMS);

        // then
        verify(userStore, never()).attachSub(any(), any(), any());
        verify(userStore).upsert(any(), eq(SEEN_AT));
    }
}
