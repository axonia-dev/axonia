package dev.axonia.events.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.communication.api.MessageOutcomePort;
import dev.axonia.events.api.MeetingPlace;
import dev.axonia.events.api.ScheduleEntry;
import dev.axonia.events.api.SessionStatus;
import dev.axonia.events.application.port.out.AttendanceStore;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SectionTypeStore;
import dev.axonia.events.application.port.out.SeriesStore;
import dev.axonia.events.application.port.out.SessionListing;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.domain.CadenceUnit;
import dev.axonia.events.domain.PlannedSection;
import dev.axonia.events.domain.SectionTypeRecordMother;
import dev.axonia.events.domain.SeriesRecordMother;
import dev.axonia.events.domain.SeriesStatus;
import dev.axonia.events.domain.SessionRecordMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class ScheduleServiceTest {

    @Mock
    private SessionStore sessionStore;
    @Mock
    private SeriesStore seriesStore;
    @Mock
    private SessionListing sessionListing;
    @Mock
    private SectionTypeStore sectionTypeStore;
    @Mock
    private AttendanceStore attendanceStore;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private MessageOutcomePort messageOutcomePort;
    @Mock
    private OrganizationContext organizationContext;

    private ScheduleService sut;

    /**
     * The real mapper, kept as a field rather than a local: the month view's real rows are read through
     * {@link SessionListing}, which maps them itself, so every stub below hands the service exactly what this mapper
     * makes of the seeded entity. A mapping regression on a real row still fails these tests, and the ghost arm is
     * still computed independently - which is the whole point of the tests that assert the two arms agree.
     */
    private SessionMapper mapper;

    /** Fixed "now" for deterministic ghost projection — 2026-06-14 00:00 UTC. */
    private static final Instant NOW = Instant.parse("2026-06-14T00:00:00Z");

    @BeforeEach
    void wire() {

        mapper = new SessionMapper(communityAccess, messageOutcomePort,
                new SessionBatchLookups(sectionTypeStore, attendanceStore, seriesStore));
        sut = new ScheduleService(sessionStore, seriesStore, sessionListing, mapper, communityAccess,
                organizationContext);
    }

    @Test
    void schedule_activeWeeklySeries_projectsGhostsForFutureAnchors() {

        // given — series starts Tue 16 Jun, window covers Tue 16, 23, 30
        var series = SeriesRecordMother.any().id(10L).communityId(1L).cadenceCount(1).cadenceUnit(CadenceUnit.WEEK)
                .startsOn(LocalDate.parse("2026-06-16")).preferredTime(LocalTime.of(18, 0)).defaultStructure(List.of())
                .build();
        var from = Instant.parse("2026-06-14T00:00:00Z");
        var to = Instant.parse("2026-07-05T00:00:00Z");

        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("fe")).thenReturn(1L);
        when(sessionListing.page(between("default", 1L, from, to))).thenReturn(List.of());
        when(seriesStore.findByOrganizationIdAndCommunityId("default", 1L)).thenReturn(List.of(series));

        // when
        var result = sut.schedule("fe", from, to, NOW);

        // then
        assertThat(result).hasSize(3);
        assertThat(result).allMatch(ScheduleEntry::virtual);
        assertThat(result).allMatch(e -> e.status() == SessionStatus.SCHEDULED);
        assertThat(result).extracting(ScheduleEntry::occurrenceDate).containsExactly(LocalDate.parse("2026-06-16"),
                LocalDate.parse("2026-06-23"), LocalDate.parse("2026-06-30"));
    }

    @Test
    void schedule_realRowAtAnchorSuppressesGhost_andAppearsAsNonVirtual() {

        // given — real session on Jun 17; second anchor Jun 24 should ghost
        var seriesId = 10L;
        var anchor = LocalDate.parse("2026-06-17");
        var realSession = SessionRecordMother.any().id(99L).seriesId(seriesId).communityId(1L).occurrenceDate(anchor)
                .scheduledAt(Instant.parse("2026-06-17T18:00:00Z")).status("SCHEDULED").sections(List.of()).build();
        var series = SeriesRecordMother.any().id(seriesId).communityId(1L).cadenceCount(1).cadenceUnit(CadenceUnit.WEEK)
                .startsOn(LocalDate.parse("2026-06-17")).preferredTime(LocalTime.of(18, 0)).defaultStructure(List.of())
                .build();
        var from = Instant.parse("2026-06-14T00:00:00Z");
        var to = Instant.parse("2026-06-25T00:00:00Z");

        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("fe")).thenReturn(1L);
        when(sessionListing.page(between("default", 1L, from, to)))
                .thenAnswer(inv -> mapper.toSessions(List.of(realSession)));
        when(seriesStore.findByOrganizationIdAndCommunityId("default", 1L)).thenReturn(List.of(series));

        // when
        var result = sut.schedule("fe", from, to, NOW);

        // then — 1 real for Jun 17, 1 ghost for Jun 24
        assertThat(result).hasSize(2);
        var real = result.stream().filter(e -> !e.virtual()).toList();
        assertThat(real).hasSize(1);
        assertThat(real.get(0).sessionId()).isEqualTo(99L);
        assertThat(real.get(0).occurrenceDate()).isEqualTo(anchor);
        var ghosts = result.stream().filter(ScheduleEntry::virtual).toList();
        assertThat(ghosts).hasSize(1);
        assertThat(ghosts.get(0).occurrenceDate()).isEqualTo(LocalDate.parse("2026-06-24"));
    }

    /**
     * A ghost and the session it becomes have to agree on whether anybody chose an hour. The feed publishes both under
     * one stable UID, so an occurrence that read timed as a projection and untimed the moment somebody opened it would
     * change shape in every subscriber's calendar for a reason no subscriber could see. A ghost has no row to read the
     * fact from, so it applies the rule its own materialisation would - and the two can only agree while that rule is
     * stated in one place.
     */
    @Test
    void schedule_aGhostAndTheSessionItBecomesAgreeThatNobodyChoseAnHour() {

        var anchor = LocalDate.parse("2026-06-17");
        var materialised = SessionRecordMother.any().id(99L).seriesId(10L).communityId(1L).occurrenceDate(anchor)
                .scheduledAt(Instant.parse("2026-06-17T00:00:00Z")).hasTime(false).status("SCHEDULED")
                .sections(List.of()).build();
        var series = SeriesRecordMother.any().id(10L).communityId(1L).cadenceCount(1).cadenceUnit(CadenceUnit.WEEK)
                .startsOn(anchor).preferredTime(null).defaultStructure(List.of()).build();
        var from = Instant.parse("2026-06-14T00:00:00Z");
        var to = Instant.parse("2026-06-25T00:00:00Z");

        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("fe")).thenReturn(1L);
        when(sessionListing.page(between("default", 1L, from, to)))
                .thenAnswer(inv -> mapper.toSessions(List.of(materialised)));
        when(seriesStore.findByOrganizationIdAndCommunityId("default", 1L)).thenReturn(List.of(series));

        // when
        var result = sut.schedule("fe", from, to, NOW);

        // then - the materialised Jun 17 entry and the Jun 24 ghost read the same fact
        assertThat(result).extracting(ScheduleEntry::virtual).containsExactly(false, true);
        assertThat(result).noneMatch(ScheduleEntry::hasTime);
    }

    @Test
    void schedule_aSeriesWithAPreferredTimeProjectsAndMaterialisesEntriesThatSaySo() {

        var anchor = LocalDate.parse("2026-06-17");
        var materialised = SessionRecordMother.any().id(99L).seriesId(10L).communityId(1L).occurrenceDate(anchor)
                .scheduledAt(Instant.parse("2026-06-17T18:00:00Z")).hasTime(true).status("SCHEDULED")
                .sections(List.of()).build();
        var series = SeriesRecordMother.any().id(10L).communityId(1L).cadenceCount(1).cadenceUnit(CadenceUnit.WEEK)
                .startsOn(anchor).preferredTime(LocalTime.of(18, 0)).defaultStructure(List.of()).build();
        var from = Instant.parse("2026-06-14T00:00:00Z");
        var to = Instant.parse("2026-06-25T00:00:00Z");

        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("fe")).thenReturn(1L);
        when(sessionListing.page(between("default", 1L, from, to)))
                .thenAnswer(inv -> mapper.toSessions(List.of(materialised)));
        when(seriesStore.findByOrganizationIdAndCommunityId("default", 1L)).thenReturn(List.of(series));

        // when
        var result = sut.schedule("fe", from, to, NOW);

        // then
        assertThat(result).extracting(ScheduleEntry::virtual).containsExactly(false, true);
        assertThat(result).allMatch(ScheduleEntry::hasTime);
    }

    /**
     * A ghost and the session it becomes have to be read on the same clock. An editor opened from the calendar composes
     * the hour it is given in the zone the entry names, so an occurrence that reported its series' zone as a projection
     * and nothing at all the moment somebody materialised it would be written on the viewer's clock instead - moving a
     * community's session by a whole offset on the gesture that meant to move it by a day.
     */
    @Test
    void schedule_aGhostAndTheSessionItBecomesAgreeOnTheZoneTheirHourIsReadIn() {

        var seriesId = 10L;
        var anchor = LocalDate.parse("2026-06-17");
        var materialised = SessionRecordMother.any().id(99L).seriesId(seriesId).communityId(1L).occurrenceDate(anchor)
                .scheduledAt(Instant.parse("2026-06-17T16:00:00Z")).hasTime(true).status("SCHEDULED")
                .sections(List.of()).build();
        var series = SeriesRecordMother.any().id(seriesId).communityId(1L).cadenceCount(1).cadenceUnit(CadenceUnit.WEEK)
                .startsOn(anchor).preferredTime(LocalTime.of(18, 0)).timeZone("Europe/Paris")
                .defaultStructure(List.of()).build();
        var from = Instant.parse("2026-06-14T00:00:00Z");
        var to = Instant.parse("2026-06-25T00:00:00Z");

        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("fe")).thenReturn(1L);
        when(sessionListing.page(between("default", 1L, from, to)))
                .thenAnswer(inv -> mapper.toSessions(List.of(materialised)));
        when(seriesStore.findByOrganizationIdAndIdIn("default", Set.of(seriesId))).thenReturn(List.of(series));
        when(seriesStore.findByOrganizationIdAndCommunityId("default", 1L)).thenReturn(List.of(series));

        // when
        var result = sut.schedule("fe", from, to, NOW);

        // then - the materialised Jun 17 entry and the Jun 24 ghost name the same zone
        assertThat(result).extracting(ScheduleEntry::virtual).containsExactly(false, true);
        assertThat(result).extracting(ScheduleEntry::seriesTimeZone).containsOnly("Europe/Paris");
    }

    @Test
    void schedule_skippedRealRowSuppressesGhost_andSkippedEntryStillPresent() {

        // given — SKIPPED row on Jun 17 suppresses ghost; Jun 24 should ghost
        var seriesId = 10L;
        var anchor = LocalDate.parse("2026-06-17");
        var skipped = SessionRecordMother.any().id(55L).seriesId(seriesId).communityId(1L).occurrenceDate(anchor)
                .scheduledAt(Instant.parse("2026-06-17T18:00:00Z")).status("SKIPPED").sections(List.of()).build();
        var series = SeriesRecordMother.any().id(seriesId).communityId(1L).cadenceCount(1).cadenceUnit(CadenceUnit.WEEK)
                .startsOn(LocalDate.parse("2026-06-17")).preferredTime(LocalTime.of(18, 0)).defaultStructure(List.of())
                .build();
        var from = Instant.parse("2026-06-14T00:00:00Z");
        var to = Instant.parse("2026-06-25T00:00:00Z");

        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("fe")).thenReturn(1L);
        when(sessionListing.page(between("default", 1L, from, to)))
                .thenAnswer(inv -> mapper.toSessions(List.of(skipped)));
        when(seriesStore.findByOrganizationIdAndCommunityId("default", 1L)).thenReturn(List.of(series));

        // when
        var result = sut.schedule("fe", from, to, NOW);

        // then
        var skippedEntries = result.stream().filter(e -> e.status() == SessionStatus.SKIPPED).toList();
        assertThat(skippedEntries).hasSize(1);
        assertThat(skippedEntries.get(0).sessionId()).isEqualTo(55L);
        assertThat(skippedEntries.get(0).virtual()).isFalse();
        // Ghost for Jun 17 suppressed, ghost for Jun 24 projected
        var ghosts = result.stream().filter(ScheduleEntry::virtual).toList();
        assertThat(ghosts).hasSize(1);
        assertThat(ghosts.get(0).occurrenceDate()).isEqualTo(LocalDate.parse("2026-06-24"));
    }

    @Test
    void schedule_anchorsBeforeTodayAreNotProjected() {

        // given — startsOn Jun 1 (WEEKLY): anchors in [Jun14,Jun29) window are Jun 15, 22
        // past anchors Jun 1, 8 are before today (Jun 14) and must be skipped
        var series = SeriesRecordMother.any().id(10L).communityId(1L).cadenceCount(1).cadenceUnit(CadenceUnit.WEEK)
                .startsOn(LocalDate.parse("2026-06-01")).defaultStructure(List.of()).build();
        var from = Instant.parse("2026-06-01T00:00:00Z");
        var to = Instant.parse("2026-06-29T00:00:00Z");

        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("fe")).thenReturn(1L);
        when(sessionListing.page(between("default", 1L, from, to))).thenReturn(List.of());
        when(seriesStore.findByOrganizationIdAndCommunityId("default", 1L)).thenReturn(List.of(series));

        // when — NOW is 2026-06-14, so today = Jun 14; fromDate becomes Jun 14
        var result = sut.schedule("fe", from, to, NOW);

        // then — Jun 15 and Jun 22 only (Jun 14 not a series anchor, Jun 1/8 before today)
        assertThat(result).hasSize(2);
        assertThat(result).extracting(ScheduleEntry::occurrenceDate).containsExactly(LocalDate.parse("2026-06-15"),
                LocalDate.parse("2026-06-22"));
    }

    @Test
    void schedule_pausedOrEndedSeriesProjectNoGhosts() {

        // given
        var paused = SeriesRecordMother.any().id(10L).communityId(1L).status(SeriesStatus.PAUSED)
                .startsOn(LocalDate.parse("2026-06-16")).defaultStructure(List.of()).build();
        var ended = SeriesRecordMother.any().id(11L).communityId(1L).status(SeriesStatus.ENDED)
                .startsOn(LocalDate.parse("2026-06-16")).defaultStructure(List.of()).build();
        var from = Instant.parse("2026-06-14T00:00:00Z");
        var to = Instant.parse("2026-06-30T00:00:00Z");

        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("fe")).thenReturn(1L);
        when(sessionListing.page(between("default", 1L, from, to))).thenReturn(List.of());
        when(seriesStore.findByOrganizationIdAndCommunityId("default", 1L)).thenReturn(List.of(paused, ended));

        // when
        var result = sut.schedule("fe", from, to, NOW);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void schedule_ghostSectionsMatchProjectedStructure() {

        // given
        when(sectionTypeStore.findByOrganizationIdAndIdIn("default", Set.of(1L)))
                .thenReturn(List.of(SectionTypeRecordMother.any().id(1L).label("Lightning Talk").build()));

        var series = SeriesRecordMother.any().id(10L).communityId(1L).cadenceCount(1).cadenceUnit(CadenceUnit.WEEK)
                .startsOn(LocalDate.parse("2026-06-17")).preferredTime(LocalTime.of(18, 0))
                .defaultStructure(List
                        .of(PlannedSection.builder().sectionTypeId(1L).title("Main talk").durationMinutes(30).build()))
                .build();
        var from = Instant.parse("2026-06-14T00:00:00Z");
        var to = Instant.parse("2026-06-18T00:00:00Z"); // one anchor: Jun 17 (Jun 24 outside window)

        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("fe")).thenReturn(1L);
        when(sessionListing.page(between("default", 1L, from, to))).thenReturn(List.of());
        when(seriesStore.findByOrganizationIdAndCommunityId("default", 1L)).thenReturn(List.of(series));

        // when
        var result = sut.schedule("fe", from, to, NOW);

        // then
        assertThat(result).hasSize(1);
        var entry = result.get(0);
        assertThat(entry.virtual()).isTrue();
        assertThat(entry.sections()).hasSize(1);
        assertThat(entry.sections().get(0).sectionTypeId()).isEqualTo(1L);
        assertThat(entry.sections().get(0).sectionType()).isEqualTo("Lightning Talk");
        assertThat(entry.sections().get(0).title()).isEqualTo("Main talk");
        assertThat(entry.sections().get(0).durationMinutes()).isEqualTo(30);
        assertThat(entry.sections().get(0).slots()).isEmpty();
    }

    @Test
    void schedule_publicOverloadDelegatesToTimestampedVariant() {

        // given — a window entirely in the future, so now() lands before the from boundary
        var series = SeriesRecordMother.any().id(10L).communityId(1L).cadenceCount(1).cadenceUnit(CadenceUnit.WEEK)
                .startsOn(LocalDate.parse("2099-01-07")).preferredTime(LocalTime.of(18, 0)).defaultStructure(List.of())
                .build();
        var from = Instant.parse("2099-01-01T00:00:00Z");
        var to = Instant.parse("2099-02-01T00:00:00Z");

        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("fe")).thenReturn(1L);
        when(sessionListing.page(between("default", 1L, from, to))).thenReturn(List.of());
        when(seriesStore.findByOrganizationIdAndCommunityId("default", 1L)).thenReturn(List.of(series));

        // when — public method (no explicit Instant): exercises ScheduleService#schedule(slug, from, to)
        var result = sut.schedule("fe", from, to);

        // then — ghosts projected; exact count not load-bearing, just verify delegation worked
        assertThat(result).isNotEmpty();
    }

    /**
     * The regression lock for the series zone column: a ghost's {@code scheduledAt} must be composed in the series' own
     * zone, not {@code UTC}. A Europe/Zurich series at 18:00 sits at 16:00Z before the autumn transition and 17:00Z
     * after it - either value staying at 18:00Z (the pre-column behaviour) or staying fixed across both anchors would
     * mean {@link ScheduleService#toGhost} silently reverted to a hard-coded zone.
     */
    @Test
    void schedule_projectsGhostInTheSeriesOwnZone_notUtc() {

        // given — quarterly Europe/Zurich series at 18:00: one anchor before the autumn transition, one after
        var series = SeriesRecordMother.any().id(20L).communityId(1L).cadenceCount(3).cadenceUnit(CadenceUnit.MONTH)
                .startsOn(LocalDate.parse("2026-08-18")).preferredTime(LocalTime.of(18, 0)).timeZone("Europe/Zurich")
                .defaultStructure(List.of()).build();
        var from = Instant.parse("2026-08-01T00:00:00Z");
        var to = Instant.parse("2026-12-01T00:00:00Z");
        var now = Instant.parse("2026-08-01T00:00:00Z");

        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("fe")).thenReturn(1L);
        when(sessionListing.page(between("default", 1L, from, to))).thenReturn(List.of());
        when(seriesStore.findByOrganizationIdAndCommunityId("default", 1L)).thenReturn(List.of(series));

        // when
        var result = sut.schedule("fe", from, to, now);

        // then - 18:00 Zurich is 16:00Z in August (CEST) and 17:00Z in November (CET), never 18:00Z
        assertThat(result).extracting(ScheduleEntry::scheduledAt).containsExactly(Instant.parse("2026-08-18T16:00:00Z"),
                Instant.parse("2026-11-18T17:00:00Z"));
    }

    @Test
    void schedule_ghostInheritsTheSeriesStandingPlace() {

        // given - a weekly series with a standing place, no real rows, one anchor in the window
        var series = SeriesRecordMother.any().id(10L).communityId(1L).cadenceCount(1).cadenceUnit(CadenceUnit.WEEK)
                .startsOn(LocalDate.parse("2026-06-17")).preferredTime(LocalTime.of(18, 0))
                .joinUrl("https://meet.example/dojo").updatedAt(Instant.parse("2026-06-01T09:00:00Z"))
                .defaultStructure(List.of()).build();
        var from = Instant.parse("2026-06-14T00:00:00Z");
        var to = Instant.parse("2026-06-18T00:00:00Z");

        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("fe")).thenReturn(1L);
        when(sessionListing.page(between("default", 1L, from, to))).thenReturn(List.of());
        when(seriesStore.findByOrganizationIdAndCommunityId("default", 1L)).thenReturn(List.of(series));

        // when
        var result = sut.schedule("fe", from, to, NOW);

        // then - the place comes from the series, and the feed's revision fields describe the series, not a row
        assertThat(result).singleElement().satisfies(entry -> {
            assertThat(entry.virtual()).isTrue();
            assertThat(entry.place().joinUrl()).isEqualTo("https://meet.example/dojo");
            assertThat(entry.version()).isZero();
            assertThat(entry.updatedAt()).isEqualTo(Instant.parse("2026-06-01T09:00:00Z"));
        });
    }

    @Test
    void schedule_realEntryCarriesTheResolvedPlaceAndTheRowsOwnRevision() {

        // given - a session that overrides its series' standing place for this one week
        var seriesId = 10L;
        var anchor = LocalDate.parse("2026-06-17");
        var real = SessionRecordMother.any().id(99L).seriesId(seriesId).communityId(1L).occurrenceDate(anchor)
                .scheduledAt(Instant.parse("2026-06-17T18:00:00Z")).status("SCHEDULED")
                .joinUrl("https://meet.example/this-week").version(4L).updatedAt(Instant.parse("2026-06-10T12:00:00Z"))
                .sections(List.of()).build();
        var series = SeriesRecordMother.any().id(seriesId).communityId(1L).cadenceCount(1).cadenceUnit(CadenceUnit.WEEK)
                .startsOn(anchor).preferredTime(LocalTime.of(18, 0)).joinUrl("https://meet.example/dojo")
                .defaultStructure(List.of()).build();
        var from = Instant.parse("2026-06-14T00:00:00Z");
        var to = Instant.parse("2026-06-18T00:00:00Z");

        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("fe")).thenReturn(1L);
        when(sessionListing.page(between("default", 1L, from, to))).thenAnswer(inv -> mapper.toSessions(List.of(real)));
        when(seriesStore.findByOrganizationIdAndIdIn("default", Set.of(seriesId))).thenReturn(List.of(series));
        when(seriesStore.findByOrganizationIdAndCommunityId("default", 1L)).thenReturn(List.of(series));

        // when
        var result = sut.schedule("fe", from, to, NOW);

        // then - the override wins over the standing place, and the revision fields are the row's own
        assertThat(result).singleElement().satisfies(entry -> {
            assertThat(entry.virtual()).isFalse();
            assertThat(entry.place().joinUrl()).isEqualTo("https://meet.example/this-week");
            assertThat(entry.version()).isEqualTo(4L);
            assertThat(entry.updatedAt()).isEqualTo(Instant.parse("2026-06-10T12:00:00Z"));
        });
    }

    /**
     * A blank standing place is no place, on the projected occurrence exactly as on the real one. The two arms resolve
     * the place in different places - {@link SessionMapper} for a real row, {@link ScheduleService#toGhost} for a ghost
     * - so a ghost that reported the stored value verbatim would put two entries of the same series on the same
     * schedule disagreeing about whether a place exists at all. The calendar feed branches on exactly that, and would
     * publish a location pointing at nothing for half of them.
     */
    @Test
    void schedule_aBlankStandingPlaceIsNoPlaceOnEveryEntry() {

        // given - a series whose place was cleared in the edit form, with one real row and one ghost in the window
        var seriesId = 10L;
        var real = SessionRecordMother.any().id(99L).seriesId(seriesId).communityId(1L)
                .occurrenceDate(LocalDate.parse("2026-06-17")).scheduledAt(Instant.parse("2026-06-17T18:00:00Z"))
                .status("SCHEDULED").sections(List.of()).build();
        var series = SeriesRecordMother.any().id(seriesId).communityId(1L).cadenceCount(1).cadenceUnit(CadenceUnit.WEEK)
                .startsOn(LocalDate.parse("2026-06-17")).preferredTime(LocalTime.of(18, 0)).joinUrl("")
                .defaultStructure(List.of()).build();
        var from = Instant.parse("2026-06-14T00:00:00Z");
        var to = Instant.parse("2026-06-25T00:00:00Z");

        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("fe")).thenReturn(1L);
        when(sessionListing.page(between("default", 1L, from, to))).thenAnswer(inv -> mapper.toSessions(List.of(real)));
        when(seriesStore.findByOrganizationIdAndIdIn("default", Set.of(seriesId))).thenReturn(List.of(series));
        when(seriesStore.findByOrganizationIdAndCommunityId("default", 1L)).thenReturn(List.of(series));

        // when
        var result = sut.schedule("fe", from, to, NOW);

        // then - both the real entry and the ghost say "no place", never the empty string
        assertThat(result).hasSize(2);
        assertThat(result).extracting(ScheduleEntry::place).containsOnly(MeetingPlace.NONE);
    }

    /**
     * The calendar feed is authorized by a token and runs with no JWT at all, so the org-explicit entry point must
     * never reach for an organization context that would throw for it.
     */
    @Test
    void schedule_orgExplicitQuery_neverConsultsTheJwtOrganizationContext() {

        // given
        var series = SeriesRecordMother.any().id(10L).communityId(1L).cadenceCount(1).cadenceUnit(CadenceUnit.WEEK)
                .startsOn(LocalDate.parse("2026-06-16")).preferredTime(LocalTime.of(18, 0)).defaultStructure(List.of())
                .build();
        var from = Instant.parse("2026-06-14T00:00:00Z");
        var to = Instant.parse("2026-07-05T00:00:00Z");

        when(sessionListing.page(between("northwind", 1L, from, to))).thenReturn(List.of());
        when(seriesStore.findByOrganizationIdAndCommunityId("northwind", 1L)).thenReturn(List.of(series));

        // when
        var result = sut.schedule(
                ScheduleQuery.builder().organizationId("northwind").communityId(1L).from(from).to(to).now(NOW).build());

        // then
        assertThat(result).hasSize(3);
        verifyNoInteractions(organizationContext);
    }

    @Test
    void activeMonths_resolvesSlugAndDelegatesToRepository() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("backend-guild")).thenReturn(42L);
        when(sessionStore.findActiveMonths("default", 42L)).thenReturn(List.of("2024-06", "2026-07"));

        // when
        var months = sut.activeMonths("backend-guild");

        // then
        assertThat(months).containsExactly("2024-06", "2026-07");
    }

    /**
     * The month view's window, as the service states it. Mapped lazily by {@code thenAnswer} above rather than eagerly
     * by {@code thenReturn}, so a stub declared here does not depend on the series stubs below it having been declared
     * first.
     */
    private SessionPageQuery between(String organizationId, Long communityId, Instant from, Instant to) {

        return SessionPageQuery.builder().organizationId(organizationId).communityId(communityId).from(from).to(to)
                .window(SessionPageQuery.Window.BETWEEN).build();
    }
}
