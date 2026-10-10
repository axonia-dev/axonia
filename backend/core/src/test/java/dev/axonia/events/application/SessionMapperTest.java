package dev.axonia.events.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.communication.api.MessageOutcome;
import dev.axonia.communication.api.MessageOutcomePort;
import dev.axonia.communication.api.MessageType;
import dev.axonia.events.api.MeetingPlace;
import dev.axonia.events.api.ReleaseState;
import dev.axonia.events.api.ReleaseStatus;
import dev.axonia.events.api.Session;
import dev.axonia.events.api.SessionStatus;
import dev.axonia.events.application.port.out.AttendanceStore;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.application.port.out.SectionTypeStore;
import dev.axonia.events.application.port.out.SeriesStore;
import dev.axonia.events.domain.SectionRecord;
import dev.axonia.events.domain.SeriesRecordMother;
import dev.axonia.events.domain.SessionRecord;
import dev.axonia.events.domain.SessionRecordMother;
import dev.axonia.events.domain.SlotRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class SessionMapperTest {

    @Mock
    private SectionTypeStore sectionTypeStore;

    @Mock
    private AttendanceStore attendanceStore;

    @Mock
    private SeriesStore seriesStore;

    @Mock
    private CommunityAccess communityAccess;

    @Mock
    private MessageOutcomePort messageOutcomePort;

    private SessionMapper sut;

    @BeforeEach
    void wire() {

        sut = new SessionMapper(communityAccess, messageOutcomePort,
                new SessionBatchLookups(sectionTypeStore, attendanceStore, seriesStore));
    }

    private SessionRecord sessionWithSlot(SlotRecord slot) {

        var section = SectionRecord.builder().sectionTypeId(1L).title("Work").notes(null).slots(List.of(slot)).build();
        return SessionRecordMother.any().id(5L).sections(List.of(section)).build();
    }

    private SlotRecord linkedSlot(Long topicId) {

        return SlotRecord.builder().topicId(topicId).title("Review carbon footprint").notes(null).durationMinutes(15)
                .presenters(Set.of()).build();
    }

    private void stubReadModel() {

        when(attendanceStore.countBySessionIdIn("default", Set.of(5L))).thenReturn(List.of());
        when(sectionTypeStore.findByOrganizationIdAndIdIn("default", Set.of(1L))).thenReturn(List.of());
    }

    @Test
    void aPastSessionIsNotHeldUntilSomebodySaysSo() {

        var entity = SessionRecordMother.any().status("SCHEDULED").scheduledAt(Instant.parse("2026-01-01T10:00:00Z"))
                .build();

        assertThat(sut.isHeld(entity)).isFalse();
        assertThat(sut.toSession(entity).status()).isEqualTo(SessionStatus.SCHEDULED);
    }

    @Test
    void aStoredHeldSessionIsHeldWhateverTheClockSays() {

        var entity = SessionRecordMother.any().status("HELD").scheduledAt(Instant.parse("2099-01-01T10:00:00Z"))
                .build();

        assertThat(sut.isHeld(entity)).isTrue();
    }

    @Test
    void toSession_mapsClosedAtAndBothReleaseIntents() {

        when(attendanceStore.countBySessionIdIn("default", Set.of(5L))).thenReturn(List.of());
        var closedAt = Instant.parse("2026-01-01T10:05:00Z");
        var wrapUpReleaseAt = Instant.parse("2026-01-03T10:05:00Z");
        var upcomingReleaseAt = Instant.parse("2025-12-29T10:00:00Z");
        var entity = SessionRecordMother.any().id(5L).status("HELD").closedAt(closedAt).wrapUpReleaseAt(wrapUpReleaseAt)
                .upcomingReleaseAt(upcomingReleaseAt).sections(List.of()).build();

        var actual = sut.toSession(entity);

        assertThat(actual.closedAt()).isEqualTo(closedAt);
        assertThat(actual.wrapUpReleaseAt()).isEqualTo(wrapUpReleaseAt);
        assertThat(actual.upcomingReleaseAt()).isEqualTo(upcomingReleaseAt);
    }

    @Test
    void toSession_leavesBothReleaseStatusesNullAndTouchesNeitherOutboxNorRoster() {

        stubReadModel();
        var entity = sessionWithSlot(linkedSlot(null));

        var actual = sut.toSession(entity);

        assertThat(actual.wrapUpStatus()).isNull();
        assertThat(actual.upcomingStatus()).isNull();
        verifyNoInteractions(communityAccess, messageOutcomePort);
    }

    /**
     * A session carrying a release intent for exactly one of the two messages, so a derivation that read the wrong
     * column or asked the outbox about the wrong {@link MessageType} answers {@code OFF} rather than the expected
     * state.
     */
    private SessionRecord sessionWithRelease(MessageType type, Instant releaseAt) {

        var builder = SessionRecordMother.any().id(5L).status("HELD").sections(List.of());
        return (MessageType.SESSION_WRAP_UP.equals(type)
                ? builder.wrapUpReleaseAt(releaseAt)
                : builder.upcomingReleaseAt(releaseAt)).build();
    }

    private ReleaseStatus statusOf(MessageType type, Instant releaseAt) {

        var detail = sut.toSessionDetail(sessionWithRelease(type, releaseAt));
        return MessageType.SESSION_WRAP_UP.equals(type) ? detail.wrapUpStatus() : detail.upcomingStatus();
    }

    /**
     * The detail read resolves both message types, so a test naming one of them stubs the other as "nothing enqueued"
     * rather than leaving it unstubbed - {@code MessageOutcome} is a record, and an unstubbed strict mock hands back
     * null.
     */
    private void stubDetailReads(MessageType type, MessageOutcome outcome) {

        when(attendanceStore.countBySessionIdIn("default", Set.of(5L))).thenReturn(List.of());
        when(messageOutcomePort.outcomeFor(type, "5")).thenReturn(outcome);
        when(messageOutcomePort.outcomeFor(other(type), "5")).thenReturn(MessageOutcome.builder().build());
        when(communityAccess.memberUserIds("default", 1L)).thenReturn(List.of());
    }

    private static Stream<MessageType> releasableTypes() {

        return Stream.of(MessageType.SESSION_WRAP_UP, MessageType.SESSION_UPCOMING);
    }

    private static MessageType other(MessageType type) {

        return MessageType.SESSION_WRAP_UP.equals(type) ? MessageType.SESSION_UPCOMING : MessageType.SESSION_WRAP_UP;
    }

    @ParameterizedTest
    @MethodSource("releasableTypes")
    void toSessionDetail_off_whenNoReleaseAndNoOutboxRows(MessageType type) {

        stubDetailReads(type, MessageOutcome.builder().build());

        assertThat(statusOf(type, null).state()).isEqualTo(ReleaseState.OFF);
    }

    /**
     * The boundary that made this derivation necessary: {@code ReleasePanel} used to derive its statement from the
     * release instant alone, so one already in the past with nothing yet enqueued read as sent the instant "Send now"
     * was pressed. A clock comparison here (deriving the state from whether the instant is before now, instead of from
     * the outbox's own counts) would turn this green test red - it must stay {@code SCHEDULED}, never {@code SENT},
     * until the outbox actually has rows.
     *
     * <p>
     * It matters more for the reminder than it ever did for the wrap-up: a reminder's release instant sits ahead of a
     * session that has not happened yet, so the window between "the instant passed" and "the producer swept it" is one
     * an organizer is far more likely to be looking at the page during.
     */
    @ParameterizedTest
    @MethodSource("releasableTypes")
    void toSessionDetail_scheduled_whenReleaseIsInThePastWithNoOutboxRowsYet(MessageType type) {

        stubDetailReads(type, MessageOutcome.builder().build());

        assertThat(statusOf(type, Instant.parse("2020-01-01T00:00:00Z")).state()).isEqualTo(ReleaseState.SCHEDULED);
    }

    @ParameterizedTest
    @MethodSource("releasableTypes")
    void toSessionDetail_queued_whenAnyRowIsStillPending(MessageType type) {

        stubDetailReads(type, MessageOutcome.builder().pending(1).sent(2).build());

        assertThat(statusOf(type, Instant.parse("2020-01-01T00:00:00Z")).state()).isEqualTo(ReleaseState.QUEUED);
    }

    /**
     * {@link MessageOutcomePort#outcomeFor}'s {@code pending} count folds {@code FAILED} rows in alongside
     * {@code PENDING} and {@code SENDING} because a failed attempt is retried until it either succeeds or is marked
     * {@code ABANDONED} - never a resting place on its own. A state derivation that checked for literal
     * {@code PENDING}/{@code SENDING} instead of branching on {@code pending() > 0} would leave a session with only a
     * retryable failed row in no state at all; this pins {@code pending() > 0} alone as sufficient for {@code QUEUED}.
     */
    @ParameterizedTest
    @MethodSource("releasableTypes")
    void toSessionDetail_queued_whenOnlyARetryableFailedRowRemains(MessageType type) {

        stubDetailReads(type, MessageOutcome.builder().pending(1).build());

        assertThat(statusOf(type, Instant.parse("2020-01-01T00:00:00Z")).state()).isEqualTo(ReleaseState.QUEUED);
    }

    @ParameterizedTest
    @MethodSource("releasableTypes")
    void toSessionDetail_sent_whenRowsExistAndEveryOneIsTerminal(MessageType type) {

        stubDetailReads(type, MessageOutcome.builder().sent(3).suppressed(5).unreachable(1).abandoned(2).build());

        var status = statusOf(type, Instant.parse("2020-01-01T00:00:00Z"));

        assertThat(status.state()).isEqualTo(ReleaseState.SENT);
        assertThat(status.sent()).isEqualTo(3);
        assertThat(status.couldNotReach()).isEqualTo(3);
    }

    /**
     * {@code suppressed} counts rows the sweep deliberately withheld for governance reasons - a member's own opt-out
     * among them - and must never surface on the card: disclosing it would tell an organizer how many of their members
     * privately chose not to receive the mail. Driven to a distinct, larger count than {@code unreachable}/
     * {@code abandoned} so a regression that summed {@code suppressed} into {@code couldNotReach} (reverting to the
     * earlier mapping) would change the expected value here, not accidentally pass.
     */
    @ParameterizedTest
    @MethodSource("releasableTypes")
    void toSessionDetail_couldNotReach_neverIncludesGovernanceSuppressions(MessageType type) {

        stubDetailReads(type, MessageOutcome.builder().sent(1).suppressed(9).unreachable(1).abandoned(1).build());

        assertThat(statusOf(type, Instant.parse("2020-01-01T00:00:00Z")).couldNotReach()).isEqualTo(2);
    }

    /**
     * The worst case: a community that switches the message off inside its release window suppresses every row for
     * governance reasons, none of them {@code NO_ADDRESS}. The card must still read {@code SENT} (every row is
     * terminal), but {@code couldNotReach} must read zero, not the suppressed count - "nothing was sent and nothing
     * failed" is the honest statement, not "14 could not be reached".
     */
    @ParameterizedTest
    @MethodSource("releasableTypes")
    void toSessionDetail_allGovernanceSuppressed_readsSentWithZeroCouldNotReach(MessageType type) {

        stubDetailReads(type, MessageOutcome.builder().suppressed(14).build());

        var status = statusOf(type, Instant.parse("2020-01-01T00:00:00Z"));

        assertThat(status.state()).isEqualTo(ReleaseState.SENT);
        assertThat(status.sent()).isZero();
        assertThat(status.couldNotReach()).isZero();
    }

    /**
     * The roster answers "how many members would this reach today" for both messages at once, so the detail read
     * resolves it once and hands the same number to each status. Pinned with {@code times(1)}: a derivation that
     * resolved the roster per message type would double a cross-context read on every session detail page.
     */
    @Test
    void toSessionDetail_recipientCountComesFromOneCommunityMembershipReadSharedByBothStatuses() {

        when(attendanceStore.countBySessionIdIn("default", Set.of(5L))).thenReturn(List.of());
        when(messageOutcomePort.outcomeFor(any(), eq("5"))).thenReturn(MessageOutcome.builder().build());
        when(communityAccess.memberUserIds("default", 1L)).thenReturn(List.of("alice", "bob", "carol"));

        var actual = sut.toSessionDetail(sessionWithRelease(MessageType.SESSION_WRAP_UP, null));

        assertThat(actual.wrapUpStatus().recipientCount()).isEqualTo(3);
        assertThat(actual.upcomingStatus().recipientCount()).isEqualTo(3);
        verify(communityAccess, times(1)).memberUserIds("default", 1L);
    }

    /**
     * The two intents are read from their own columns and asked of their own message type. A single session carrying a
     * released wrap-up and a suppressed reminder must not report one state twice.
     */
    @Test
    void toSessionDetail_readsEachMessageTypeFromItsOwnIntentAndItsOwnOutboxRows() {

        when(attendanceStore.countBySessionIdIn("default", Set.of(5L))).thenReturn(List.of());
        when(messageOutcomePort.outcomeFor(MessageType.SESSION_WRAP_UP, "5"))
                .thenReturn(MessageOutcome.builder().sent(4).build());
        when(messageOutcomePort.outcomeFor(MessageType.SESSION_UPCOMING, "5"))
                .thenReturn(MessageOutcome.builder().build());
        when(communityAccess.memberUserIds("default", 1L)).thenReturn(List.of());
        var entity = SessionRecordMother.any().id(5L).status("HELD")
                .wrapUpReleaseAt(Instant.parse("2026-01-03T10:05:00Z")).upcomingReleaseAt(null).sections(List.of())
                .build();

        var actual = sut.toSessionDetail(entity);

        assertThat(actual.wrapUpStatus().state()).isEqualTo(ReleaseState.SENT);
        assertThat(actual.wrapUpStatus().sent()).isEqualTo(4);
        assertThat(actual.upcomingStatus().state()).isEqualTo(ReleaseState.OFF);
        assertThat(actual.upcomingStatus().sent()).isZero();
    }

    private static final String STANDING_ROOM = "https://meet.example/dojo";
    private static final String OWN_ROOM = "https://meet.example/this-week";

    private void standingPlaceOnSeriesOne() {

        when(seriesStore.findByOrganizationIdAndIdIn("default", Set.of(1L)))
                .thenReturn(List.of(SeriesRecordMother.any().id(1L).joinUrl(STANDING_ROOM).build()));
    }

    private static String joinUrlOf(Session session) {

        return session.place().joinUrl();
    }

    @Test
    void toSession_reportsTheSeriesStandingPlaceWhenTheSessionSetsNone() {

        when(attendanceStore.countBySessionIdIn("default", Set.of(5L))).thenReturn(List.of());
        standingPlaceOnSeriesOne();

        assertThat(joinUrlOf(sut.toSession(SessionRecordMother.any().id(5L).build()))).isEqualTo(STANDING_ROOM);
    }

    @Test
    void toSession_reportsTheSessionsOwnPlaceOverTheSeriesStandingPlace() {

        when(attendanceStore.countBySessionIdIn("default", Set.of(5L))).thenReturn(List.of());
        standingPlaceOnSeriesOne();

        assertThat(joinUrlOf(sut.toSession(SessionRecordMother.any().id(5L).joinUrl(OWN_ROOM).build())))
                .isEqualTo(OWN_ROOM);
    }

    /** A one-off belongs to no series, so there is nothing to inherit from and nothing to ask about. */
    @Test
    void toSession_aSessionWithoutASeriesInheritsNothingAndAsksNothing() {

        when(attendanceStore.countBySessionIdIn("default", Set.of(5L))).thenReturn(List.of());

        assertThat(sut.toSession(SessionRecordMother.any().id(5L).seriesId(null).build()).place())
                .isEqualTo(MeetingPlace.NONE);
        verifyNoInteractions(seriesStore);
    }

    @Test
    void toSession_aSeriesThatCannotBeReadLeavesTheSessionPlaceless() {

        when(attendanceStore.countBySessionIdIn("default", Set.of(5L))).thenReturn(List.of());

        assertThat(sut.toSession(SessionRecordMother.any().id(5L).build()).place()).isEqualTo(MeetingPlace.NONE);
    }

    /**
     * An empty batch asks the database nothing at all. It used to be reached through the list paths themselves - an
     * empty page of sessions was mapped like any other - but those now short-circuit before the mapper, in
     * {@code SessionListing}, so the only thing left holding {@code attendeeCountsOf}'s empty guard honest is this.
     * Without the guard the batch would read {@code entities.getFirst()} to find its organization and throw.
     */
    @Test
    void toSessions_anEmptyBatchReadsNothing() {

        assertThat(sut.toSessions(List.of())).isEmpty();
        verifyNoInteractions(attendanceStore, seriesStore, sectionTypeStore);
    }

    /**
     * The batch read the schedule and both session lists go through: every place in the page is resolved from a single
     * series query. The {@code never()} is the point of the test - one series read per row would be an N+1 that no
     * assertion about the places themselves could ever catch, since row-by-row resolution answers correctly too.
     */
    @Test
    void toSessions_resolvesEveryPlaceFromOneSeriesRead() {

        when(attendanceStore.countBySessionIdIn(eq("default"), any())).thenReturn(List.of());
        when(seriesStore.findByOrganizationIdAndIdIn("default", Set.of(1L)))
                .thenReturn(List.of(SeriesRecordMother.any().id(1L).joinUrl(STANDING_ROOM).build()));

        var actual = sut.toSessions(List.of(SessionRecordMother.any().id(5L).build(),
                SessionRecordMother.any().id(6L).build(), SessionRecordMother.any().id(7L).joinUrl(OWN_ROOM).build()));

        assertThat(actual).extracting(SessionMapperTest::joinUrlOf).containsExactly(STANDING_ROOM, STANDING_ROOM,
                OWN_ROOM);
        verify(seriesStore, never()).findByOrganizationIdAndId(any(), any());
    }

    /**
     * The governing zone rides back on the same series read the standing place comes from, on the batch path as well as
     * the single one. A schedule page mixes both kinds of row, and the schedule dialog keys on exactly the difference:
     * a series' occurrence is shown and edited in the zone its community chose, and a one-off - which has no series and
     * so no such zone - falls back to the viewer's own. Null is the answer there rather than a gap.
     */
    @Test
    void toSessions_carriesEachRowsGoverningZoneAndNullForAOneOff() {

        when(attendanceStore.countBySessionIdIn(eq("default"), any())).thenReturn(List.of());
        when(seriesStore.findByOrganizationIdAndIdIn("default", Set.of(1L)))
                .thenReturn(List.of(SeriesRecordMother.any().id(1L).timeZone("Europe/Paris").build()));

        var actual = sut.toSessions(List.of(SessionRecordMother.any().id(5L).build(),
                SessionRecordMother.any().id(6L).seriesId(null).build()));

        assertThat(actual).extracting(Session::seriesTimeZone).containsExactly("Europe/Paris", null);
        verify(seriesStore, never()).findByOrganizationIdAndId(any(), any());
    }

    @Test
    void toSessions_aPageOfOneOffsAsksAboutNoSeriesAtAll() {

        when(attendanceStore.countBySessionIdIn(eq("default"), any())).thenReturn(List.of());

        var actual = sut.toSessions(List.of(SessionRecordMother.any().id(5L).seriesId(null).build(),
                SessionRecordMother.any().id(6L).seriesId(null).joinUrl(OWN_ROOM).build()));

        assertThat(actual).extracting(SessionMapperTest::joinUrlOf).containsExactly(null, OWN_ROOM);
        verifyNoInteractions(seriesStore);
    }

    @Test
    void toSession_carriesTheRowsVersionSoAnUpdatedOccurrenceCanAdvanceItsCalendarSequence() {

        when(attendanceStore.countBySessionIdIn("default", Set.of(5L))).thenReturn(List.of());

        assertThat(sut.toSession(SessionRecordMother.any().id(5L).seriesId(null).version(4L).build()).version())
                .isEqualTo(4L);
    }

    /**
     * The resolved place answers "where does this meet", which is the wrong question for an edit form: a form seeded
     * from it cannot tell a session that inherits from one that overrides, so saving it untouched would write the
     * inherited place back as this occurrence's own.
     */
    @Test
    void toSession_reportsAnInheritedPlaceAsNoOverrideOfItsOwn() {

        when(attendanceStore.countBySessionIdIn("default", Set.of(5L))).thenReturn(List.of());
        standingPlaceOnSeriesOne();

        var actual = sut.toSession(SessionRecordMother.any().id(5L).build());

        assertThat(actual.place().joinUrl()).isEqualTo(STANDING_ROOM);
        assertThat(actual.placeOverride()).isEqualTo(MeetingPlace.NONE);
        assertThat(actual.seriesPlace().joinUrl()).isEqualTo(STANDING_ROOM);
    }

    /** An override hides the standing place from {@code place}, so the place it would fall back to is said apart. */
    @Test
    void toSession_reportsTheOverrideBesideThePlaceItWouldFallBackTo() {

        when(attendanceStore.countBySessionIdIn("default", Set.of(5L))).thenReturn(List.of());
        standingPlaceOnSeriesOne();

        var actual = sut.toSession(SessionRecordMother.any().id(5L).joinUrl(OWN_ROOM).build());

        assertThat(actual.place().joinUrl()).isEqualTo(OWN_ROOM);
        assertThat(actual.placeOverride().joinUrl()).isEqualTo(OWN_ROOM);
        assertThat(actual.seriesPlace().joinUrl()).isEqualTo(STANDING_ROOM);
    }

    @Test
    void toSession_aBlankOverrideIsNoOverride() {

        when(attendanceStore.countBySessionIdIn("default", Set.of(5L))).thenReturn(List.of());
        standingPlaceOnSeriesOne();

        var actual = sut.toSession(SessionRecordMother.any().id(5L).joinUrl("").build());

        assertThat(actual.placeOverride()).isEqualTo(MeetingPlace.NONE);
        assertThat(actual.place().joinUrl()).isEqualTo(STANDING_ROOM);
    }

    @Test
    void toSession_aBlankStandingPlaceIsNoPlaceToFallBackTo() {

        when(attendanceStore.countBySessionIdIn("default", Set.of(5L))).thenReturn(List.of());
        when(seriesStore.findByOrganizationIdAndIdIn("default", Set.of(1L)))
                .thenReturn(List.of(SeriesRecordMother.any().id(1L).joinUrl("").build()));

        var actual = sut.toSession(SessionRecordMother.any().id(5L).build());

        assertThat(actual.seriesPlace()).isEqualTo(MeetingPlace.NONE);
        assertThat(actual.place()).isEqualTo(MeetingPlace.NONE);
    }
}
