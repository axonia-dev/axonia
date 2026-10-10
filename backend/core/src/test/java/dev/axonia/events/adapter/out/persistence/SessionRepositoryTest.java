package dev.axonia.events.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.events.api.SessionStatus;
import dev.axonia.events.application.PastSessionsQuery;

import static org.assertj.core.api.Assertions.assertThat;

class SessionRepositoryTest extends CoreJdbcTestSupport {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SeriesRepository seriesRepository;

    @Autowired
    private SectionTypeRepository sectionTypeRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private Long communityId() {

        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES ('default', 'GuildS', now(), now(), 0) ON CONFLICT DO NOTHING");
        return jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, updated_at, version) "
                        + "SELECT 'default','sess-' || nextval('communities_id_seq'),'S', k.id,'','ACTIVE', now(), now(), 0 "
                        + "FROM community_kind k WHERE k.organization_id='default' AND k.label='GuildS' RETURNING id",
                Long.class);
    }

    /**
     * A community whose upcoming/wrap-up communication settings are set explicitly, for the three new due-work queries.
     */
    private Long communityId(boolean notifyUpcoming, int leadDays, boolean notifyWrapUp) {

        var id = communityId();
        jdbc.update("UPDATE communities SET notify_session_upcoming = ?, session_upcoming_lead_days = ?, "
                + "notify_session_wrap_up = ? WHERE id = ?", notifyUpcoming, leadDays, notifyWrapUp, id);
        return id;
    }

    /** The series most tests need and don't care about the shape of beyond belonging to {@code communityId}. */
    private Long seriesId(Long communityId) {

        return seriesRepository.save(SeriesEntityMother.any().communityId(communityId)
                .startsOn(LocalDate.parse("2026-06-03")).defaultStructure(List.of()).build()).getId();
    }

    private Long saveScheduled(Long seriesId, Long communityId, Instant scheduledAt) {

        return saveScheduled(seriesId, communityId, scheduledAt, null);
    }

    private Long saveScheduled(Long seriesId, Long communityId, Instant scheduledAt, Instant upcomingReleaseAt) {

        return saveWithUpcomingIntent(seriesId, communityId, "SCHEDULED", scheduledAt, upcomingReleaseAt);
    }

    private Long saveWithUpcomingIntent(Long seriesId, Long communityId, String status, Instant scheduledAt,
            Instant upcomingReleaseAt) {

        return sessionRepository
                .save(SessionEntity.builder().organizationId("default").seriesId(seriesId).communityId(communityId)
                        .status(status).scheduledAt(scheduledAt)
                        .occurrenceDate(scheduledAt.atOffset(java.time.ZoneOffset.UTC).toLocalDate())
                        .upcomingReleaseAt(upcomingReleaseAt).notes("").recordingUrl("").sections(List.of()).build())
                .getId();
    }

    private Long saveHeld(Long seriesId, Long communityId, Instant scheduledAt, Instant wrapUpReleaseAt) {

        return sessionRepository
                .save(SessionEntity.builder().organizationId("default").seriesId(seriesId).communityId(communityId)
                        .status("HELD").scheduledAt(scheduledAt)
                        .occurrenceDate(scheduledAt.atOffset(java.time.ZoneOffset.UTC).toLocalDate())
                        .wrapUpReleaseAt(wrapUpReleaseAt).notes("").recordingUrl("").sections(List.of()).build())
                .getId();
    }

    @Test
    void save_roundTripsSectionsSlotsAndPresenters() {

        // given
        var communityId = communityId();
        var sectionTypeId = sectionTypeRepository.save(SectionTypeEntityMother.any().organizationId("default").build())
                .getId();
        var seriesId = seriesId(communityId);

        var session = SessionEntity.builder().organizationId("default").seriesId(seriesId).communityId(communityId)
                .status("SCHEDULED").scheduledAt(Instant.parse("2099-01-01T10:00:00Z"))
                .occurrenceDate(LocalDate.of(2099, 1, 1)).notes(
                        "")
                .recordingUrl(
                        "")
                .sections(List.of(SectionEntity.builder().sectionTypeId(sectionTypeId).title("Talks")
                        .slots(List.of(SlotEntity.builder().title("Talk A").durationMinutes(20)
                                .presenters(Set.of(SlotPresenterRef.builder().userId("alice").build()))
                                .tags(Set.of(SlotTagRef.builder().tag("kafka").build())).build(),
                                SlotEntity.builder().title("Open slot").durationMinutes(15).presenters(Set.of())
                                        .build()))
                        .build(),
                        SectionEntity.builder().sectionTypeId(sectionTypeId).title("Round table").slots(List.of())
                                .build()))
                .build();

        // when
        var saved = sessionRepository.save(session);
        var reloaded = sessionRepository.findByOrganizationIdAndId("default", saved.getId()).orElseThrow();

        // then - order preserved across both levels; round-table section keeps zero slots
        assertThat(reloaded.getSections()).extracting(SectionEntity::getTitle).containsExactly("Talks", "Round table");
        assertThat(reloaded.getSections().get(0).getSlots()).extracting(SlotEntity::getTitle).containsExactly("Talk A",
                "Open slot");
        assertThat(reloaded.getSections().get(0).getSlots().get(0).getDurationMinutes()).isEqualTo(20);
        assertThat(reloaded.getSections().get(0).getSlots().get(1).getDurationMinutes()).isEqualTo(15);
        assertThat(reloaded.getSections().get(0).getSlots().get(0).getPresenters())
                .extracting(SlotPresenterRef::getUserId).containsExactly("alice");
        assertThat(reloaded.getSections().get(0).getSlots().get(1).getPresenters()).isEmpty();
        assertThat(reloaded.getSections().get(1).getSlots()).isEmpty();
        // a slot that was never proposed carries its own subject; one that carries none round-trips as empty
        assertThat(reloaded.getSections().get(0).getSlots().get(0).getTags()).extracting(SlotTagRef::getTag)
                .containsExactly("kafka");
        assertThat(reloaded.getSections().get(0).getSlots().get(1).getTags()).isEmpty();
    }

    /**
     * The upcoming notice's release intent is a stored column like the wrap-up's, so it has to survive the round trip
     * in both of its meanings: an instant, and the deliberate null for a session that sends no reminder.
     */
    @Test
    void save_roundTripsTheUpcomingReleaseIntentIncludingItsAbsence() {

        // given
        var communityId = communityId();
        var seriesId = seriesId(communityId);
        var releaseAt = Instant.parse("2099-01-01T10:00:00Z");
        var withIntent = SessionEntity.builder().organizationId("default").seriesId(seriesId).communityId(communityId)
                .status("SCHEDULED").scheduledAt(Instant.parse("2099-01-04T10:00:00Z"))
                .occurrenceDate(LocalDate.of(2099, 1, 4)).upcomingReleaseAt(releaseAt).notes("").recordingUrl("")
                .sections(List.of()).build();
        var withoutIntent = SessionEntity.builder().organizationId("default").seriesId(seriesId)
                .communityId(communityId).status("SCHEDULED").scheduledAt(Instant.parse("2099-01-05T10:00:00Z"))
                .occurrenceDate(LocalDate.of(2099, 1, 5)).notes("").recordingUrl("").sections(List.of()).build();

        // when
        var storedId = sessionRepository.save(withIntent).getId();
        var absentId = sessionRepository.save(withoutIntent).getId();

        // then
        assertThat(
                sessionRepository.findByOrganizationIdAndId("default", storedId).orElseThrow().getUpcomingReleaseAt())
                .isEqualTo(releaseAt);
        assertThat(
                sessionRepository.findByOrganizationIdAndId("default", absentId).orElseThrow().getUpcomingReleaseAt())
                .isNull();
    }

    /**
     * The rows themselves are read by {@code SessionListReader} and pinned by {@code SessionListReaderTest}; what stays
     * here is the count beside them, which is still the repository's.
     */
    @Test
    void countUpcoming_countsFutureNonCancelledOnly() {

        // given
        var communityId = communityId();
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        save(seriesId, communityId, "SCHEDULED", Instant.parse("2099-01-01T10:00:00Z")); // future, counts
        save(seriesId, communityId, "SCHEDULED", Instant.parse("2000-01-01T10:00:00Z")); // past/held, excluded
        save(seriesId, communityId, "CANCELLED", Instant.parse("2099-02-01T10:00:00Z")); // cancelled, excluded

        // when / then
        assertThat(sessionRepository.countUpcoming("default", communityId, now)).isEqualTo(1L);
    }

    private void save(Long seriesId, Long communityId, String status, Instant at) {

        sessionRepository.save(SessionEntity.builder().organizationId("default").seriesId(seriesId)
                .communityId(communityId).status(status).scheduledAt(at)
                .occurrenceDate(at.atOffset(java.time.ZoneOffset.UTC).toLocalDate()).notes("").recordingUrl("")
                .sections(List.of()).build());
    }

    @Test
    void findPendingForSeriesExcluding_excludesASessionAlreadyInThePast() {

        // given
        var communityId = communityId();
        var seriesId = seriesId(communityId);
        sessionRepository.save(SessionEntity.builder().organizationId("default").seriesId(seriesId)
                .communityId(communityId).status("SCHEDULED").scheduledAt(Instant.parse("2000-01-01T10:00:00Z"))
                .occurrenceDate(LocalDate.of(2000, 1, 1)).notes("").recordingUrl("").sections(List.of()).build());

        // when - excludeSessionId (-1) cannot match anything, isolating the scheduled_at >= now predicate
        var actual = sessionRepository.findPendingForSeriesExcluding("default", seriesId, -1L,
                Instant.parse("2026-06-08T00:00:00Z"));

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void countPast_countsHeldNonCancelledOnly() {

        // given
        var communityId = communityId();
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        save(seriesId, communityId, "SCHEDULED", Instant.parse("2026-06-01T10:00:00Z")); // held, counts
        save(seriesId, communityId, "SCHEDULED", Instant.parse("2026-05-01T10:00:00Z")); // older held, counts
        save(seriesId, communityId, "SCHEDULED", Instant.parse("2099-01-01T10:00:00Z")); // upcoming, excluded
        save(seriesId, communityId, "CANCELLED", Instant.parse("2026-05-15T10:00:00Z")); // cancelled, excluded

        // when / then
        assertThat(sessionRepository.countPast(
                PastSessionsQuery.builder().organizationId("default").communityId(communityId).now(now).build()))
                .isEqualTo(2L);
    }

    /** The count has to narrow exactly as the page does, or a status-filtered page reports a total it does not hold. */
    @Test
    void countPast_honoursTheAskedStatus() {

        // given
        var communityId = communityId();
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        save(seriesId, communityId, "HELD", Instant.parse("2026-06-01T10:00:00Z")); // counts
        save(seriesId, communityId, "SKIPPED", Instant.parse("2026-06-05T10:00:00Z")); // excluded
        save(seriesId, communityId, "SCHEDULED", Instant.parse("2026-06-06T10:00:00Z")); // excluded

        // when / then
        assertThat(sessionRepository.countPast(PastSessionsQuery.builder().organizationId("default")
                .communityId(communityId).now(now).status(SessionStatus.HELD).build())).isEqualTo(1L);
    }

    @Test
    void countPast_honoursFromToWindow() {

        // given
        var communityId = communityId();
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        save(seriesId, communityId, "SCHEDULED", Instant.parse("2026-03-01T10:00:00Z")); // before window
        save(seriesId, communityId, "SCHEDULED", Instant.parse("2026-04-15T10:00:00Z")); // inside window
        save(seriesId, communityId, "SCHEDULED", Instant.parse("2026-06-01T10:00:00Z")); // after window

        // when / then - from inclusive, to exclusive
        assertThat(sessionRepository
                .countPast(PastSessionsQuery.builder().organizationId("default").communityId(communityId).now(now)
                        .from(Instant.parse("2026-04-01T00:00:00Z")).to(Instant.parse("2026-05-01T00:00:00Z")).build()))
                .isEqualTo(1L);
    }

    @Test
    void findActiveMonths_returnsDistinctYearMonthsAscending() {

        // given - two sessions in 2024-06 (one CANCELLED), one in 2022-03, one future 2027-01
        var communityId = communityId();
        var seriesId = seriesRepository.save(SeriesEntityMother.any().communityId(communityId)
                .startsOn(LocalDate.parse("2022-01-01")).defaultStructure(List.of()).build()).getId();
        saveSession(communityId, seriesId, "SCHEDULED", "2024-06-05");
        saveSession(communityId, seriesId, "CANCELLED", "2024-06-20");
        saveSession(communityId, seriesId, "HELD", "2022-03-11");
        saveSession(communityId, seriesId, "SCHEDULED", "2027-01-09");

        // when
        var months = sessionRepository.findActiveMonths("default", communityId);

        // then - distinct, ascending, cancelled month still counts, one entry per month
        assertThat(months).containsExactly("2022-03", "2024-06", "2027-01");
    }

    @Test
    void findActiveMonths_isEmptyForCommunityWithoutSessions() {

        var communityId = communityId();

        assertThat(sessionRepository.findActiveMonths("default", communityId)).isEmpty();
    }

    @Test
    void findActiveMonths_isScopedToTheGivenCommunity() {

        // given two communities under the same org, each with a session in a different month
        var communityA = communityId();
        var communityB = communityId();
        var seriesA = seriesRepository.save(SeriesEntityMother.any().communityId(communityA)
                .startsOn(LocalDate.parse("2024-01-01")).defaultStructure(List.of()).build()).getId();
        var seriesB = seriesRepository.save(SeriesEntityMother.any().communityId(communityB)
                .startsOn(LocalDate.parse("2023-01-01")).defaultStructure(List.of()).build()).getId();
        saveSession(communityA, seriesA, "SCHEDULED", "2024-06-05");
        saveSession(communityB, seriesB, "SCHEDULED", "2023-02-11");

        // when / then - A only sees its own month, not B's
        assertThat(sessionRepository.findActiveMonths("default", communityA)).containsExactly("2024-06");
        assertThat(sessionRepository.findActiveMonths("default", communityB)).containsExactly("2023-02");
    }

    /**
     * The stored intent is what makes a notice due, and the community's lead time no longer enters the query at all.
     * Both halves are asserted at once: this community announces thirty days ahead, so the instant the old derived
     * expression would have computed sits twenty-eight days behind {@code since} and could never match, while the
     * session's own stored intent is a minute old and does.
     */
    @Test
    void findUpcomingNoticeSessionIds_matchesAScheduledSessionPastItsOwnReleaseInstant() {

        // given
        var communityId = communityId(true, 30, true);
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        var since = now.minus(5, ChronoUnit.MINUTES);
        var dueId = saveScheduled(seriesId, communityId, now.plus(2, ChronoUnit.DAYS),
                now.minus(1, ChronoUnit.MINUTES));

        // when
        var actual = sessionRepository.findUpcomingNoticeSessionIds("default", now, since);

        // then
        assertThat(actual).containsExactly(dueId);
    }

    @Test
    void findUpcomingNoticeSessionIds_excludesASessionNotYetPastItsReleaseInstant() {

        // given
        var communityId = communityId(true, 3, true);
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        saveScheduled(seriesId, communityId, now.plus(2, ChronoUnit.DAYS), now.plus(1, ChronoUnit.HOURS));

        // when / then
        assertThat(sessionRepository.findUpcomingNoticeSessionIds("default", now, Instant.EPOCH)).isEmpty();
    }

    /**
     * The null: a session carrying no intent sends nothing, and no clock-derived default rescues it. The lead time here
     * would have made this session due under the expression this query used to compute, which is exactly the point -
     * the silence is now the stored decision, not arithmetic.
     */
    @Test
    void findUpcomingNoticeSessionIds_excludesASessionWithNoReleaseIntent() {

        // given
        var communityId = communityId(true, 3, true);
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        saveScheduled(seriesId, communityId, now.plus(2, ChronoUnit.DAYS), null);

        // when / then
        assertThat(sessionRepository.findUpcomingNoticeSessionIds("default", now, Instant.EPOCH)).isEmpty();
    }

    @Test
    void findUpcomingNoticeSessionIds_excludesWhenTheCommunityHasOptedOut() {

        // given
        var communityId = communityId(false, 3, true);
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        saveScheduled(seriesId, communityId, now.plus(2, ChronoUnit.DAYS), now.minus(1, ChronoUnit.HOURS));

        // when / then
        assertThat(sessionRepository.findUpcomingNoticeSessionIds("default", now, Instant.EPOCH)).isEmpty();
    }

    /**
     * The {@code status = 'SCHEDULED'} filter, which nothing else here holds and which three other decisions lean on.
     * V33 stamps a future occurrence that happens to be skipped today precisely so unskipping it restores its reminder,
     * and that is only safe because this query refuses to announce a session nobody intends to hold. Widen the status
     * and every member of the community is mailed "this is coming up" about an occurrence that was called off.
     */
    @Test
    void findUpcomingNoticeSessionIds_excludesASkippedOccurrence() {

        // given - squarely inside the window on every other count: opted in, future, intent an hour old
        var communityId = communityId(true, 3, true);
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        saveWithUpcomingIntent(seriesId, communityId, "SKIPPED", now.plus(2, ChronoUnit.DAYS),
                now.minus(1, ChronoUnit.HOURS));

        // when / then
        assertThat(sessionRepository.findUpcomingNoticeSessionIds("default", now, Instant.EPOCH)).isEmpty();
    }

    @Test
    void findUpcomingNoticeSessionIds_excludesAPastSession() {

        // given - already started, must not renotify: its intent is squarely inside the window, and only the
        // scheduled_at guard keeps it quiet.
        var communityId = communityId(true, 3, true);
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        saveScheduled(seriesId, communityId, now.minus(1, ChronoUnit.DAYS), now.minus(1, ChronoUnit.HOURS));

        // when / then
        assertThat(sessionRepository.findUpcomingNoticeSessionIds("default", now, Instant.EPOCH)).isEmpty();
    }

    /**
     * The watermark boundary: a release instant exactly at {@code since} was already claimed by whichever earlier tick
     * advanced the watermark to that exact instant, so it must not match again - exclusive, not inclusive.
     */
    @Test
    void findUpcomingNoticeSessionIds_excludesAReleaseInstantExactlyAtTheSinceWatermark() {

        // given
        var communityId = communityId(true, 3, true);
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        var since = now.minus(1, ChronoUnit.HOURS);
        saveScheduled(seriesId, communityId, now.plus(2, ChronoUnit.DAYS), since);

        // when / then
        assertThat(sessionRepository.findUpcomingNoticeSessionIds("default", now, since)).isEmpty();
    }

    /** The inside arm: a release instant one minute past the watermark is claimed. */
    @Test
    void findUpcomingNoticeSessionIds_matchesAReleaseInstantJustPastTheSinceWatermark() {

        // given
        var communityId = communityId(true, 3, true);
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        var since = now.minus(1, ChronoUnit.HOURS);
        var dueId = saveScheduled(seriesId, communityId, now.plus(2, ChronoUnit.DAYS),
                since.plus(1, ChronoUnit.MINUTES));

        // when
        var actual = sessionRepository.findUpcomingNoticeSessionIds("default", now, since);

        // then
        assertThat(actual).containsExactly(dueId);
    }

    /**
     * The closed end of the {@code (since, now]} window the javadoc advertises. An intent stored at exactly a tick's
     * own {@code now} must be claimed by that tick, because the tick then advances the watermark to that same instant
     * and the next one's exclusive {@code > :since} would exclude it forever. An off-by-one here loses a reminder the
     * organizer explicitly scheduled, silently and permanently, with no error anywhere.
     */
    @Test
    void findUpcomingNoticeSessionIds_matchesAReleaseInstantExactlyAtNow() {

        // given
        var communityId = communityId(true, 3, true);
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        var dueId = saveScheduled(seriesId, communityId, now.plus(2, ChronoUnit.DAYS), now);

        // when
        var actual = sessionRepository.findUpcomingNoticeSessionIds("default", now, now.minus(1, ChronoUnit.HOURS));

        // then
        assertThat(actual).containsExactly(dueId);
    }

    private static final Duration THIRTY_DAYS = Duration.ofDays(30);

    @Test
    void findWrapUpSessionIds_matchesAHeldSessionPastItsOwnReleaseInstant() {

        // given
        var communityId = communityId(true, 3, true);
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        var dueId = saveHeld(seriesId, communityId, now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS));

        // when
        var actual = sessionRepository.findWrapUpSessionIds("default", now, now.minus(THIRTY_DAYS));

        // then
        assertThat(actual).containsExactly(dueId);
    }

    /**
     * The wrap-up's own closed end, for the reason
     * {@code findUpcomingNoticeSessionIds_matchesAReleaseInstantExactlyAtNow} spells out: a release instant landing
     * exactly on a tick's {@code now} is that tick's to claim, or nobody's ever.
     */
    @Test
    void findWrapUpSessionIds_matchesAReleaseInstantExactlyAtNow() {

        // given
        var communityId = communityId(true, 3, true);
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        var dueId = saveHeld(seriesId, communityId, now.minus(2, ChronoUnit.HOURS), now);

        // when
        var actual = sessionRepository.findWrapUpSessionIds("default", now, now.minus(THIRTY_DAYS));

        // then
        assertThat(actual).containsExactly(dueId);
    }

    @Test
    void findWrapUpSessionIds_excludesASessionNotYetPastItsReleaseInstant() {

        // given
        var communityId = communityId(true, 3, true);
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        saveHeld(seriesId, communityId, now.minus(2, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS));

        // when / then
        assertThat(sessionRepository.findWrapUpSessionIds("default", now, now.minus(THIRTY_DAYS))).isEmpty();
    }

    @Test
    void findWrapUpSessionIds_excludesAHeldSessionWithNoReleaseInstant() {

        // given - permanent suppression: no release instant, never sends
        var communityId = communityId(true, 3, true);
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        saveHeld(seriesId, communityId, now.minus(2, ChronoUnit.HOURS), null);

        // when / then
        assertThat(sessionRepository.findWrapUpSessionIds("default", now, now.minus(THIRTY_DAYS))).isEmpty();
    }

    @Test
    void findWrapUpSessionIds_excludesWhenTheCommunityHasOptedOut() {

        // given
        var communityId = communityId(true, 3, false);
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        saveHeld(seriesId, communityId, now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS));

        // when / then
        assertThat(sessionRepository.findWrapUpSessionIds("default", now, now.minus(THIRTY_DAYS))).isEmpty();
    }

    /**
     * The outside arm of the {@code since} floor: a session released long before the window opens does not get mailed
     * just because a member joined the community today. Without this floor, this would be a hit.
     */
    @Test
    void findWrapUpSessionIds_excludesASessionReleasedBeforeTheSinceFloor() {

        // given
        var communityId = communityId(true, 3, true);
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        var since = now.minus(THIRTY_DAYS);
        saveHeld(seriesId, communityId, since.minus(60, ChronoUnit.DAYS), since.minus(1, ChronoUnit.HOURS));

        // when / then
        assertThat(sessionRepository.findWrapUpSessionIds("default", now, since)).isEmpty();
    }

    /**
     * The watermark boundary: {@code since} is the exact {@code now} an earlier tick already claimed through, so a
     * session released at precisely that instant was already produced then - the bound is exclusive ({@code >}, not
     * {@code >=}), and this session must not be produced a second time on this tick.
     */
    @Test
    void findWrapUpSessionIds_excludesASessionReleasedExactlyAtTheSinceFloor() {

        // given
        var communityId = communityId(true, 3, true);
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        var since = now.minus(THIRTY_DAYS);
        saveHeld(seriesId, communityId, since.minus(60, ChronoUnit.DAYS), since);

        // when / then
        assertThat(sessionRepository.findWrapUpSessionIds("default", now, since)).isEmpty();
    }

    /** The inside arm: a session released one second past the floor is still due. */
    @Test
    void findWrapUpSessionIds_matchesASessionReleasedJustPastTheSinceFloor() {

        // given
        var communityId = communityId(true, 3, true);
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        var since = now.minus(THIRTY_DAYS);
        var dueId = saveHeld(seriesId, communityId, since.minus(60, ChronoUnit.DAYS), since.plusSeconds(1));

        // when
        var actual = sessionRepository.findWrapUpSessionIds("default", now, since);

        // then
        assertThat(actual).containsExactly(dueId);
    }

    private static final int ONE_DAY_OFFSET = 1;

    @Test
    void findOverdueUnclosedSessionIds_matchesAScheduledSessionPastItsDueInstant() {

        // given - due = scheduledAt + 1 day; scheduledAt chosen so due is one hour in the past.
        var communityId = communityId();
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        var dueId = saveScheduled(seriesId, communityId, now.minus(1, ChronoUnit.DAYS).minus(1, ChronoUnit.HOURS));

        // when
        var actual = sessionRepository.findOverdueUnclosedSessionIds("default", ONE_DAY_OFFSET, now.minus(THIRTY_DAYS),
                now);

        // then
        assertThat(actual).containsExactly(dueId);
    }

    @Test
    void findOverdueUnclosedSessionIds_excludesASessionNotYetPastItsDueInstant() {

        // given - due is one hour still in the future.
        var communityId = communityId();
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        saveScheduled(seriesId, communityId, now.minus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));

        // when / then
        assertThat(
                sessionRepository.findOverdueUnclosedSessionIds("default", ONE_DAY_OFFSET, now.minus(THIRTY_DAYS), now))
                .isEmpty();
    }

    @Test
    void findOverdueUnclosedSessionIds_excludesASessionAlreadyClosed() {

        // given
        var communityId = communityId();
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        var id = saveScheduled(seriesId, communityId, now.minus(1, ChronoUnit.DAYS).minus(1, ChronoUnit.HOURS));
        jdbc.update("UPDATE community_session SET closed_at = ? WHERE id = ?", Timestamp.from(now), id);

        // when / then
        assertThat(
                sessionRepository.findOverdueUnclosedSessionIds("default", ONE_DAY_OFFSET, now.minus(THIRTY_DAYS), now))
                .isEmpty();
    }

    @Test
    void findOverdueUnclosedSessionIds_excludesANonScheduledSession() {

        // given - already HELD, nothing left to nudge
        var communityId = communityId();
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        saveHeld(seriesId, communityId, now.minus(1, ChronoUnit.DAYS).minus(1, ChronoUnit.HOURS), null);

        // when / then
        assertThat(
                sessionRepository.findOverdueUnclosedSessionIds("default", ONE_DAY_OFFSET, now.minus(THIRTY_DAYS), now))
                .isEmpty();
    }

    /**
     * The outside arm for the awaiting-close nudge's own floor: a session scheduled long before the window opens does
     * not flood a newly promoted organizer just because they were promoted today.
     */
    @Test
    void findOverdueUnclosedSessionIds_excludesASessionWithDueInstantBeforeTheSinceFloor() {

        // given
        var communityId = communityId();
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        var since = now.minus(THIRTY_DAYS);
        saveScheduled(seriesId, communityId, since.minus(1, ChronoUnit.DAYS).minus(1, ChronoUnit.HOURS));

        // when / then
        assertThat(sessionRepository.findOverdueUnclosedSessionIds("default", ONE_DAY_OFFSET, since, now)).isEmpty();
    }

    /**
     * The watermark boundary: a due instant exactly at {@code since} was already claimed by whichever earlier tick
     * advanced the watermark to that exact instant - exclusive, not inclusive.
     */
    @Test
    void findOverdueUnclosedSessionIds_excludesASessionWithDueInstantExactlyAtTheSinceFloor() {

        // given
        var communityId = communityId();
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        var since = now.minus(THIRTY_DAYS);
        saveScheduled(seriesId, communityId, since.minus(1, ChronoUnit.DAYS));

        // when / then
        assertThat(sessionRepository.findOverdueUnclosedSessionIds("default", ONE_DAY_OFFSET, since, now)).isEmpty();
    }

    /** The inside arm: a due instant one minute past the floor is still due. */
    @Test
    void findOverdueUnclosedSessionIds_matchesASessionWithDueInstantJustPastTheSinceFloor() {

        // given
        var communityId = communityId();
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        var since = now.minus(THIRTY_DAYS);
        var dueId = saveScheduled(seriesId, communityId, since.minus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.MINUTES));

        // when
        var actual = sessionRepository.findOverdueUnclosedSessionIds("default", ONE_DAY_OFFSET, since, now);

        // then
        assertThat(actual).containsExactly(dueId);
    }

    /**
     * The accepted silence the upcoming notice grew out of, and this nudge keeps: a session scheduled five days in the
     * past already has a due instant ({@code scheduledAt + 1 day}) well before a watermark only five minutes old,
     * purely because it was only just materialised or rescheduled. Not nudged, deliberately - the nudge is an
     * organizer-only reminder with no stored intent to override it. See
     * {@link SessionRepository#findOverdueUnclosedSessionIds}'s javadoc.
     */
    @Test
    void findOverdueUnclosedSessionIds_excludesASessionCreatedInsideItsOwnDueWindow() {

        // given
        var communityId = communityId();
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        var since = now.minus(5, ChronoUnit.MINUTES);
        saveScheduled(seriesId, communityId, now.minus(5, ChronoUnit.DAYS));

        // when / then
        assertThat(sessionRepository.findOverdueUnclosedSessionIds("default", ONE_DAY_OFFSET, since, now)).isEmpty();
    }

    @Test
    void findPendingForSeriesExcluding_looksPastTheExcludedSessionToTheGenuineNextOne() {

        // given - the wrap-up producer's own session (its scheduledAt still >= now, the edge case a session marked
        // HELD before its own instant can produce) must not be mistaken for what comes next.
        var communityId = communityId();
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        var wrappedUpSessionId = saveScheduled(seriesId, communityId, now.plus(1, ChronoUnit.HOURS));
        var genuineNextId = saveScheduled(seriesId, communityId, now.plus(2, ChronoUnit.DAYS));

        // when
        var actual = sessionRepository.findPendingForSeriesExcluding("default", seriesId, wrappedUpSessionId, now);

        // then
        assertThat(actual).isPresent();
        assertThat(actual.orElseThrow().getId()).isEqualTo(genuineNextId);
    }

    @Test
    void findPendingForSeriesExcluding_isEmptyWhenTheOnlyPendingSessionIsTheExcludedOne() {

        // given
        var communityId = communityId();
        var seriesId = seriesId(communityId);
        var now = Instant.parse("2026-06-08T00:00:00Z");
        var onlySessionId = saveScheduled(seriesId, communityId, now.plus(1, ChronoUnit.HOURS));

        // when / then
        assertThat(sessionRepository.findPendingForSeriesExcluding("default", seriesId, onlySessionId, now)).isEmpty();
    }

    private Long saveSession(Long communityId, Long seriesId, String status, String occurrenceDate) {

        var date = LocalDate.parse(occurrenceDate);
        return sessionRepository
                .save(SessionEntity.builder().organizationId("default").seriesId(seriesId).communityId(communityId)
                        .status(status).scheduledAt(date.atTime(18, 0).toInstant(java.time.ZoneOffset.UTC))
                        .occurrenceDate(date).notes("").recordingUrl("").sections(List.of()).build())
                .getId();
    }
}
