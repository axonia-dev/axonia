package dev.axonia.events.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class AwaitingCloseProducerTest {

    private static final Instant NOW = Instant.parse("2026-06-08T00:00:00Z");
    private static final String ORGANIZATION_ID = "acme";
    private static final Long COMMUNITY_ID = 7L;
    private static final Instant ONE_DAY_THRESHOLD = NOW.minus(1, ChronoUnit.DAYS);
    private static final Instant SEVEN_DAY_THRESHOLD = NOW.minus(7, ChronoUnit.DAYS);
    private static final Duration MAX_AGE = Duration.ofDays(30);
    private static final Instant SINCE = NOW.minus(MAX_AGE);
    private static final String ONE_DAY_KEY = "session-awaiting-close-1d";
    private static final String SEVEN_DAY_KEY = "session-awaiting-close-7d";

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
    private AwaitingCloseProducer producer;

    @BeforeEach
    void seedOrganization() {

        when(organizationResolver.currentOrganization()).thenReturn(Optional.of(ORGANIZATION_ID));
        lenient().when(communicationWindows.awaitingCloseMaxAge()).thenReturn(MAX_AGE);
        // Default: pass the floor straight through, as if no watermark row exists yet for either arm. Lenient: the
        // one test that stubs organizationResolver to fail never reaches this call.
        lenient().when(watermark.since(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        // Neither arm finds anything by default; each test opts a specific arm in. Lenient: the one test that
        // stubs organizationResolver to fail never reaches either repository call, which would otherwise make
        // these two default stubs unused stubbings under strict Mockito.
        lenient().when(sessionRepository.findOverdueUnclosedSessionIds(ORGANIZATION_ID, 1, SINCE, NOW))
                .thenReturn(List.of());
        lenient().when(sessionRepository.findOverdueUnclosedSessionIds(ORGANIZATION_ID, 7, SINCE, NOW))
                .thenReturn(List.of());
    }

    @Test
    void anUnansweredSessionNudgesTheOrganizersAtOneDay() {

        // given - one day past scheduledAt, not yet seven: only the one-day arm is due.
        when(sessionRepository.findOverdueUnclosedSessionIds(ORGANIZATION_ID, 1, SINCE, NOW)).thenReturn(List.of(42L));
        when(sessionRepository.findByOrganizationIdAndId(ORGANIZATION_ID, 42L))
                .thenReturn(Optional.of(SessionEntityMother.any().id(42L).communityId(COMMUNITY_ID)
                        .scheduledAt(ONE_DAY_THRESHOLD).facilitatorUserId(null).build()));
        givenOrganizers("lead-1");

        // when
        producer.produce(NOW);

        // then
        var message = captureEnqueued();
        assertThat(message.type()).isEqualTo(MessageType.SESSION_AWAITING_CLOSE);
        assertThat(message.subjectRef()).isEqualTo("42");
        assertThat(message.recipientUserIds()).containsExactly("lead-1");
        assertThat(message.template()).isEqualTo("session-awaiting-close");
    }

    /**
     * The complementary case to the test above: once a session is seven days overdue, the second arm fires too, under a
     * distinct {@code subjectRef} - {@code "42:7d"} - so it does not collide with the one-day nudge's own row under the
     * outbox's {@code (organization_id, message_type, subject_ref)} unique key. {@link MessageSubjectPolicyAdapter}
     * parses a {@code subjectRef} up to its first {@code :}, exactly matching this suffix.
     */
    @Test
    void aSessionSevenDaysOverdueAlsoGetsTheSecondNudgeUnderItsOwnSubjectRef() {

        // given - both thresholds match: still unclosed, seven days past scheduledAt.
        when(sessionRepository.findOverdueUnclosedSessionIds(ORGANIZATION_ID, 1, SINCE, NOW)).thenReturn(List.of(42L));
        when(sessionRepository.findOverdueUnclosedSessionIds(ORGANIZATION_ID, 7, SINCE, NOW)).thenReturn(List.of(42L));
        when(sessionRepository.findByOrganizationIdAndId(ORGANIZATION_ID, 42L))
                .thenReturn(Optional.of(SessionEntityMother.any().id(42L).communityId(COMMUNITY_ID)
                        .scheduledAt(SEVEN_DAY_THRESHOLD).facilitatorUserId(null).build()));
        givenOrganizers("lead-1");

        // when
        producer.produce(NOW);

        // then - one call per arm, distinct subjectRefs
        var captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(port, times(2)).enqueue(captor.capture());
        assertThat(captor.getAllValues()).extracting(OutboundMessage::subjectRef).containsExactly("42", "42:7d");
    }

    /**
     * Which of the two arms produced a row is known at production, never changes afterwards, and is therefore the one
     * thing about a nudge's age that is safe to freeze into a fact. A relative time would not be: facts are frozen in
     * the outbox at enqueue and rendered arbitrarily later, so a stored "2 days ago" would still read "2 days ago" when
     * an organizer previews the row a week on.
     *
     * <p>
     * Drives both arms through {@code produce} the same way
     * {@link #aSessionSevenDaysOverdueAlsoGetsTheSecondNudgeUnderItsOwnSubjectRef} does - both thresholds match, so the
     * same overdue session triggers both firings - then stubs {@link SessionFactsAssembler#factsForAwaitingClose} per
     * arm, since it is a mock with no default return.
     */
    @Test
    void eachArmStampsItsOwnNudgeArmFact() {

        // given - both thresholds match: still unclosed, seven days past scheduledAt.
        var entity = SessionEntityMother.any().id(42L).communityId(COMMUNITY_ID).scheduledAt(SEVEN_DAY_THRESHOLD)
                .facilitatorUserId(null).build();
        when(sessionRepository.findOverdueUnclosedSessionIds(ORGANIZATION_ID, 1, SINCE, NOW)).thenReturn(List.of(42L));
        when(sessionRepository.findOverdueUnclosedSessionIds(ORGANIZATION_ID, 7, SINCE, NOW)).thenReturn(List.of(42L));
        when(sessionRepository.findByOrganizationIdAndId(ORGANIZATION_ID, 42L)).thenReturn(Optional.of(entity));
        when(factsAssembler.factsForAwaitingClose(ORGANIZATION_ID, entity, "ONE_DAY"))
                .thenReturn(Map.of("nudgeArm", "ONE_DAY"));
        when(factsAssembler.factsForAwaitingClose(ORGANIZATION_ID, entity, "SEVEN_DAY"))
                .thenReturn(Map.of("nudgeArm", "SEVEN_DAY"));
        givenOrganizers("lead-1");

        // when
        producer.produce(NOW);

        // then

        var enqueued = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(port, atLeastOnce()).enqueue(enqueued.capture());

        assertThat(enqueued.getAllValues()).extracting(message -> message.facts().get("nudgeArm"))
                .containsExactly("ONE_DAY", "SEVEN_DAY");
    }

    @Test
    void theFacilitatorIsNudgedAlongsideTheOrganizersWithoutDuplication() {

        // given - the facilitator is also an organizer; must appear exactly once.
        when(sessionRepository.findOverdueUnclosedSessionIds(ORGANIZATION_ID, 1, SINCE, NOW)).thenReturn(List.of(42L));
        when(sessionRepository.findByOrganizationIdAndId(ORGANIZATION_ID, 42L))
                .thenReturn(Optional.of(SessionEntityMother.any().id(42L).communityId(COMMUNITY_ID)
                        .scheduledAt(ONE_DAY_THRESHOLD).facilitatorUserId("lead-1").build()));
        givenOrganizers("lead-1", "lead-2");

        // when
        producer.produce(NOW);

        // then
        var message = captureEnqueued();
        assertThat(message.recipientUserIds()).containsExactlyInAnyOrder("lead-1", "lead-2");
    }

    @Test
    void noEnqueueWhenNothingIsOverdue() {

        // when
        producer.produce(NOW);

        // then
        verifyNoInteractions(port, communityAccess, factsAssembler);
    }

    @Test
    void noEnqueueWhenThereIsNobodyToNudge() {

        // given - no organizers left, no facilitator assigned: nobody to send to.
        when(sessionRepository.findOverdueUnclosedSessionIds(ORGANIZATION_ID, 1, SINCE, NOW)).thenReturn(List.of(42L));
        when(sessionRepository.findByOrganizationIdAndId(ORGANIZATION_ID, 42L))
                .thenReturn(Optional.of(SessionEntityMother.any().id(42L).communityId(COMMUNITY_ID)
                        .scheduledAt(ONE_DAY_THRESHOLD).facilitatorUserId(null).build()));
        when(communityAccess.organizerUserIds(ORGANIZATION_ID, COMMUNITY_ID)).thenReturn(List.of());

        // when
        producer.produce(NOW);

        // then
        verify(port, never()).enqueue(any());
    }

    /**
     * Proves {@code since} genuinely comes from {@link CommunicationWindows#awaitingCloseMaxAge()}, not a hard-coded
     * value: a different max age computes a different floor, and the repository is queried with that floor - not the
     * fixture's default {@link #SINCE}. See {@code SessionRepositoryTest} for what the SQL floor itself excludes
     * against a real database.
     */
    @Test
    void theSinceFloorIsComputedFromCommunicationWindowsMaxAge() {

        // given
        var narrowerMaxAge = Duration.ofDays(3);
        when(communicationWindows.awaitingCloseMaxAge()).thenReturn(narrowerMaxAge);
        var narrowerSince = NOW.minus(narrowerMaxAge);
        when(sessionRepository.findOverdueUnclosedSessionIds(ORGANIZATION_ID, 1, narrowerSince, NOW))
                .thenReturn(List.of());
        when(sessionRepository.findOverdueUnclosedSessionIds(ORGANIZATION_ID, 7, narrowerSince, NOW))
                .thenReturn(List.of());

        // when
        producer.produce(NOW);

        // then - both arms were asked with the narrower floor; had the producer ignored CommunicationWindows and
        // kept using the fixture's default SINCE, neither stub above would match and Mockito would return the
        // unstubbed default (empty list) regardless - verify pins the exact argument, not just the outcome.
        verify(sessionRepository).findOverdueUnclosedSessionIds(ORGANIZATION_ID, 1, narrowerSince, NOW);
        verify(sessionRepository).findOverdueUnclosedSessionIds(ORGANIZATION_ID, 7, narrowerSince, NOW);
    }

    /**
     * The core claim for this producer's two arms: once {@link ProductionWatermark} answers a stored watermark rather
     * than the floor, each arm asks the repository with its OWN stored value - a session either arm already claimed
     * through on an earlier tick is never re-nudged.
     */
    @Test
    void aSessionOverdueBeforeAnArmsWatermarkIsNotNudgedAgainByThatArm() {

        // given - the one-day arm's watermark has already advanced past this tick's window; the seven-day arm's
        // has not.
        var oneDayWatermark = NOW.minus(Duration.ofMinutes(5));
        when(watermark.since(ONE_DAY_KEY, SINCE)).thenReturn(oneDayWatermark);
        when(sessionRepository.findOverdueUnclosedSessionIds(ORGANIZATION_ID, 1, oneDayWatermark, NOW))
                .thenReturn(List.of());
        when(sessionRepository.findOverdueUnclosedSessionIds(ORGANIZATION_ID, 7, SINCE, NOW)).thenReturn(List.of(42L));
        when(sessionRepository.findByOrganizationIdAndId(ORGANIZATION_ID, 42L))
                .thenReturn(Optional.of(SessionEntityMother.any().id(42L).communityId(COMMUNITY_ID)
                        .scheduledAt(SEVEN_DAY_THRESHOLD).facilitatorUserId(null).build()));
        givenOrganizers("lead-1");

        // when
        producer.produce(NOW);

        // then - only the seven-day arm's nudge landed
        var message = captureEnqueued();
        assertThat(message.subjectRef()).isEqualTo("42:7d");
    }

    @Test
    void bothArmsWatermarksAdvanceOnASuccessfulProduce() {

        // when
        producer.produce(NOW);

        // then - advancing does not depend on either arm having found anything due.
        verify(watermark).advanceTo(ONE_DAY_KEY, NOW);
        verify(watermark).advanceTo(SEVEN_DAY_KEY, NOW);
    }

    @Test
    void neitherArmsWatermarkAdvancesWhenProduceFails() {

        // given
        when(sessionRepository.findOverdueUnclosedSessionIds(ORGANIZATION_ID, 1, SINCE, NOW))
                .thenThrow(new IllegalStateException("boom"));

        // when / then
        assertThatThrownBy(() -> producer.produce(NOW)).isInstanceOf(IllegalStateException.class);
        verify(watermark, never()).advanceTo(any(), any());
    }

    @Test
    void produceRefusesToRunOutsideAnOrganizationScope() {

        // given
        when(organizationResolver.currentOrganization()).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> producer.produce(NOW)).isInstanceOf(IllegalStateException.class);
    }

    private void givenOrganizers(String... userIds) {

        when(communityAccess.organizerUserIds(ORGANIZATION_ID, COMMUNITY_ID)).thenReturn(List.of(userIds));
    }

    private OutboundMessage captureEnqueued() {

        var captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(port).enqueue(captor.capture());
        return captor.getValue();
    }
}
