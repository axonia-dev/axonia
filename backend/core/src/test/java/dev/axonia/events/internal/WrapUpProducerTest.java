package dev.axonia.events.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.OrganizationResolver;
import dev.axonia.communication.CommunicationPort;
import dev.axonia.communication.CommunicationWindows;
import dev.axonia.communication.MessageType;
import dev.axonia.communication.OutboundMessage;
import dev.axonia.communication.ProductionWatermark;
import dev.axonia.events.CommunityAccess;
import dev.axonia.events.mother.SessionEntityMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class WrapUpProducerTest {

    private static final Instant NOW = Instant.parse("2026-06-08T00:00:00Z");
    private static final String ORGANIZATION_ID = "acme";
    private static final Long COMMUNITY_ID = 7L;
    private static final Duration MAX_AGE = Duration.ofDays(30);
    private static final String PRODUCER_KEY = WrapUpProducer.PRODUCER_KEY;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private CommunityAccess communityAccess;

    @Mock
    private SessionFactsAssembler factsAssembler;

    @Mock
    private CommunicationPort port;

    @Mock
    private OrganizationResolver organizationResolver;

    @Mock
    private CommunicationWindows communicationWindows;

    @Mock
    private ProductionWatermark watermark;

    @InjectMocks
    private WrapUpProducer producer;

    @BeforeEach
    void seedOrganization() {

        when(organizationResolver.currentOrganization()).thenReturn(Optional.of(ORGANIZATION_ID));
        // Lenient: the one test that stubs organizationResolver to fail never reaches this call, which would
        // otherwise make it an unused stubbing under strict Mockito.
        lenient().when(communicationWindows.wrapUpMaxAge()).thenReturn(MAX_AGE);
        // Default: pass the floor straight through, as if no watermark row exists yet - individual tests override
        // this to prove the producer genuinely uses whatever ProductionWatermark answers, not the floor itself.
        lenient().when(watermark.since(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    void aReleasedWrapUpIsEnqueuedToEveryMember() {

        // given
        givenDueSession(42L, NOW);
        givenMembers("user-1", "user-2");

        // when
        producer.produce(NOW);

        // then
        var message = captureEnqueued();
        assertThat(message.type()).isEqualTo(MessageType.SESSION_WRAP_UP);
        assertThat(message.subjectRef()).isEqualTo("42");
        assertThat(message.recipientUserIds()).containsExactlyInAnyOrder("user-1", "user-2");
        assertThat(message.template()).isEqualTo("session-wrap-up");
    }

    @Test
    void aWrapUpWaitsForItsReleaseInstant() {

        // given - not yet past wrapUpReleaseAt is excluded at the SQL level (SessionRepositoryTest proves the
        // boundary against a real database); simulated here by the query returning nothing due.
        when(sessionRepository.findWrapUpSessionIds(ORGANIZATION_ID, NOW, sinceFor(NOW))).thenReturn(List.of());

        // when
        producer.produce(NOW);

        // then
        verifyNoInteractions(port, communityAccess, factsAssembler);
    }

    @Test
    void aHeldSessionWithNoReleaseInstantNeverSends() {

        // given - permanent suppression: SessionRepository#findWrapUpSessionIds never matches a
        // wrapUpReleaseAt of null, at any distance in time, so the query itself never returns this session.
        var farFuture = NOW.plus(365, ChronoUnit.DAYS);
        when(sessionRepository.findWrapUpSessionIds(ORGANIZATION_ID, farFuture, sinceFor(farFuture)))
                .thenReturn(List.of());

        // when
        producer.produce(farFuture);

        // then
        verifyNoInteractions(port, communityAccess, factsAssembler);
    }

    /**
     * The empty-recipients check runs before fact assembly, not after: a community with no members currently costs
     * nothing beyond the membership lookup itself - no {@link SessionFactsAssembler} call, no {@code enqueue}.
     */
    @Test
    void noEnqueueOrFactAssemblyWhenTheCommunityHasNoMembers() {

        // given
        when(sessionRepository.findWrapUpSessionIds(ORGANIZATION_ID, NOW, sinceFor(NOW))).thenReturn(List.of(42L));
        when(sessionRepository.findByOrganizationIdAndId(ORGANIZATION_ID, 42L)).thenReturn(
                Optional.of(SessionEntityMother.any().id(42L).communityId(COMMUNITY_ID).status("HELD").build()));
        when(communityAccess.memberUserIds(ORGANIZATION_ID, COMMUNITY_ID)).thenReturn(List.of());

        // when
        producer.produce(NOW);

        // then
        verifyNoInteractions(port, factsAssembler);
    }

    /**
     * Proves {@code since} genuinely comes from {@link CommunicationWindows#wrapUpMaxAge()}, not a hard-coded value -
     * see {@code SessionRepositoryTest} for what the SQL floor itself excludes against a real database.
     */
    @Test
    void theSinceFloorIsComputedFromCommunicationWindowsMaxAge() {

        // given
        var narrowerMaxAge = Duration.ofDays(3);
        when(communicationWindows.wrapUpMaxAge()).thenReturn(narrowerMaxAge);
        var narrowerSince = NOW.minus(narrowerMaxAge);
        when(sessionRepository.findWrapUpSessionIds(ORGANIZATION_ID, NOW, narrowerSince)).thenReturn(List.of());

        // when
        producer.produce(NOW);

        // then - had the producer ignored CommunicationWindows and kept using the default 30-day floor, this exact
        // argument would never have been asked for and verify would fail.
        verify(sessionRepository).findWrapUpSessionIds(ORGANIZATION_ID, NOW, narrowerSince);
    }

    @Test
    void produceRefusesToRunOutsideAnOrganizationScope() {

        // given
        when(organizationResolver.currentOrganization()).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> producer.produce(NOW)).isInstanceOf(IllegalStateException.class);
    }

    /**
     * The core claim, proven at the wiring level: once {@link ProductionWatermark} answers a stored watermark rather
     * than the floor, the producer asks the repository with THAT instant, not the floor - so a session this producer
     * already claimed through on an earlier tick (simulated here by the repository returning nothing due for the stored
     * watermark) is never re-produced. {@code SessionRepositoryTest} proves the SQL boundary itself against a real
     * database; this proves the producer genuinely passes the stored watermark through rather than recomputing its own
     * window every tick.
     */
    @Test
    void aSessionReleasedBeforeTheWatermarkIsNotProducedAgain() {

        // given - a real watermark already sits ahead of the floor, simulating an earlier tick's advance
        var storedWatermark = NOW.minus(Duration.ofMinutes(5));
        when(watermark.since(PRODUCER_KEY, sinceFor(NOW))).thenReturn(storedWatermark);
        when(sessionRepository.findWrapUpSessionIds(ORGANIZATION_ID, NOW, storedWatermark)).thenReturn(List.of());

        // when
        producer.produce(NOW);

        // then
        verifyNoInteractions(port, communityAccess, factsAssembler);
    }

    /** The complementary case: a session released after the stored watermark is still produced. */
    @Test
    void aSessionReleasedAfterTheWatermarkIsProduced() {

        // given
        var storedWatermark = NOW.minus(Duration.ofMinutes(5));
        when(watermark.since(PRODUCER_KEY, sinceFor(NOW))).thenReturn(storedWatermark);
        when(sessionRepository.findWrapUpSessionIds(ORGANIZATION_ID, NOW, storedWatermark)).thenReturn(List.of(42L));
        when(sessionRepository.findByOrganizationIdAndId(ORGANIZATION_ID, 42L))
                .thenReturn(Optional.of(SessionEntityMother.any().id(42L).communityId(COMMUNITY_ID).status("HELD")
                        .wrapUpReleaseAt(NOW.minusSeconds(1)).build()));
        givenMembers("user-1");

        // when
        producer.produce(NOW);

        // then
        assertThat(captureEnqueued().subjectRef()).isEqualTo("42");
    }

    /**
     * A first run for this organization - no stored watermark row - falls back to {@code now - wrapUpMaxAge}: proven
     * here by asserting the floor the producer computes and hands to {@link ProductionWatermark#since} is exactly that,
     * independent of whatever {@code since} answers back.
     */
    @Test
    void aFirstRunWithNoWatermarkRowUsesTheMaxAgeBackstopAsItsFloor() {

        // given
        when(sessionRepository.findWrapUpSessionIds(ORGANIZATION_ID, NOW, sinceFor(NOW))).thenReturn(List.of());

        // when
        producer.produce(NOW);

        // then
        verify(watermark).since(PRODUCER_KEY, sinceFor(NOW));
    }

    @Test
    void theWatermarkAdvancesAfterASuccessfulProduceEvenWhenNothingWasDue() {

        // given
        when(sessionRepository.findWrapUpSessionIds(ORGANIZATION_ID, NOW, sinceFor(NOW))).thenReturn(List.of());

        // when
        producer.produce(NOW);

        // then - advancing must not depend on there having been anything to enqueue, or a tick that finds nothing
        // due would leave the watermark stuck, and every later tick would re-scan the same widening window.
        verify(watermark).advanceTo(PRODUCER_KEY, NOW);
    }

    /**
     * The other half of "same transaction": if the repository query itself blows up, the watermark must not advance -
     * in production this exception aborts {@code produce}'s own transaction before {@code advanceTo} is ever reached,
     * so nothing here needs to fake a rollback to prove the wiring is right, only that {@code advanceTo} is never
     * called once an earlier step has thrown.
     */
    @Test
    void theWatermarkDoesNotAdvanceWhenProduceFails() {

        // given
        when(sessionRepository.findWrapUpSessionIds(ORGANIZATION_ID, NOW, sinceFor(NOW)))
                .thenThrow(new IllegalStateException("boom"));

        // when / then
        assertThatThrownBy(() -> producer.produce(NOW)).isInstanceOf(IllegalStateException.class);
        verify(watermark, never()).advanceTo(any(), any());
    }

    /**
     * The deliberate behaviour change, asserted rather than left to be discovered: once the watermark has advanced past
     * a session's release instant, a member who joins the community afterwards receives nothing for it - even though
     * they are, at the moment of the second tick, a genuine member with every right to the roster
     * {@link CommunityAccess#memberUserIds} would otherwise return. Tick one enqueues to the founding member and
     * advances the watermark to {@code NOW}; tick two - a new member now in the roster - never even reaches the roster
     * lookup, because the repository (its own boundary proven by {@code SessionRepositoryTest}) has already stopped
     * returning this session once its due instant no longer exceeds the new watermark.
     *
     * <p>
     * This is a WIRING proof, not an end-to-end one: tick two's {@code findWrapUpSessionIds} answer is stubbed to
     * return nothing, it is not a real database enforcing the boundary. What makes that stub trustworthy rather than
     * circular is {@code SessionRepositoryTest#findWrapUpSessionIds_excludesASessionReleasedExactlyAtTheSinceFloor},
     * which proves the identical {@code (since, now]} exclusion against a real Postgres connection - this test proves
     * the producer correctly acts on that exclusion once the repository provides it, nothing more.
     */
    @Test
    void aMemberWhoJoinsAfterTheWrapUpWasReleasedReceivesNothing() {

        // given - tick one: the session is due, one founding member gets it.
        when(sessionRepository.findWrapUpSessionIds(ORGANIZATION_ID, NOW, sinceFor(NOW))).thenReturn(List.of(42L));
        when(sessionRepository.findByOrganizationIdAndId(ORGANIZATION_ID, 42L))
                .thenReturn(Optional.of(SessionEntityMother.any().id(42L).communityId(COMMUNITY_ID).status("HELD")
                        .wrapUpReleaseAt(NOW.minusSeconds(1)).build()));
        givenMembers("founding-member");

        // when - tick one
        producer.produce(NOW);

        // then - tick one enqueued to the founding member and advanced the watermark to NOW
        assertThat(captureEnqueued().recipientUserIds()).containsExactly("founding-member");
        verify(watermark).advanceTo(PRODUCER_KEY, NOW);

        // given - tick two: a new member has since joined, but the watermark (now NOW) means the SAME session no
        // longer falls in (since, tickTwo] - the repository call this simulates is exactly what
        // SessionRepositoryTest#findWrapUpSessionIds_excludesASessionReleasedExactlyAtTheSinceFloor proves for real.
        var tickTwo = NOW.plus(Duration.ofMinutes(5));
        when(watermark.since(PRODUCER_KEY, tickTwo.minus(MAX_AGE))).thenReturn(NOW);
        when(sessionRepository.findWrapUpSessionIds(ORGANIZATION_ID, tickTwo, NOW)).thenReturn(List.of());
        lenient().when(communityAccess.memberUserIds(ORGANIZATION_ID, COMMUNITY_ID))
                .thenReturn(List.of("founding-member", "late-joiner"));

        // when - tick two
        producer.produce(tickTwo);

        // then - still exactly one enqueue total, from tick one, and the roster is never even consulted a second
        // time: tick two's query already excludes the session, so it never reaches the roster lookup that would
        // have picked up the late joiner.
        verify(port, times(1)).enqueue(any());
        verify(communityAccess, times(1)).memberUserIds(anyString(), anyLong());
    }

    private void givenDueSession(long sessionId, Instant now) {

        when(sessionRepository.findWrapUpSessionIds(ORGANIZATION_ID, now, sinceFor(now)))
                .thenReturn(List.of(sessionId));
        when(sessionRepository.findByOrganizationIdAndId(ORGANIZATION_ID, sessionId))
                .thenReturn(Optional.of(SessionEntityMother.any().id(sessionId).communityId(COMMUNITY_ID).status("HELD")
                        .wrapUpReleaseAt(now.minus(1, ChronoUnit.HOURS)).build()));
    }

    private void givenMembers(String... userIds) {

        when(communityAccess.memberUserIds(ORGANIZATION_ID, COMMUNITY_ID)).thenReturn(List.of(userIds));
    }

    private static Instant sinceFor(Instant now) {

        return now.minus(MAX_AGE);
    }

    private OutboundMessage captureEnqueued() {

        var captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(port).enqueue(captor.capture());
        return captor.getValue();
    }
}
