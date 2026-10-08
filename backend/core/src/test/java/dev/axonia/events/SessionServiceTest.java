package dev.axonia.events;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import dev.axonia.ConflictException;
import dev.axonia.ForbiddenException;
import dev.axonia.NotFoundException;
import dev.axonia.UsageCounters;
import dev.axonia.communication.MessageOutcome;
import dev.axonia.communication.MessageOutcomePort;
import dev.axonia.communication.MessageType;
import dev.axonia.community.CommunicationSettings;
import dev.axonia.community.MemberMatch;
import dev.axonia.events.internal.ActionItemRepository;
import dev.axonia.events.internal.AttendanceRepository;
import dev.axonia.events.internal.CheckpointDecisionPass;
import dev.axonia.events.internal.FacilitatorResolver;
import dev.axonia.events.internal.PastSessionsQuery;
import dev.axonia.events.internal.SectionEntity;
import dev.axonia.events.internal.SectionTypeCatalog;
import dev.axonia.events.internal.SectionTypeRef;
import dev.axonia.events.internal.SectionTypeRepository;
import dev.axonia.events.internal.SeriesEntity;
import dev.axonia.events.internal.SeriesRepository;
import dev.axonia.events.internal.SessionAttendeeCount;
import dev.axonia.events.internal.SessionAuthorizer;
import dev.axonia.events.internal.SessionBatchLookups;
import dev.axonia.events.internal.SessionCreation;
import dev.axonia.events.internal.SessionEntity;
import dev.axonia.events.internal.SessionInstant;
import dev.axonia.events.internal.SessionListReader;
import dev.axonia.events.internal.SessionMapper;
import dev.axonia.events.internal.SessionPageQuery;
import dev.axonia.events.internal.SessionRepository;
import dev.axonia.events.internal.SlotEntity;
import dev.axonia.events.internal.TopicEntity;
import dev.axonia.events.internal.TopicRepository;
import dev.axonia.events.internal.UpcomingReleasePolicy;
import dev.axonia.events.mother.SectionTypeEntityMother;
import dev.axonia.events.mother.SeriesEntityMother;
import dev.axonia.events.mother.SessionEntityMother;
import dev.axonia.events.mother.TopicEntityMother;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class SessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-10T09:00:00Z");

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private SeriesRepository seriesRepository;
    @Mock
    private TopicRepository topicRepository;
    @Mock
    private ActionItemRepository actionItemRepository;
    @Mock
    private CheckpointDecisionPass checkpointDecisionPass;
    @Mock
    private SectionTypeRepository sectionTypeRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private SessionListReader sessionListReader;
    @Mock
    private SessionAuthorizer sessionAuthorizer;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private MessageOutcomePort messageOutcomePort;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    private final UsageCounters usageCounters = new UsageCounters(meters);

    private SessionService sut;

    /**
     * The real mapper the service used to run its list rows through. {@code past} and {@code upcoming} now read through
     * {@link SessionListReader}, which does that mapping itself, so the three tests below stub the reader with this
     * same mapper's output rather than with hand-built DTOs: what the service is handed is still exactly what a seeded
     * row maps to, and a mapping regression still fails here.
     */
    private SessionMapper mapper;

    @BeforeEach
    void wire() {

        mapper = new SessionMapper(communityAccess, messageOutcomePort,
                new SessionBatchLookups(sectionTypeRepository, attendanceRepository, seriesRepository));
        // the real SessionCreation and the real UpcomingReleasePolicy, not mocks: these tests are the proof that both
        // creation paths route their draft row through the release intent choke point rather than saving one that never
        // met it, and that update recomputes the reschedule rule from that same arithmetic
        var upcomingReleasePolicy = new UpcomingReleasePolicy(communityAccess, clock);
        var sectionTypeCatalog = new SectionTypeCatalog(sectionTypeRepository);
        var facilitatorResolver = new FacilitatorResolver(communityAccess);
        sut = new SessionService(sessionRepository, seriesRepository, topicRepository, actionItemRepository,
                checkpointDecisionPass, mapper, sessionListReader, new SessionCreation(upcomingReleasePolicy),
                sessionAuthorizer, communityAccess, currentUser, organizationContext, eventPublisher, clock,
                upcomingReleasePolicy, new SessionInstant(seriesRepository), usageCounters, sectionTypeCatalog,
                facilitatorResolver);
    }

    @BeforeEach
    void resolvesAnyFacilitatorReferenceToItself() {

        // A default pass-through: most tests here pass an existing user id as facilitatorUserId and are not
        // exercising member resolution itself, so resolving it to that same id keeps them green. Tests that DO care
        // about resolution (a name that fails to resolve, one that resolves to someone else) stub the exact query
        // themselves, which - stubbed after this default - wins.
        lenient().when(communityAccess.resolveMember(anyLong(), any()))
                .thenAnswer(inv -> new MemberMatch.Matched(inv.getArgument(1)));
    }

    /**
     * An instant, restated the way a caller now states one: its own wall clock, read in UTC. Every session in this
     * class belongs to a series that meets in UTC, so composing this back in the governing zone lands on exactly the
     * instant it came from and every expectation below is the instant it always was. Which zone actually governs when
     * the two disagree is proved against a community that does not meet in UTC, in {@link SessionRescheduleZoneTest}.
     */
    private static ScheduledAt stated(Instant instant) {

        var utc = instant.atZone(ZoneOffset.UTC);
        return new ScheduledAt(utc.toLocalDate(), utc.toLocalTime(), "UTC");
    }

    /**
     * The series a mother-built session belongs to, whose zone governs how a reschedule's wall clock is read. It is the
     * same lookup {@link SessionMapper} already makes for the standing place, so one stub serves both.
     */
    private void givenTheSeriesMeetsIn(String timeZone) {

        when(seriesRepository.findByOrganizationIdAndId("default", 1L))
                .thenReturn(Optional.of(SeriesEntityMother.any().id(1L).timeZone(timeZone).build()));
    }

    /** The community's announcement lead time, for the creation paths that stamp a default upcoming release. */
    private void leadDays(long communityId, int days) {

        when(communityAccess.communicationSettings("default", communityId))
                .thenReturn(new CommunicationSettings(true, true, days, 24));
    }

    private void talk(Long id) {

        when(sectionTypeRepository.findByOrganizationIdAndId(eq("default"), eq(id)))
                .thenReturn(Optional.of(SectionTypeEntityMother.any().id(id).label("Talk").build()));
    }

    /** An active series a member may open, with no row at the anchor yet: what a fresh materialisation needs. */
    private void givenAnEnsurableSeries(SeriesEntity series, LocalDate anchor) {

        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("dev-user");
        when(communityAccess.isMember(1L, "dev-user")).thenReturn(true);
        when(sessionRepository.findByOrganizationIdAndSeriesIdAndOccurrenceDate("default", 5L, anchor))
                .thenReturn(Optional.empty());
        leadDays(1L, 3);
        when(sessionRepository.save(any()))
                .thenAnswer(inv -> ((SessionEntity) inv.getArgument(0)).toBuilder().id(99L).build());
    }

    private void givenScheduledPastSession(Long id) {

        var entity = SessionEntityMother.any().id(id).communityId(7L).status("SCHEDULED")
                .scheduledAt(NOW.minus(1, ChronoUnit.HOURS)).sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", id)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
    }

    private void givenScheduledFutureSession(Long id) {

        var entity = SessionEntityMother.any().id(id).communityId(7L).status("SCHEDULED")
                .scheduledAt(NOW.plus(1, ChronoUnit.HOURS)).sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", id)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
    }

    private void givenHeldSession(Long id) {

        var entity = SessionEntityMother.any().id(id).communityId(7L).status("HELD")
                .scheduledAt(NOW.minus(1, ChronoUnit.HOURS)).closedAt(NOW.minus(1, ChronoUnit.HOURS))
                .wrapUpReleaseAt(NOW.plus(47, ChronoUnit.HOURS)).sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", id)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
    }

    /**
     * {@code close} and {@code update} both return {@link SessionMapper#toSessionDetail}, which resolves both release
     * statuses through {@code communityAccess} and {@code messageOutcomePort} - stubbed here as "nobody enqueued,
     * nobody home" so tests asserting on the persisted entity, not the card, do not also have to reason about those
     * reads.
     */
    private void stubNoReleaseActivity(Long sessionId, Long communityId) {

        when(communityAccess.memberUserIds("default", communityId)).thenReturn(List.of());
        when(messageOutcomePort.outcomeFor(any(), eq(String.valueOf(sessionId))))
                .thenReturn(MessageOutcome.builder().build());
    }

    /**
     * A session cancelled through the dedicated {@code /cancel} endpoint: {@code status = CANCELLED} but
     * {@code closedAt} stays null, exactly as {@link SessionService#cancel} leaves it and as every row cancelled before
     * the V26 backfill still sits in the database.
     */
    private void givenCancelledSession(Long id) {

        var entity = SessionEntityMother.any().id(id).communityId(7L).status("CANCELLED")
                .scheduledAt(NOW.minus(1, ChronoUnit.HOURS)).sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", id)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
    }

    @Test
    void materialize_createsSectionsFromDefaultStructureWhenNonePending() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).startsOn(LocalDate.parse("2026-06-03"))
                .defaultStructure(List
                        .of(SectionTypeRef.builder().sectionTypeId(1L).title("Main talk").durationMinutes(30).build()))
                .build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("dev-user");
        when(communityAccess.isMember(1L, "dev-user")).thenReturn(true);
        when(sessionRepository.findByOrganizationIdAndSeriesIdAndOccurrenceDate(eq("default"), eq(5L), any()))
                .thenReturn(Optional.empty());
        leadDays(1L, 3);
        when(sessionRepository.save(any()))
                .thenAnswer(inv -> ((SessionEntity) inv.getArgument(0)).toBuilder().id(99L).build());

        var actual = sut.materialize(5L);

        assertThat(actual.id()).isEqualTo(99L);
        assertThat(actual.status()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(actual.sections()).hasSize(1);
        assertThat(actual.sections().get(0).sectionTypeId()).isEqualTo(1L);
        assertThat(actual.sections().get(0).title()).isEqualTo("Main talk");
        assertThat(actual.sections().get(0).durationMinutes()).isEqualTo(30);
        assertThat(actual.sections().get(0).slots()).isEmpty();
    }

    @Test
    void materialize_nullStructureCreatesNoSections() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).startsOn(LocalDate.parse("2026-06-03"))
                .defaultStructure(null).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("dev-user");
        when(communityAccess.isMember(1L, "dev-user")).thenReturn(true);
        when(sessionRepository.findByOrganizationIdAndSeriesIdAndOccurrenceDate(eq("default"), eq(5L), any()))
                .thenReturn(Optional.empty());
        leadDays(1L, 3);
        when(sessionRepository.save(any()))
                .thenAnswer(inv -> ((SessionEntity) inv.getArgument(0)).toBuilder().id(99L).build());

        var actual = sut.materialize(5L);

        assertThat(actual.sections()).isEmpty();
    }

    @Test
    void materialize_returnsExistingPending() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).defaultStructure(List.of()).build();
        var pending = SessionEntityMother.any().id(42L).seriesId(5L).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("dev-user");
        when(communityAccess.isMember(1L, "dev-user")).thenReturn(true);
        when(sessionRepository.findByOrganizationIdAndSeriesIdAndOccurrenceDate(eq("default"), eq(5L), any()))
                .thenReturn(Optional.of(pending));
        when(attendanceRepository.countBySessionIdIn("default", Set.of(42L)))
                .thenReturn(List.of(new SessionAttendeeCount(42L, 3L)));

        var actual = sut.materialize(5L);

        assertThat(actual.id()).isEqualTo(42L);
        assertThat(actual.attendeeCount()).isEqualTo(3L);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void materialize_recoversFromSlotRaceViaFindPending() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).startsOn(LocalDate.parse("2026-06-03"))
                .defaultStructure(List.of()).build();
        var winner = SessionEntityMother.any().id(77L).seriesId(5L).sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("dev-user");
        when(communityAccess.isMember(1L, "dev-user")).thenReturn(true);
        when(sessionRepository.findByOrganizationIdAndSeriesIdAndOccurrenceDate(eq("default"), eq(5L), any()))
                .thenReturn(Optional.empty()).thenReturn(Optional.of(winner));
        leadDays(1L, 3);
        when(sessionRepository.save(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup slot"));

        var actual = sut.materialize(5L);

        assertThat(actual.id()).isEqualTo(77L);
    }

    @Test
    void materialize_raceWithoutRecoveryRethrows() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).startsOn(LocalDate.parse("2026-06-03"))
                .defaultStructure(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("dev-user");
        when(communityAccess.isMember(1L, "dev-user")).thenReturn(true);
        when(sessionRepository.findByOrganizationIdAndSeriesIdAndOccurrenceDate(eq("default"), eq(5L), any()))
                .thenReturn(Optional.empty());
        leadDays(1L, 3);
        when(sessionRepository.save(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup slot"));

        assertThatThrownBy(() -> sut.materialize(5L))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void materialize_seriesNotActiveRejected() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).status(SeriesStatus.PAUSED).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("dev-user");
        when(communityAccess.isMember(1L, "dev-user")).thenReturn(true);

        assertThatThrownBy(() -> sut.materialize(5L)).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("SERIES_NOT_ACTIVE");
    }

    @Test
    void materialize_beyondScheduleHorizonNotSchedulable() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).cadenceCount(1).cadenceUnit(CadenceUnit.DAY)
                .startsOn(LocalDate.parse("2015-01-01")).defaultStructure(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("dev-user");
        when(communityAccess.isMember(1L, "dev-user")).thenReturn(true);

        assertThatThrownBy(() -> sut.materialize(5L)).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("SERIES_NOT_SCHEDULABLE");
    }

    @Test
    void materialize_notMember() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).defaultStructure(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("intruder");
        when(communityAccess.isMember(1L, "intruder")).thenReturn(false);

        assertThatThrownBy(() -> sut.materialize(5L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void materialize_seriesNotFound() {

        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.materialize(5L)).isInstanceOf(NotFoundException.class);
    }

    /**
     * {@code materialize} picks its next slot against the injected {@link Clock}, not the system clock: a daily series
     * starting two days before the fixed {@code NOW} lands on {@code NOW}'s own date only if the code asks the clock
     * for "now" rather than {@code Instant.now()}, which would pick a slot on whatever date the test actually runs.
     */
    @Test
    void materialize_picksNextSlotAgainstTheInjectedClockNotTheSystemClock() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).cadenceCount(1).cadenceUnit(CadenceUnit.DAY)
                .startsOn(LocalDate.parse("2026-03-08")).preferredTime(java.time.LocalTime.of(10, 0))
                .defaultStructure(List.of()).build();
        var expectedAnchor = LocalDate.parse("2026-03-10");
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("dev-user");
        when(communityAccess.isMember(1L, "dev-user")).thenReturn(true);
        when(sessionRepository.findByOrganizationIdAndSeriesIdAndOccurrenceDate("default", 5L, expectedAnchor))
                .thenReturn(Optional.empty());
        leadDays(1L, 3);
        when(sessionRepository.save(any()))
                .thenAnswer(inv -> ((SessionEntity) inv.getArgument(0)).toBuilder().id(99L).build());

        var actual = sut.materialize(5L);

        assertThat(actual.occurrenceDate()).isEqualTo(expectedAnchor);
        assertThat(actual.scheduledAt()).isEqualTo(Instant.parse("2026-03-10T10:00:00Z"));
    }

    @Test
    void ensure_freshAnchorCreatesScheduledSession() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).startsOn(LocalDate.parse("2026-06-03"))
                .preferredTime(java.time.LocalTime.of(18, 0))
                .defaultStructure(List
                        .of(SectionTypeRef.builder().sectionTypeId(1L).title("Main talk").durationMinutes(30).build()))
                .build();
        var anchor = LocalDate.of(2099, 12, 12);
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("dev-user");
        when(communityAccess.isMember(1L, "dev-user")).thenReturn(true);
        when(sessionRepository.findByOrganizationIdAndSeriesIdAndOccurrenceDate("default", 5L, anchor))
                .thenReturn(Optional.empty());
        leadDays(1L, 3);
        when(sessionRepository.save(any()))
                .thenAnswer(inv -> ((SessionEntity) inv.getArgument(0)).toBuilder().id(99L).build());

        var actual = sut.ensure(5L, anchor);

        assertThat(actual.id()).isEqualTo(99L);
        assertThat(actual.status()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(actual.occurrenceDate()).isEqualTo(anchor);
        assertThat(actual.scheduledAt()).isEqualTo(java.time.OffsetDateTime
                .of(anchor, java.time.LocalTime.of(18, 0), java.time.ZoneOffset.UTC).toInstant());
        assertThat(actual.sections()).hasSize(1);
        verify(sessionRepository).save(argThat(e -> anchor.equals(e.getOccurrenceDate())));
    }

    /**
     * The regression lock for the series zone column: materializing an occurrence must compose {@code scheduledAt} in
     * the series' own zone, not {@code UTC}. 18:00 in Europe/Zurich on a November date (past the autumn transition) is
     * 17:00Z, not 18:00Z - if {@link SessionService#ensure} silently reverted to a hard-coded UTC zone, this would
     * fail.
     */
    @Test
    void ensure_materializesInTheSeriesOwnZone_notUtc() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).startsOn(LocalDate.parse("2026-06-03"))
                .preferredTime(java.time.LocalTime.of(18, 0)).timeZone("Europe/Zurich").defaultStructure(List.of())
                .build();
        var anchor = LocalDate.of(2026, 11, 18);
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("dev-user");
        when(communityAccess.isMember(1L, "dev-user")).thenReturn(true);
        when(sessionRepository.findByOrganizationIdAndSeriesIdAndOccurrenceDate("default", 5L, anchor))
                .thenReturn(Optional.empty());
        leadDays(1L, 3);
        when(sessionRepository.save(any()))
                .thenAnswer(inv -> ((SessionEntity) inv.getArgument(0)).toBuilder().id(99L).build());

        var actual = sut.ensure(5L, anchor);

        assertThat(actual.scheduledAt()).isEqualTo(Instant.parse("2026-11-18T17:00:00Z"));
    }

    /**
     * A series that never named an hour materialises an occurrence that says so. The instant it composes is midnight in
     * the series' own zone, which is exactly the shape no reader can tell apart from a series that genuinely meets at
     * midnight - so the row states the fact rather than leaving three consumers to infer it back out of the instant.
     * The zone here is deliberately not UTC: midnight in Paris is 22:00Z, so the fact cannot be coming from the shape.
     */
    @Test
    void ensure_seriesWithNoPreferredTime_materialisesAnUntimedOccurrence() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).startsOn(LocalDate.parse("2026-06-03"))
                .preferredTime(null).timeZone("Europe/Paris").defaultStructure(List.of()).build();
        var anchor = LocalDate.of(2026, 8, 13);
        givenAnEnsurableSeries(series, anchor);

        var actual = sut.ensure(5L, anchor);

        assertThat(actual.hasTime()).isFalse();
        assertThat(actual.scheduledAt()).isEqualTo(Instant.parse("2026-08-12T22:00:00Z"));
        verify(sessionRepository).save(argThat(e -> !e.isHasTime()));
    }

    @Test
    void ensure_seriesWithAPreferredTime_materialisesATimedOccurrence() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).startsOn(LocalDate.parse("2026-06-03"))
                .preferredTime(java.time.LocalTime.of(18, 0)).timeZone("Europe/Paris").defaultStructure(List.of())
                .build();
        var anchor = LocalDate.of(2026, 8, 13);
        givenAnEnsurableSeries(series, anchor);

        var actual = sut.ensure(5L, anchor);

        assertThat(actual.hasTime()).isTrue();
        verify(sessionRepository).save(argThat(e -> e.isHasTime()));
    }

    /**
     * A one-off's form has always required an hour, so no one-off ever arrives without one. The instant chosen here is
     * midnight UTC on purpose: the fact has to come from the creation path, not from what the instant happens to look
     * like.
     */
    @Test
    void createOneOff_alwaysStatesThatItHasATime() {

        givenOrganizerOfFrontend();
        leadDays(1L, 3);
        when(sessionRepository.save(any()))
                .thenAnswer(inv -> ((SessionEntity) inv.getArgument(0)).toBuilder().id(55L).build());

        var actual = sut.createOneOff("frontend", new CreateOneOffRequest("Lightning talks",
                stated(Instant.parse("2099-12-20T00:00:00Z")), null, 60, List.of()));

        assertThat(actual.hasTime()).isTrue();
        verify(sessionRepository).save(argThat(e -> e.isHasTime()));
    }

    /**
     * The default rule at the other creation path: a projected occurrence becomes a real row only on first touch, and
     * that row must meet the same default as a one-off does, not skip it because it arrived through a different door.
     */
    @Test
    void ensure_stampsTheUpcomingReleaseDerivedFromTheCommunitysLeadTime() {

        // given a weekly series whose next occurrence is a long way out
        var series = SeriesEntityMother.any().id(5L).communityId(1L).startsOn(LocalDate.parse("2026-06-03"))
                .preferredTime(java.time.LocalTime.of(18, 0)).defaultStructure(List.of()).build();
        var anchor = LocalDate.of(2099, 12, 12);
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("dev-user");
        when(communityAccess.isMember(1L, "dev-user")).thenReturn(true);
        when(sessionRepository.findByOrganizationIdAndSeriesIdAndOccurrenceDate("default", 5L, anchor))
                .thenReturn(Optional.empty());
        leadDays(1L, 3);
        when(sessionRepository.save(any()))
                .thenAnswer(inv -> ((SessionEntity) inv.getArgument(0)).toBuilder().id(99L).build());

        // when
        var actual = sut.ensure(5L, anchor);

        // then
        var expected = Instant.parse("2099-12-09T18:00:00Z");
        assertThat(actual.upcomingReleaseAt()).isEqualTo(expected);
        verify(sessionRepository).save(argThat(e -> expected.equals(e.getUpcomingReleaseAt())));
    }

    @Test
    void ensure_sameAnchorTwiceIsIdempotent() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).defaultStructure(List.of()).build();
        var anchor = LocalDate.of(2099, 12, 12);
        var existing = SessionEntityMother.any().id(42L).seriesId(5L).occurrenceDate(anchor).sections(List.of())
                .build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("dev-user");
        when(communityAccess.isMember(1L, "dev-user")).thenReturn(true);
        when(sessionRepository.findByOrganizationIdAndSeriesIdAndOccurrenceDate("default", 5L, anchor))
                .thenReturn(Optional.of(existing));

        var actual = sut.ensure(5L, anchor);

        assertThat(actual.id()).isEqualTo(42L);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void ensure_requiresMembership() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).defaultStructure(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("intruder");
        when(communityAccess.isMember(1L, "intruder")).thenReturn(false);

        assertThatThrownBy(() -> sut.ensure(5L, LocalDate.of(2099, 12, 12))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void ensure_requiresActiveSeries() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).status(SeriesStatus.PAUSED).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("dev-user");
        when(communityAccess.isMember(1L, "dev-user")).thenReturn(true);

        assertThatThrownBy(() -> sut.ensure(5L, LocalDate.of(2099, 12, 12))).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("SERIES_NOT_ACTIVE");
    }

    /**
     * Before this the zone composed the instant and was then thrown away, so a session belonging to no series had no
     * clock at all and every message about it had nothing to render an hour in but UTC.
     */
    @Test
    void createOneOff_keepsTheClockItWasTypedIn() {

        givenOrganizerOfFrontend();
        leadDays(1L, 3);
        when(sessionRepository.save(any()))
                .thenAnswer(inv -> ((SessionEntity) inv.getArgument(0)).toBuilder().id(55L).build());

        sut.createOneOff("frontend",
                new CreateOneOffRequest("Incident review",
                        new ScheduledAt(LocalDate.of(2099, 10, 1), LocalTime.of(9, 0), "America/Los_Angeles"), null, 90,
                        List.of()));

        verify(sessionRepository).save(argThat(e -> "America/Los_Angeles".equals(e.getTimeZone())));
    }

    /** An occurrence reads its series' zone, so storing one of its own is how the two come to disagree. */
    @Test
    void ensure_storesNoZoneOfItsOwnForAnOccurrenceOfASeries() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).startsOn(LocalDate.parse("2026-06-03"))
                .preferredTime(LocalTime.of(18, 0)).timeZone("Europe/Zurich").defaultStructure(List.of()).build();
        var anchor = LocalDate.of(2099, 12, 12);
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("dev-user");
        when(communityAccess.isMember(1L, "dev-user")).thenReturn(true);
        when(sessionRepository.findByOrganizationIdAndSeriesIdAndOccurrenceDate("default", 5L, anchor))
                .thenReturn(Optional.empty());
        leadDays(1L, 3);
        when(sessionRepository.save(any()))
                .thenAnswer(inv -> ((SessionEntity) inv.getArgument(0)).toBuilder().id(56L).build());

        sut.ensure(5L, anchor);

        verify(sessionRepository).save(argThat(e -> e.getTimeZone() == null));
    }

    @Test
    void createOneOff_organizerCreatesSerieslessSession() {

        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("frontend")).thenReturn(1L);
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.isOrganizer(1L, "orga")).thenReturn(true);
        var at = Instant.parse("2099-12-20T17:00:00Z");
        leadDays(1L, 3);
        when(sessionRepository.save(any()))
                .thenAnswer(inv -> ((SessionEntity) inv.getArgument(0)).toBuilder().id(55L).build());

        var req = new CreateOneOffRequest("Lightning talks", stated(at), null, 60, List.of());
        var actual = sut.createOneOff("frontend", req);

        assertThat(actual.id()).isEqualTo(55L);
        assertThat(actual.seriesId()).isNull();
        assertThat(actual.occurrenceDate()).isNull();
        assertThat(actual.status()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(actual.scheduledAt()).isEqualTo(at);
        verify(sessionRepository).save(argThat(e -> e.getSeriesId() == null && e.getOccurrenceDate() == null
                && "Lightning talks".equals(e.getTitle())));
    }

    /**
     * A one-off belongs to no series, so its own room is the only room it can ever have: if the request's room does not
     * reach the row, the session has nowhere to meet and nothing else can supply one. The stored column is asserted
     * alongside the projection because the two travel by different routes - the request goes through {@code NewSession}
     * and {@code SessionCreation} to the entity, and comes back through {@code SessionMapper}.
     */
    @Test
    void createOneOff_persistsTheRoomTheSessionMeetsIn() {

        givenOrganizerOfFrontend();
        var at = Instant.parse("2099-12-20T17:00:00Z");
        leadDays(1L, 3);
        when(sessionRepository.save(any()))
                .thenAnswer(inv -> ((SessionEntity) inv.getArgument(0)).toBuilder().id(55L).build());

        var actual = sut.createOneOff("frontend", new CreateOneOffRequest("Lightning talks", stated(at),
                new MeetingPlace(null, "https://meet.example/one-off", null), 60, List.of()));

        assertThat(actual.place().joinUrl()).isEqualTo("https://meet.example/one-off");
        verify(sessionRepository).save(argThat(e -> "https://meet.example/one-off".equals(e.getJoinUrl())));
    }

    @Test
    void createOneOff_withSectionsMapsThemToEntities() {

        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("frontend")).thenReturn(1L);
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.isOrganizer(1L, "orga")).thenReturn(true);
        var at = Instant.parse("2099-12-20T17:00:00Z");
        leadDays(1L, 3);
        talk(2L);
        when(sessionRepository.save(any()))
                .thenAnswer(inv -> ((SessionEntity) inv.getArgument(0)).toBuilder().id(77L).build());

        var req = new CreateOneOffRequest("Talk", stated(at), null, 60,
                List.of(new SectionTemplate(2L, "Lightning rounds", 20)));
        var actual = sut.createOneOff("frontend", req);

        assertThat(actual.id()).isEqualTo(77L);
        verify(sessionRepository).save(
                argThat(e -> e.getSections().size() == 1 && e.getSections().get(0).getSectionTypeId().equals(2L)));
    }

    /** A section type is a row in this Organization's own catalogue, not an arbitrary number. */
    @Test
    void createOneOff_refusesASectionTypeThatIsNotInThisOrganizationsCatalogue() {

        givenOrganizerOfFrontend();
        var at = Instant.parse("2099-12-20T17:00:00Z");
        when(sectionTypeRepository.findByOrganizationIdAndId("default", 999_999L)).thenReturn(Optional.empty());

        var req = new CreateOneOffRequest("Talk", stated(at), null, 60,
                List.of(new SectionTemplate(999_999L, "Bogus", 20)));

        assertThatThrownBy(() -> sut.createOneOff("frontend", req)).isInstanceOf(NotFoundException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void createOneOff_memberBlockedWhenPolicyIsCurated() {

        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("frontend")).thenReturn(1L);
        when(currentUser.userId()).thenReturn("member");
        when(communityAccess.isOrganizer(1L, "member")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L)).thenReturn(dev.axonia.community.ContributionPolicy.curated());

        var req = new CreateOneOffRequest("Talk", stated(Instant.parse("2099-12-20T17:00:00Z")), null, 60, List.of());
        assertThatThrownBy(() -> sut.createOneOff("frontend", req)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createOneOff_memberAllowedWhenPolicyIsOpen() {

        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("frontend")).thenReturn(1L);
        when(currentUser.userId()).thenReturn("member");
        when(communityAccess.isOrganizer(1L, "member")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L))
                .thenReturn(new dev.axonia.community.ContributionPolicy(true, false, false, false));
        when(communityAccess.isMember(1L, "member")).thenReturn(true);
        var at = Instant.parse("2099-12-20T17:00:00Z");
        leadDays(1L, 3);
        when(sessionRepository.save(any()))
                .thenAnswer(inv -> ((SessionEntity) inv.getArgument(0)).toBuilder().id(66L).build());

        var req = new CreateOneOffRequest("Open session", stated(at), null, 45, List.of());
        var actual = sut.createOneOff("frontend", req);
        assertThat(actual.id()).isEqualTo(66L);
    }

    /**
     * The default rule at the one-off creation path, which is also the backfill path: the agent's session-create tool
     * calls {@code createOneOff} rather than inserting rows of its own.
     */
    @Test
    void createOneOff_stampsTheUpcomingReleaseDerivedFromTheCommunitysLeadTime() {

        // given a community that announces three days ahead, and a session a year out
        givenOrganizerOfFrontend();
        var at = Instant.parse("2099-12-20T17:00:00Z");
        leadDays(1L, 3);
        when(sessionRepository.save(any()))
                .thenAnswer(inv -> ((SessionEntity) inv.getArgument(0)).toBuilder().id(55L).build());

        // when
        var actual = sut.createOneOff("frontend",
                new CreateOneOffRequest("Lightning talks", stated(at), null, 60, List.of()));

        // then the intent is stored, on the row and on the projection alike
        assertThat(actual.upcomingReleaseAt()).isEqualTo(at.minus(3, ChronoUnit.DAYS));
        verify(sessionRepository).save(argThat(e -> at.minus(3, ChronoUnit.DAYS).equals(e.getUpcomingReleaseAt())));
    }

    /**
     * The lead time comes from the session's own community, never a constant: the same instant in a community that
     * announces a fortnight ahead releases a fortnight ahead.
     */
    @Test
    void createOneOff_takesTheLeadTimeFromTheSessionsOwnCommunity() {

        // given
        givenOrganizerOfFrontend();
        var at = Instant.parse("2099-12-20T17:00:00Z");
        leadDays(1L, 14);
        when(sessionRepository.save(any()))
                .thenAnswer(inv -> ((SessionEntity) inv.getArgument(0)).toBuilder().id(55L).build());

        // when
        var actual = sut.createOneOff("frontend",
                new CreateOneOffRequest("Lightning talks", stated(at), null, 60, List.of()));

        // then
        assertThat(actual.upcomingReleaseAt()).isEqualTo(at.minus(14, ChronoUnit.DAYS));
    }

    @Test
    void createOneOff_storesNoUpcomingReleaseForASessionInsideItsOwnLeadWindow() {

        // given a session tomorrow, in a community that announces three days ahead
        givenOrganizerOfFrontend();
        var at = NOW.plus(1, ChronoUnit.DAYS);
        leadDays(1L, 3);
        when(sessionRepository.save(any()))
                .thenAnswer(inv -> ((SessionEntity) inv.getArgument(0)).toBuilder().id(55L).build());

        // when
        var actual = sut.createOneOff("frontend",
                new CreateOneOffRequest("Short notice", stated(at), null, 60, List.of()));

        // then no reminder is scheduled: visibly off, rather than an instant that has already gone by
        assertThat(actual.upcomingReleaseAt()).isNull();
        verify(sessionRepository).save(argThat(e -> e.getUpcomingReleaseAt() == null));
    }

    @Test
    void createOneOff_storesNoUpcomingReleaseForASessionBackfilledIntoThePast() {

        // given a session imported from another tool, held two years ago
        givenOrganizerOfFrontend();
        var at = NOW.minus(730, ChronoUnit.DAYS);
        leadDays(1L, 3);
        when(sessionRepository.save(any()))
                .thenAnswer(inv -> ((SessionEntity) inv.getArgument(0)).toBuilder().id(55L).build());

        // when
        var actual = sut.createOneOff("frontend",
                new CreateOneOffRequest("Two years ago", stated(at), null, 60, List.of()));

        // then
        assertThat(actual.upcomingReleaseAt()).isNull();
        verify(sessionRepository).save(argThat(e -> e.getUpcomingReleaseAt() == null));
    }

    private void givenOrganizerOfFrontend() {

        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("frontend")).thenReturn(1L);
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.isOrganizer(1L, "orga")).thenReturn(true);
    }

    @Test
    void update_publishesSessionRescheduled() {

        leadDays(1L, 3);
        stubNoReleaseActivity(7L, 1L);
        givenTheSeriesMeetsIn("UTC");
        var entity = SessionEntityMother.any().id(7L).communityId(1L).sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var moved = Instant.parse("2099-12-20T17:00:00Z");
        sut.update(7L, new UpdateSessionRequest(stated(moved), 90, "orga", null));

        verify(eventPublisher).publishEvent(argThat((Object e) -> e instanceof SessionRescheduled sr
                && sr.sessionId().equals(7L) && sr.communityId().equals(1L)));
    }

    @Test
    void cancel_publishesSessionCancelled() {

        var entity = SessionEntityMother.any().id(7L).communityId(1L).sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sut.cancel(7L);

        verify(eventPublisher)
                .publishEvent(argThat((Object e) -> e instanceof SessionCancelled sc && sc.sessionId().equals(7L)));
    }

    @Test
    void get_pastUnansweredSessionStaysScheduled() {

        // a session past its scheduled time is not HELD just because the clock moved past it
        var entity = SessionEntityMother.any().id(7L).status("SCHEDULED")
                .scheduledAt(Instant.parse("2000-01-01T00:00:00Z")).sections(null).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));

        var actual = sut.get(7L);

        assertThat(actual.status()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(actual.sections()).isEmpty();
    }

    @Test
    void get_storedHeldSessionSurfacesAsHeld() {

        var entity = SessionEntityMother.any().id(7L).status("HELD").scheduledAt(Instant.parse("2000-01-01T00:00:00Z"))
                .sections(null).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));

        var actual = sut.get(7L);

        assertThat(actual.status()).isEqualTo(SessionStatus.HELD);
    }

    @Test
    void get_notFound() {

        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.get(7L)).isInstanceOf(NotFoundException.class);
    }

    /**
     * {@code get} backs {@code SessionAccessAdapter} callers in {@code bookmarks} and {@code knowledge}, neither of
     * which renders the wrap-up card - proving {@code wrapUpStatus} stays null here is what keeps them from paying for
     * {@link dev.axonia.events.internal.SessionMapper#toSessionDetail}'s outbox and roster reads.
     */
    @Test
    void get_leavesBothReleaseStatusesNull() {

        var entity = SessionEntityMother.any().id(7L).status("HELD")
                .wrapUpReleaseAt(Instant.parse("2000-01-01T00:00:00Z"))
                .upcomingReleaseAt(Instant.parse("2000-01-01T00:00:00Z")).sections(null).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));

        var actual = sut.get(7L);

        assertThat(actual.wrapUpStatus()).isNull();
        assertThat(actual.upcomingStatus()).isNull();
    }

    @Test
    void getDetail_carriesBothReleaseStatusesForTheSessionDetailPage() {

        var entity = SessionEntityMother.any().id(7L).status("HELD")
                .wrapUpReleaseAt(Instant.parse("2000-01-01T00:00:00Z")).sections(null).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(messageOutcomePort.outcomeFor(MessageType.SESSION_WRAP_UP, "7"))
                .thenReturn(MessageOutcome.builder().sent(2).build());
        when(messageOutcomePort.outcomeFor(MessageType.SESSION_UPCOMING, "7"))
                .thenReturn(MessageOutcome.builder().build());
        when(communityAccess.memberUserIds("default", 1L)).thenReturn(List.of("alice", "bob"));

        var detail = sut.getDetail(7L);

        assertThat(detail.wrapUpStatus().state()).isEqualTo(ReleaseState.SENT);
        assertThat(detail.wrapUpStatus().sent()).isEqualTo(2);
        assertThat(detail.wrapUpStatus().recipientCount()).isEqualTo(2);
        assertThat(detail.upcomingStatus().state()).isEqualTo(ReleaseState.OFF);
    }

    @Test
    void upcoming_returnsPage() {

        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("frontend")).thenReturn(1L);
        when(sessionListReader.page(SessionPageQuery.builder().organizationId("default").communityId(1L).now(NOW)
                .window(SessionPageQuery.Window.UPCOMING).pageable(Pageable.unpaged()).build()))
                .thenAnswer(inv -> mapper
                        .toSessions(List.of(SessionEntityMother.any().id(7L).sections(List.of()).build())));
        when(sessionRepository.countUpcoming(eq("default"), eq(1L), eq(NOW))).thenReturn(1L);

        var actual = sut.upcoming("frontend", Pageable.unpaged());

        assertThat(actual.getTotalElements()).isEqualTo(1L);
        assertThat(actual.getContent().getFirst().id()).isEqualTo(7L);
    }

    @Test
    void past_returnsPageWithStoredHeldStatus() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("frontend")).thenReturn(1L);
        when(sessionListReader.page(any(SessionPageQuery.class)))
                .thenAnswer(inv -> mapper.toSessions(List.of(SessionEntityMother.any().id(7L).status("HELD")
                        .scheduledAt(Instant.parse("2000-01-01T00:00:00Z")).sections(List.of()).build())));
        when(sessionRepository.countPast(any(PastSessionsQuery.class))).thenReturn(12L);

        // when
        var actual = sut.past(PastSessionsRequest.builder().slug("frontend").pageable(Pageable.unpaged()).build());

        // then
        assertThat(actual.getTotalElements()).isEqualTo(12L);
        assertThat(actual.getContent().getFirst().id()).isEqualTo(7L);
        assertThat(actual.getContent().getFirst().status()).isEqualTo(SessionStatus.HELD);
    }

    /**
     * {@code past} builds both the page query it reads and the {@link PastSessionsQuery} it counts from the injected
     * {@link Clock}, not {@code Instant.now()} - the same testability concern {@code materialize} and {@code upcoming}
     * share.
     */
    @Test
    void past_buildsQueryFromTheInjectedClock() {

        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("frontend")).thenReturn(1L);
        when(sessionListReader.page(any(SessionPageQuery.class))).thenReturn(List.of());
        when(sessionRepository.countPast(any(PastSessionsQuery.class))).thenReturn(0L);

        sut.past(PastSessionsRequest.builder().slug("frontend").pageable(Pageable.unpaged()).build());

        var captor = ArgumentCaptor.forClass(SessionPageQuery.class);
        verify(sessionListReader).page(captor.capture());
        assertThat(captor.getValue().now()).isEqualTo(NOW);
        assertThat(captor.getValue().window()).isEqualTo(SessionPageQuery.Window.PAST);
        var countCaptor = ArgumentCaptor.forClass(PastSessionsQuery.class);
        verify(sessionRepository).countPast(countCaptor.capture());
        assertThat(countCaptor.getValue().now()).isEqualTo(NOW);
    }

    /**
     * The status narrows the page and the count alike. A total counted over a wider set than the page reads is the
     * defect this asserts against: it would report occurrences that never took place as though they had.
     */
    @Test
    void past_carriesTheAskedStatusIntoBothTheQueryAndTheCount() {

        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("frontend")).thenReturn(1L);
        when(sessionListReader.page(any(SessionPageQuery.class))).thenReturn(List.of());
        when(sessionRepository.countPast(any(PastSessionsQuery.class))).thenReturn(0L);

        sut.past(PastSessionsRequest.builder().slug("frontend").status(SessionStatus.HELD).pageable(Pageable.unpaged())
                .build());

        var captor = ArgumentCaptor.forClass(SessionPageQuery.class);
        verify(sessionListReader).page(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(SessionStatus.HELD);
        var countCaptor = ArgumentCaptor.forClass(PastSessionsQuery.class);
        verify(sessionRepository).countPast(countCaptor.capture());
        assertThat(countCaptor.getValue().status()).isEqualTo(SessionStatus.HELD);
    }

    @Test
    void update_preservesOccurrenceAnchor() {

        leadDays(1L, 3);
        stubNoReleaseActivity(7L, 1L);
        givenTheSeriesMeetsIn("UTC");
        var anchor = LocalDate.of(2099, 12, 12);
        var entity = SessionEntityMother.any().id(7L).communityId(1L).occurrenceDate(anchor).sections(List.of())
                .build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var moved = Instant.parse("2099-12-21T17:00:00Z");
        sut.update(7L, new UpdateSessionRequest(stated(moved), 90, "newfac", null));

        verify(sessionRepository).save(argThat(e -> anchor.equals(e.getOccurrenceDate())));
    }

    @Test
    void update_movesAndSetsFacilitator() {

        leadDays(1L, 3);
        stubNoReleaseActivity(7L, 1L);
        givenTheSeriesMeetsIn("UTC");
        var entity = SessionEntityMother.any().id(7L).communityId(1L).sections(List.of()).facilitatorUserId("orga")
                .build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var moved = Instant.parse("2099-12-20T17:00:00Z");
        var actual = sut.update(7L, new UpdateSessionRequest(stated(moved), 90, "newfac", null));

        assertThat(actual.scheduledAt()).isEqualTo(moved);
        assertThat(actual.durationMinutes()).isEqualTo(90);
        assertThat(actual.facilitatorUserId()).isEqualTo("newfac");
        verify(sessionAuthorizer).requireOrganizer(entity, "orga");
    }

    /**
     * The facilitator becomes a message recipient: AwaitingCloseProducer addresses the reminder to them. A free string
     * written straight onto the row makes "who gets told this session needs closing" a request-body field, and every
     * other person-shaped field on this context (presenter, assignee, contributor) is resolved through
     * CommunityAccess.resolveMember instead.
     */
    @Test
    void update_refusesAFacilitatorWhoIsNotAMemberOfThisCommunity() {

        givenTheSeriesMeetsIn("UTC");
        var entity = SessionEntityMother.any().id(7L).communityId(1L).sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.resolveMember(1L, "u-outsider")).thenReturn(new MemberMatch.NotFound());

        var moved = Instant.parse("2099-12-20T17:00:00Z");
        assertThatThrownBy(() -> sut.update(7L, new UpdateSessionRequest(stated(moved), 90, "u-outsider", null)))
                .isInstanceOf(dev.axonia.ValidationException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void update_acceptsAFacilitatorWhoIsAMember() {

        leadDays(1L, 3);
        stubNoReleaseActivity(7L, 1L);
        givenTheSeriesMeetsIn("UTC");
        var entity = SessionEntityMother.any().id(7L).communityId(1L).sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.resolveMember(1L, "u-2")).thenReturn(new MemberMatch.Matched("u-2"));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var moved = Instant.parse("2099-12-20T17:00:00Z");
        var actual = sut.update(7L, new UpdateSessionRequest(stated(moved), 90, "u-2", null));

        assertThat(actual.facilitatorUserId()).isEqualTo("u-2");
    }

    /**
     * An ambiguous reference is a different failure than an unresolvable one: told only "not a member", an organizer
     * who typed a display name two members share has nothing to act on. The candidate count survives, the same way
     * TopicMemberResolver and PresenterResolver already report it.
     */
    @Test
    void update_refusesAnAmbiguousFacilitator() {

        givenTheSeriesMeetsIn("UTC");
        var entity = SessionEntityMother.any().id(7L).communityId(1L).sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.resolveMember(1L, "Bob")).thenReturn(new MemberMatch.Ambiguous(3));

        var moved = Instant.parse("2099-12-20T17:00:00Z");
        assertThatThrownBy(() -> sut.update(7L, new UpdateSessionRequest(stated(moved), 90, "Bob", null)))
                .isInstanceOf(dev.axonia.ValidationException.class).extracting("code")
                .isEqualTo("FACILITATOR_AMBIGUOUS");
        verify(sessionRepository, never()).save(any());
    }

    /**
     * UpdateSessionRequest is a full replace: a client editing an unrelated field (the room, the duration) echoes back
     * whatever facilitator the row already carries. Re-validating that echo on every save would lock the session out of
     * being edited at all once the facilitator has left the community, or once an erasure surrogate has replaced the
     * id, until someone thinks to clear the field first purely to unblock an unrelated save.
     */
    @Test
    void update_leavesAnUnchangedFacilitatorAloneEvenIfItNoLongerResolves() {

        leadDays(1L, 3);
        stubNoReleaseActivity(7L, 1L);
        givenTheSeriesMeetsIn("UTC");
        // Stored and submitted must be equal, distinct objects: two occurrences of the same string literal are the
        // same interned instance, so a future ".equals" -> "==" regression would still pass this test by accident.
        // String.join builds a fresh instance at runtime with no compile-time constant folding to undo that.
        var storedFacilitatorUserId = String.join("", "u-departed");
        var entity = SessionEntityMother.any().id(7L).communityId(1L).sections(List.of())
                .facilitatorUserId(storedFacilitatorUserId).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var submittedFacilitatorUserId = String.join("", "u-departed");
        assertThat(submittedFacilitatorUserId).isNotSameAs(storedFacilitatorUserId).isEqualTo(storedFacilitatorUserId);

        var moved = Instant.parse("2099-12-20T17:00:00Z");
        var actual = sut.update(7L, new UpdateSessionRequest(stated(moved), 90, submittedFacilitatorUserId, null));

        assertThat(actual.facilitatorUserId()).isEqualTo("u-departed");
        verify(communityAccess, never()).resolveMember(anyLong(), any());
    }

    /** No facilitator is a legitimate state and clears the field. */
    @Test
    void update_acceptsNoFacilitator() {

        leadDays(1L, 3);
        stubNoReleaseActivity(7L, 1L);
        givenTheSeriesMeetsIn("UTC");
        var entity = SessionEntityMother.any().id(7L).communityId(1L).sections(List.of()).facilitatorUserId("orga")
                .build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var moved = Instant.parse("2099-12-20T17:00:00Z");
        var actual = sut.update(7L, new UpdateSessionRequest(stated(moved), 90, null, null));

        assertThat(actual.facilitatorUserId()).isNull();
        verify(communityAccess, never()).resolveMember(anyLong(), any());
    }

    /** A blank facilitator is treated the same as an absent one - cleared, not looked up. */
    @Test
    void update_treatsABlankFacilitatorAsAbsent() {

        leadDays(1L, 3);
        stubNoReleaseActivity(7L, 1L);
        givenTheSeriesMeetsIn("UTC");
        var entity = SessionEntityMother.any().id(7L).communityId(1L).sections(List.of()).facilitatorUserId("orga")
                .build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var moved = Instant.parse("2099-12-20T17:00:00Z");
        var actual = sut.update(7L, new UpdateSessionRequest(stated(moved), 90, "   ", null));

        assertThat(actual.facilitatorUserId()).isNull();
        verify(communityAccess, never()).resolveMember(anyLong(), any());
    }

    /**
     * The week the usual room is booked, one occurrence meets somewhere else. The override has to reach the row: a
     * response that echoed the request while the column stayed behind would show the organizer a move that never
     * happened, and the calendar feed would keep publishing the old room.
     */
    @Test
    void update_setsTheRoomThisOneOccurrenceMeetsIn() {

        leadDays(1L, 3);
        stubNoReleaseActivity(7L, 1L);
        givenTheSeriesMeetsIn("UTC");
        var entity = SessionEntityMother.any().id(7L).communityId(1L).sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var moved = Instant.parse("2099-12-20T17:00:00Z");
        var actual = sut.update(7L, new UpdateSessionRequest(stated(moved), 90, "orga",
                new MeetingPlace(null, "https://meet.example/this-week", null)));

        assertThat(actual.place().joinUrl()).isEqualTo("https://meet.example/this-week");
        verify(sessionRepository).save(argThat(e -> "https://meet.example/this-week".equals(e.getJoinUrl())));
    }

    /**
     * {@code hasTime} is now what the request says, not what the row already said. It used to travel through
     * {@code toBuilder()} untouched, so an occurrence that arrived untimed stayed untimed however plainly an organizer
     * then typed an hour into the dialog: the row said 18:00 and the page went on rendering a date with no time.
     */
    @Test
    void update_statesThatTheOccurrenceHasATimeWhenTheRequestNamesOne() {

        leadDays(1L, 3);
        stubNoReleaseActivity(7L, 1L);
        givenTheSeriesMeetsIn("Europe/Paris");
        var entity = SessionEntityMother.any().id(7L).communityId(1L).hasTime(false)
                .scheduledAt(Instant.parse("2026-08-12T22:00:00Z")).sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var actual = sut.update(7L, new UpdateSessionRequest(
                new ScheduledAt(LocalDate.of(2026, 8, 13), LocalTime.of(17, 0), "UTC"), 90, "orga", null));

        assertThat(actual.hasTime()).isTrue();
        assertThat(actual.scheduledAt()).isEqualTo(Instant.parse("2026-08-13T15:00:00Z"));
        verify(sessionRepository).save(argThat(SessionEntity::isHasTime));
    }

    /**
     * The other direction, and the one an organizer reaches by clearing the field: the occurrence goes untimed, and its
     * instant becomes midnight in the zone its series meets in. Asserting the instant matters as much as the flag,
     * because midnight in {@code Europe/Paris} is 22:00Z the day before - a composition that fell back to UTC would
     * silently move the occurrence onto the wrong civil date while the flag still read false.
     */
    @Test
    void update_clearingTheTimeLeavesTheOccurrenceUntimedAtMidnightInItsSeriesZone() {

        leadDays(1L, 3);
        stubNoReleaseActivity(7L, 1L);
        givenTheSeriesMeetsIn("Europe/Paris");
        var entity = SessionEntityMother.any().id(7L).communityId(1L).hasTime(true).sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var actual = sut.update(7L,
                new UpdateSessionRequest(new ScheduledAt(LocalDate.of(2026, 8, 13), null, "UTC"), 90, "orga", null));

        assertThat(actual.hasTime()).isFalse();
        assertThat(actual.scheduledAt()).isEqualTo(Instant.parse("2026-08-12T22:00:00Z"));
        verify(sessionRepository).save(argThat(e -> !e.isHasTime()));
    }

    /**
     * A one-off has no cadence to be untimed within, so clearing its time is refused rather than stored. The same
     * refusal {@code createOneOff} makes, reached from the edit path: an organizer cannot arrive at an untimed one-off
     * by creating a timed one and then emptying the field.
     */
    @Test
    void update_refusesToLeaveAOneOffUntimed() {

        var entity = SessionEntityMother.any().id(7L).seriesId(null).occurrenceDate(null).communityId(1L)
                .sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");

        assertThatThrownBy(() -> sut.update(7L,
                new UpdateSessionRequest(new ScheduledAt(LocalDate.of(2026, 8, 13), null, "UTC"), 90, "orga", null)))
                .isInstanceOf(dev.axonia.ValidationException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void update_onCancelledRejected() {

        var entity = SessionEntityMother.any().id(7L).communityId(1L).status("CANCELLED").sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");

        assertThatThrownBy(() -> sut.update(7L,
                new UpdateSessionRequest(stated(Instant.parse("2099-12-20T17:00:00Z")), 90, "x", null)))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("SESSION_CANCELLED");
    }

    @Test
    void cancel_marksCancelled() {

        var entity = SessionEntityMother.any().id(7L).communityId(1L).sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var actual = sut.cancel(7L);

        assertThat(actual.status()).isEqualTo(SessionStatus.CANCELLED);
    }

    @Test
    void cancel_revertsSlottedTopicsToProposed() {

        var slot = SlotEntity.builder().id(11L).topicId(99L).build();
        var section = SectionEntity.builder().id(21L).sectionTypeId(1L).slots(new ArrayList<>(List.of(slot))).build();
        var entity = SessionEntityMother.any().id(7L).communityId(1L).sections(new ArrayList<>(List.of(section)))
                .build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(topicRepository.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional.of(TopicEntity.builder()
                .id(99L).organizationId("default").communityId(1L).status(TopicStatus.SCHEDULED).build()));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sut.cancel(7L);

        verify(topicRepository).save(argThat(t -> t.getStatus() == TopicStatus.PROPOSED));
    }

    @Test
    void cancel_alreadyCancelledRejected() {

        var entity = SessionEntityMother.any().id(7L).communityId(1L).status("CANCELLED").sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");

        assertThatThrownBy(() -> sut.cancel(7L)).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("SESSION_CANCELLED");
    }

    @Test
    void cancel_alreadyHeldRejected() {

        var entity = SessionEntityMother.any().id(7L).communityId(1L).status("HELD")
                .scheduledAt(Instant.parse("2000-01-01T00:00:00Z")).sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");

        assertThatThrownBy(() -> sut.cancel(7L)).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("SESSION_ALREADY_HELD");
    }

    @Test
    void cancel_pastUnansweredSessionAllowed() {

        // HELD is asserted, not derived from the clock, so a past SCHEDULED session is still an open
        // question and cancelling it is the organizer's "no, it did not happen" answer.
        var entity = SessionEntityMother.any().id(42L).communityId(1L).status("SCHEDULED")
                .scheduledAt(Instant.parse("2026-01-01T10:00:00Z")).sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 42L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        sut.cancel(42L);

        verify(sessionRepository).save(argThat(s -> "CANCELLED".equals(s.getStatus())));
    }

    @Test
    void recordFollowUp_recordsAndMarksTopicsDone() {

        var slot = SlotEntity.builder().id(11L).topicId(50L).presenters(Set.of()).build();
        var section = SectionEntity.builder().id(21L).sectionTypeId(1L).slots(new ArrayList<>(List.of(slot))).build();
        var entity = SessionEntityMother.any().id(7L).communityId(1L).status("HELD")
                .scheduledAt(Instant.parse("2000-01-01T00:00:00Z")).sections(new ArrayList<>(List.of(section))).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(topicRepository.findByOrganizationIdAndId("default", 50L))
                .thenReturn(Optional.of(TopicEntityMother.any().id(50L).communityId(1L).build()));
        when(topicRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var actual = sut.recordFollowUp(7L, new FollowUpRequest("Great turnout", "https://rec/1", null));

        assertThat(actual.notes()).isEqualTo("Great turnout");
        verify(topicRepository).save(org.mockito.ArgumentMatchers.argThat(t -> t.getStatus() == TopicStatus.DONE));
        verify(sessionAuthorizer).requireOrganizer(entity, "orga");
    }

    @Test
    void recordFollowUp_organizerCheckRefusalSkipsDecisions() {

        var entity = SessionEntityMother.any().id(7L).communityId(1L).status("HELD")
                .scheduledAt(Instant.parse("2000-01-01T00:00:00Z")).sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        doThrow(new ForbiddenException("Not an organizer")).when(sessionAuthorizer).requireOrganizer(any(), any());

        var decisions = List.of(new CheckpointDecision(5L, CheckpointOutcome.DONE));
        assertThatThrownBy(() -> sut.recordFollowUp(7L, new FollowUpRequest(null, null, decisions)))
                .isInstanceOf(ForbiddenException.class);

        verify(sessionRepository, never()).save(any());
        verify(checkpointDecisionPass, never()).apply(any(), any(), any(), any());
    }

    @Test
    void recordFollowUp_onCancelledRejected() {

        var entity = SessionEntityMother.any().id(7L).communityId(1L).status("CANCELLED").sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");

        assertThatThrownBy(() -> sut.recordFollowUp(7L, new FollowUpRequest(null, null, null)))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("SESSION_CANCELLED");
    }

    @Test
    void recordFollowUp_notHeldRejected() {

        var entity = SessionEntityMother.any().id(7L).communityId(1L).sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");

        assertThatThrownBy(() -> sut.recordFollowUp(7L, new FollowUpRequest(null, null, null)))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("SESSION_NOT_HELD");
    }

    @Test
    void recordFollowUp_pastButUnansweredRejected() {

        // The follow-up guard tightens - a session merely in the past is not enough, only one someone
        // confirmed took place is.
        var entity = SessionEntityMother.any().id(42L).communityId(1L).status("SCHEDULED")
                .scheduledAt(Instant.parse("2026-01-01T10:00:00Z")).sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 42L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");

        assertThatThrownBy(() -> sut.recordFollowUp(42L, new FollowUpRequest("notes", null, null)))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("SESSION_NOT_HELD");
    }

    @Test
    void confirmingHeldStampsReleaseAtTheCommunityGracePeriod() {

        givenScheduledPastSession(42L);
        when(communityAccess.communicationSettings(7L)).thenReturn(new CommunicationSettings(true, true, 3, 48));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubNoReleaseActivity(42L, 7L);

        sut.close(42L, new CloseSessionRequest(true));

        verify(sessionRepository).save(argThat(s -> "HELD".equals(s.getStatus()) && NOW.equals(s.getClosedAt())
                && NOW.plus(48, ChronoUnit.HOURS).equals(s.getWrapUpReleaseAt())));
        verify(sessionAuthorizer).requireOrganizer(any(SessionEntity.class), eq("orga"));
    }

    /**
     * The product counter, asserted on the series a dashboard panel actually reads rather than on a mock having been
     * called: a call site wired to the wrong constant writes a different series and fails here.
     */
    @Test
    void confirmingHeldCountsOneSessionHeld() {

        givenScheduledPastSession(42L);
        when(communityAccess.communicationSettings(7L)).thenReturn(new CommunicationSettings(true, true, 3, 48));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubNoReleaseActivity(42L, 7L);

        sut.close(42L, new CloseSessionRequest(true));

        assertThat(usageEvents("session-held")).isEqualTo(1.0);
    }

    @Test
    void answeringNoCountsNoSessionHeld() {

        givenScheduledPastSession(42L);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubNoReleaseActivity(42L, 7L);

        sut.close(42L, new CloseSessionRequest(false));

        assertThat(usageEvents("session-held")).isZero();
    }

    private double usageEvents(String event) {

        return meters.get("axonia.usage.events").tag("event", event).counter().count();
    }

    @Test
    void answeringNoCancelsAndReleasesNothing() {

        givenScheduledPastSession(42L);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubNoReleaseActivity(42L, 7L);

        sut.close(42L, new CloseSessionRequest(false));

        verify(sessionRepository).save(argThat(s -> "CANCELLED".equals(s.getStatus()) && NOW.equals(s.getClosedAt())
                && s.getWrapUpReleaseAt() == null));
    }

    /**
     * The "no" answer runs through the existing cancel path, not just its status string: a fizzled session with slotted
     * topics must give them back to the backlog exactly like {@code POST /sessions/{id}/cancel} does, or they stay
     * silently pinned to a dead session.
     */
    @Test
    void answeringNoReleasesSlottedTopicsToTheBacklog() {

        var slot = SlotEntity.builder().id(11L).topicId(99L).build();
        var section = SectionEntity.builder().id(21L).sectionTypeId(1L).slots(new ArrayList<>(List.of(slot))).build();
        var entity = SessionEntityMother.any().id(42L).communityId(7L).status("SCHEDULED")
                .scheduledAt(NOW.minus(1, ChronoUnit.HOURS)).sections(new ArrayList<>(List.of(section))).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 42L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(topicRepository.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional.of(TopicEntity.builder()
                .id(99L).organizationId("default").communityId(7L).status(TopicStatus.SCHEDULED).build()));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubNoReleaseActivity(42L, 7L);

        sut.close(42L, new CloseSessionRequest(false));

        verify(topicRepository).save(argThat(t -> t.getStatus() == TopicStatus.PROPOSED));
    }

    @Test
    void answeringNoPublishesSessionCancelled() {

        givenScheduledPastSession(42L);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubNoReleaseActivity(42L, 7L);

        sut.close(42L, new CloseSessionRequest(false));

        verify(eventPublisher)
                .publishEvent(argThat((Object e) -> e instanceof SessionCancelled sc && sc.sessionId().equals(42L)));
    }

    /**
     * A session cancelled through the dedicated endpoint never got {@code closedAt} stamped (pre-existing behaviour,
     * and every row cancelled before the V26 backfill). The close guard must still catch it by status, or a called-off
     * session can be resurrected into HELD and mailed a wrap-up.
     */
    @Test
    void aCancelledSessionCannotBeClosedAsHeld() {

        givenCancelledSession(42L);

        assertThatThrownBy(() -> sut.close(42L, new CloseSessionRequest(true))).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("SESSION_ALREADY_CLOSED");
    }

    @Test
    void aCancelledSessionCannotBeClosedAsNotHeld() {

        givenCancelledSession(42L);

        assertThatThrownBy(() -> sut.close(42L, new CloseSessionRequest(false))).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("SESSION_ALREADY_CLOSED");
    }

    @Test
    void aSessionCannotBeClosedTwice() {

        givenHeldSession(42L);

        assertThatThrownBy(() -> sut.close(42L, new CloseSessionRequest(true))).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("SESSION_ALREADY_CLOSED");
    }

    @Test
    void aFutureSessionCannotBeClosed() {

        givenScheduledFutureSession(42L);

        assertThatThrownBy(() -> sut.close(42L, new CloseSessionRequest(true))).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("SESSION_NOT_YET_DUE");
    }

    /**
     * A session still ahead of the clock, carrying whatever reminder intent the test needs. Every reschedule test
     * shares it so the fixture cannot drift between "untouched" and "deliberate".
     */
    private void givenScheduledSession(Long id, Instant scheduledAt, Instant upcomingReleaseAt) {

        var entity = SessionEntityMother.any().id(id).communityId(7L).status("SCHEDULED").scheduledAt(scheduledAt)
                .upcomingReleaseAt(upcomingReleaseAt).sections(List.of()).build();
        givenTheSeriesMeetsIn("UTC");
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", id)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
    }

    /**
     * The reschedule rule, first half. Nobody ever touched this reminder - the stored instant is exactly the default
     * the old schedule implies - so it moves with the session and keeps announcing it the same number of days ahead.
     */
    @Test
    void update_movesAReminderNobodyEverTouched() {

        leadDays(7L, 3);
        stubNoReleaseActivity(7L, 7L);
        givenScheduledSession(7L, NOW.plus(30, ChronoUnit.DAYS), NOW.plus(27, ChronoUnit.DAYS));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sut.update(7L, new UpdateSessionRequest(stated(NOW.plus(60, ChronoUnit.DAYS)), 90, "orga", null));

        verify(sessionRepository).save(argThat(s -> NOW.plus(57, ChronoUnit.DAYS).equals(s.getUpcomingReleaseAt())));
        // one lead-time read for the whole decision: the comparison and the recompute share it, so a cross-context
        // read is not doubled on every session edit and both halves cannot disagree about the lead time
        verify(communityAccess, times(1)).communicationSettings("default", 7L);
    }

    /**
     * A reschedule can change the reminder's state, so it answers {@link SessionMapper#toSessionDetail} like every
     * other mutation in this service that does - a caller reading the response back to see where the reminder landed
     * would otherwise be handed the null that means "not the detail read".
     */
    @Test
    void update_answersWithTheDetailProjection() {

        leadDays(7L, 3);
        givenScheduledSession(7L, NOW.plus(30, ChronoUnit.DAYS), NOW.plus(27, ChronoUnit.DAYS));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(communityAccess.memberUserIds("default", 7L)).thenReturn(List.of("alice", "bob"));
        when(messageOutcomePort.outcomeFor(any(), eq("7"))).thenReturn(MessageOutcome.builder().build());

        var actual = sut.update(7L, new UpdateSessionRequest(stated(NOW.plus(60, ChronoUnit.DAYS)), 90, "orga", null));

        assertThat(actual.upcomingStatus().state()).isEqualTo(ReleaseState.SCHEDULED);
        assertThat(actual.upcomingStatus().recipientCount()).isEqualTo(2);
        assertThat(actual.wrapUpStatus().state()).isEqualTo(ReleaseState.OFF);
    }

    /**
     * The reschedule rule, second half, and the companion the first half is worthless without: the stored instant
     * differs from the default the old schedule implies, so an organizer put it there and the reschedule leaves it
     * alone.
     */
    @Test
    void update_leavesAReminderTheOrganizerChoseExactlyWhereItIs() {

        leadDays(7L, 3);
        stubNoReleaseActivity(7L, 7L);
        var deliberate = NOW.plus(10, ChronoUnit.DAYS);
        givenScheduledSession(7L, NOW.plus(30, ChronoUnit.DAYS), deliberate);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sut.update(7L, new UpdateSessionRequest(stated(NOW.plus(60, ChronoUnit.DAYS)), 90, "orga", null));

        verify(sessionRepository).save(argThat(s -> deliberate.equals(s.getUpcomingReleaseAt())));
    }

    /**
     * The limit the reschedule rule accepts out loud: a deliberate instant left in place can end up after the session's
     * new start. It is reported by the card rather than quietly corrected, because correcting it would overwrite the
     * one thing a stored intent exists to preserve - that somebody decided.
     */
    @Test
    void update_leavesADeliberateReminderEvenWhenItNowFallsAfterTheNewStart() {

        leadDays(7L, 3);
        stubNoReleaseActivity(7L, 7L);
        var deliberate = NOW.plus(20, ChronoUnit.DAYS);
        givenScheduledSession(7L, NOW.plus(30, ChronoUnit.DAYS), deliberate);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sut.update(7L, new UpdateSessionRequest(stated(NOW.plus(5, ChronoUnit.DAYS)), 90, "orga", null));

        verify(sessionRepository).save(argThat(s -> deliberate.equals(s.getUpcomingReleaseAt())));
    }

    /**
     * The recompute is the whole default rule, not just its subtraction: a session pulled inside its own lead window
     * goes visibly off rather than carrying an instant that has already gone by and could never be claimed.
     */
    @Test
    void update_turnsAnUntouchedReminderOffWhenTheSessionMovesInsideItsLeadWindow() {

        leadDays(7L, 3);
        stubNoReleaseActivity(7L, 7L);
        givenScheduledSession(7L, NOW.plus(30, ChronoUnit.DAYS), NOW.plus(27, ChronoUnit.DAYS));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sut.update(7L, new UpdateSessionRequest(stated(NOW.plus(1, ChronoUnit.DAYS)), 90, "orga", null));

        verify(sessionRepository).save(argThat(s -> s.getUpcomingReleaseAt() == null));
    }

    /**
     * The half of the companion pair that mails people if it is wrong, and the reason the comparison is against the
     * bare subtraction rather than against the whole default rule: an organizer suppresses the reminder for a sensitive
     * session, somebody moves that session, and nothing goes out. Null is never the result of a subtraction, so a
     * stored null never compares equal and is always read as somebody's decision.
     */
    @Test
    void update_leavesADeliberateSuppressionOff() {

        leadDays(7L, 3);
        stubNoReleaseActivity(7L, 7L);
        givenScheduledSession(7L, NOW.plus(30, ChronoUnit.DAYS), null);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sut.update(7L, new UpdateSessionRequest(stated(NOW.plus(30, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS)), 90,
                "orga", null));

        verify(sessionRepository).save(argThat(s -> s.getUpcomingReleaseAt() == null));
    }

    /**
     * The same suppression, on the session whose own default is <em>also</em> past - created inside its lead window,
     * then switched off. Comparing the stored value against the whole default rule would make both sides null here,
     * read the organizer's "do not send" as untouched, and hand the session a fresh reminder on the way out of the lead
     * window. The bare subtraction ({@code NOW - 2d}) never equals null, so the decision survives.
     */
    @Test
    void update_leavesADeliberateSuppressionOffEvenWhenTheOldDefaultHadAlreadyPassed() {

        leadDays(7L, 3);
        stubNoReleaseActivity(7L, 7L);
        givenScheduledSession(7L, NOW.plus(1, ChronoUnit.DAYS), null);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sut.update(7L, new UpdateSessionRequest(stated(NOW.plus(30, ChronoUnit.DAYS)), 90, "orga", null));

        verify(sessionRepository).save(argThat(s -> s.getUpcomingReleaseAt() == null));
    }

    /**
     * The hazard the room field on the reschedule dialog made routine. An organizer opens that dialog on a session a
     * couple of days out purely to paste a meeting link, leaves the date exactly where it was, and confirms. The stored
     * instant still equals the bare subtraction, so it is "untouched" and recomputed - and the recompute runs the whole
     * default rule, which turns an instant already behind the clock into null. The reminder for the session most likely
     * to be given a room is switched off by an edit that never touched the schedule at all. A save that moves nothing
     * has nothing to decide - which it answers before the lead time is so much as read, so the community lookup is not
     * paid for either.
     */
    @Test
    void update_leavesAnUntouchedReminderAloneWhenTheScheduleDidNotMove() {

        stubNoReleaseActivity(7L, 7L);
        var scheduledAt = NOW.plus(2, ChronoUnit.DAYS);
        var untouched = scheduledAt.minus(3, ChronoUnit.DAYS);
        givenScheduledSession(7L, scheduledAt, untouched);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sut.update(7L, new UpdateSessionRequest(stated(scheduledAt), 90, "orga",
                new MeetingPlace(null, "https://meet.example/annex", null)));

        verify(sessionRepository).save(argThat(s -> untouched.equals(s.getUpcomingReleaseAt())));
        verify(communityAccess, never()).communicationSettings("default", 7L);
    }

    /**
     * The other thing the bare subtraction buys: an untouched intent that has already gone by still moves. It equals
     * the subtraction from the old schedule wherever the clock now sits, so it is recomputed rather than frozen as
     * deliberate and left below every future production watermark, never claimable again.
     */
    @Test
    void update_movesAnUntouchedReminderWhoseInstantHasAlreadyPassed() {

        leadDays(7L, 3);
        stubNoReleaseActivity(7L, 7L);
        givenScheduledSession(7L, NOW.plus(2, ChronoUnit.DAYS), NOW.minus(1, ChronoUnit.DAYS));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sut.update(7L, new UpdateSessionRequest(stated(NOW.plus(60, ChronoUnit.DAYS)), 90, "orga", null));

        verify(sessionRepository).save(argThat(s -> NOW.plus(57, ChronoUnit.DAYS).equals(s.getUpcomingReleaseAt())));
    }
}
