package dev.axonia.events.adapter.out.community;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.community.api.LobbySession;
import dev.axonia.community.api.spi.SessionShowcase.SessionSignals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class SessionShowcaseAdapterIntegrationTest extends CoreJdbcTestSupport {

    private static final String ORG = "showcase-sessions";
    private static final String OTHER_ORG = "showcase-sessions-other";
    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private SessionShowcaseAdapter sut;

    @BeforeEach
    void setUp() {

        sut = new SessionShowcaseAdapter(jdbc, () -> ORG);
        kind(ORG);
        kind(OTHER_ORG);
    }

    @Test
    void nextSessionsAtIgnoresCancelledSkippedAndPast() {

        // given
        var guild = community("guild", ORG);
        var quiet = community("quiet", ORG);
        session(guild, "CANCELLED", NOW.plus(Duration.ofHours(1)), null);
        session(guild, "SKIPPED", NOW.plus(Duration.ofHours(2)), null);
        session(guild, "HELD", NOW.minus(Duration.ofHours(1)), null);
        session(guild, "SCHEDULED", NOW.plus(Duration.ofDays(9)), null);
        session(guild, "SCHEDULED", NOW.plus(Duration.ofDays(3)), null);
        session(quiet, "SCHEDULED", NOW, null);

        // when
        var actual = sut.nextSessionsAt(Set.of(guild, quiet), NOW);

        // then
        assertThat(actual).containsOnly(entry(guild, NOW.plus(Duration.ofDays(3))), entry(quiet, NOW));
    }

    @Test
    void signalsCarryNextLatestHeldCountAndLatestProposedTopic() {

        // given
        var guild = community("guild", ORG);
        session(guild, "SCHEDULED", NOW.plus(Duration.ofDays(8)), "Later");
        session(guild, "SCHEDULED", NOW.plus(Duration.ofDays(2)), "Kickoff");
        session(guild, "CANCELLED", NOW.plus(Duration.ofDays(1)), "Called off");
        session(guild, "HELD", NOW.minus(Duration.ofDays(30)), "Older");
        session(guild, "HELD", NOW.minus(Duration.ofDays(7)), "Retro");
        session(guild, "SKIPPED", NOW.minus(Duration.ofDays(2)), "Skipped");
        session(guild, "SCHEDULED", NOW.minus(Duration.ofDays(1)), "Never closed");
        topic(guild, "DONE", "Done topic", NOW.minus(Duration.ofHours(1)));
        topic(guild, "PROPOSED", "Old idea", NOW.minus(Duration.ofDays(3)));
        topic(guild, "PROPOSED", "Virtual threads", NOW.minus(Duration.ofDays(1)));
        topic(guild, "SCHEDULED", "Booked", NOW.minus(Duration.ofMinutes(5)));

        // when
        var actual = sut.signals(Set.of(guild), NOW);

        // then
        assertThat(actual).containsOnlyKeys(guild);
        assertThat(actual.get(guild))
                .isEqualTo(new SessionSignals(new LobbySession("Kickoff", NOW.plus(Duration.ofDays(2)), true, "UTC"),
                        new LobbySession("Retro", NOW.minus(Duration.ofDays(7)), true, "UTC"), 2, "Virtual threads"));
    }

    @Test
    void signalsOfACommunityWithOnlyATopicLeaveTheSessionsEmpty() {

        // given
        var guild = community("guild", ORG);
        topic(guild, "PROPOSED", "Idea", NOW.minus(Duration.ofDays(1)));

        // when
        var actual = sut.signals(Set.of(guild), NOW);

        // then
        assertThat(actual).containsEntry(guild, new SessionSignals(null, null, 0, "Idea"));
    }

    @Test
    void anUntitledSessionHasNoTitle() {

        // given
        var guild = community("guild", ORG);
        session(guild, "SCHEDULED", NOW.plus(Duration.ofDays(1)), null);

        // when
        var actual = sut.signals(Set.of(guild), NOW);

        // then
        assertThat(actual.get(guild).next())
                .isEqualTo(new LobbySession(null, NOW.plus(Duration.ofDays(1)), true, "UTC"));
    }

    @Test
    void anUntimedSessionOnTodaysDateInItsZoneIsStillNext() {

        // given: 17 November in Zurich began at 2026-11-16T23:00Z
        var guild = community("guild", ORG);
        var today = Instant.parse("2026-11-16T23:00:00Z");
        untimed(guild, today, "Europe/Zurich", null);

        // when
        var next = sut.nextSessionsAt(Set.of(guild), NOW);
        var signals = sut.signals(Set.of(guild), NOW);

        // then
        assertThat(next).containsOnly(entry(guild, today));
        assertThat(signals.get(guild).next()).isEqualTo(new LobbySession("Untimed", today, false, "Europe/Zurich"));
    }

    @Test
    void anUntimedSessionOfAnEarlierDateIsOver() {

        // given: 16 November in Zurich
        var guild = community("guild", ORG);
        untimed(guild, Instant.parse("2026-11-15T23:00:00Z"), "Europe/Zurich", null);

        // when / then
        assertThat(sut.nextSessionsAt(Set.of(guild), NOW)).isEmpty();
        assertThat(sut.signals(Set.of(guild), NOW)).isEmpty();
    }

    @Test
    void anUntimedOccurrenceKeepsItsDayInItsSeriesZone() {

        // given: 17 November in Los Angeles runs until 2026-11-18T08:00Z, past the UTC day's end
        var guild = community("guild", ORG);
        var occurrence = Instant.parse("2026-11-17T08:00:00Z");
        untimed(guild, occurrence, null, series(guild, "America/Los_Angeles"));
        var lateThatDay = Instant.parse("2026-11-18T03:00:00Z");

        // when
        var actual = sut.nextSessionsAt(Set.of(guild), lateThatDay);
        var signals = sut.signals(Set.of(guild), lateThatDay);

        // then
        assertThat(actual).containsOnly(entry(guild, occurrence));
        assertThat(signals.get(guild).next())
                .isEqualTo(new LobbySession("Untimed", occurrence, false, "America/Los_Angeles"));
    }

    @Test
    void aSessionInProgressIsNextNotLatest() {

        // given
        var guild = community("guild", ORG);
        session(guild, "HELD", NOW.minus(Duration.ofDays(7)), "Retro");
        var started = NOW.minus(Duration.ofMinutes(20));
        timed(guild, "HELD", started, 60, "Live");

        // when
        var next = sut.nextSessionsAt(Set.of(guild), NOW);
        var signals = sut.signals(Set.of(guild), NOW);

        // then
        assertThat(next).containsOnly(entry(guild, started));
        assertThat(signals.get(guild).next()).isEqualTo(new LobbySession("Live", started, true, "UTC"));
        assertThat(signals.get(guild).latest())
                .isEqualTo(new LobbySession("Retro", NOW.minus(Duration.ofDays(7)), true, "UTC"));
    }

    @Test
    void aSessionPastItsDurationOrWithoutOneIsOverOnceStarted() {

        // given
        var guild = community("guild", ORG);
        timed(guild, "SCHEDULED", NOW.minus(Duration.ofMinutes(90)), 60, "Finished");
        timed(guild, "SCHEDULED", NOW.minus(Duration.ofMinutes(1)), null, "No duration");

        // when / then
        assertThat(sut.nextSessionsAt(Set.of(guild), NOW)).isEmpty();
        assertThat(sut.signals(Set.of(guild), NOW)).isEmpty();
    }

    @Test
    void onlyTheLastWeekIsSearchedForASessionStillUnderWay() {

        // given: a session started six days ago and running seven is kept; one started past the horizon is not
        var kept = community("kept", ORG);
        var dropped = community("dropped", ORG);
        var started = NOW.minus(Duration.ofDays(6));
        timed(kept, "SCHEDULED", started, (int) Duration.ofDays(7).toMinutes(), "Hackathon");
        timed(dropped, "SCHEDULED", NOW.minus(Duration.ofDays(30)), (int) Duration.ofDays(60).toMinutes(), "Ancient");

        // when
        var next = sut.nextSessionsAt(Set.of(kept, dropped), NOW);
        var signals = sut.signals(Set.of(kept, dropped), NOW);

        // then
        assertThat(next).containsOnly(entry(kept, started));
        assertThat(signals).containsOnlyKeys(kept);
    }

    @Test
    void unknownIdsAreAbsent() {

        // given
        var guild = community("guild", ORG);
        var quiet = community("quiet", ORG);
        session(guild, "SCHEDULED", NOW.plus(Duration.ofDays(1)), "Kickoff");

        // when
        var next = sut.nextSessionsAt(Set.of(guild, quiet, 987_654_321L), NOW);
        var signals = sut.signals(Set.of(guild, quiet, 987_654_321L), NOW);

        // then
        assertThat(next).containsOnlyKeys(guild);
        assertThat(signals).containsOnlyKeys(guild);
    }

    @Test
    void noIdsAskNothing() {

        // when / then
        assertThat(sut.nextSessionsAt(Set.of(), NOW)).isEmpty();
        assertThat(sut.signals(Set.of(), NOW)).isEmpty();
    }

    @Test
    void otherOrganizationIsInvisible() {

        // given
        var theirs = community("theirs", OTHER_ORG);
        session(theirs, OTHER_ORG, "SCHEDULED", NOW.plus(Duration.ofDays(1)), "Kickoff");
        session(theirs, OTHER_ORG, "HELD", NOW.minus(Duration.ofDays(1)), "Retro");
        topic(theirs, OTHER_ORG, "PROPOSED", "Idea", NOW.minus(Duration.ofDays(1)));

        // when
        var next = sut.nextSessionsAt(Set.of(theirs), NOW);
        var signals = sut.signals(Set.of(theirs), NOW);

        // then
        assertThat(next).isEmpty();
        assertThat(signals).isEmpty();
    }

    private void kind(String organizationId) {

        jdbc.update("""
                INSERT INTO community_kind (organization_id, label, created_at, updated_at, version)
                VALUES (:org, 'Guild', now(), now(), 0) ON CONFLICT DO NOTHING
                """, new MapSqlParameterSource("org", organizationId));
    }

    private long community(String slug, String organizationId) {

        return jdbc.queryForObject("""
                INSERT INTO communities (organization_id, slug, name, kind_id, description, status,
                        can_start_series, can_edit_structure, can_queue_topics, created_at, updated_at, version)
                VALUES (:org, :slug, 'Guild',
                        (SELECT min(id) FROM community_kind WHERE organization_id = :org), 'd', 'ACTIVE',
                        false, false, false, now(), now(), 0)
                RETURNING id
                """, new MapSqlParameterSource("slug", slug).addValue("org", organizationId), Long.class);
    }

    private void session(long communityId, String status, Instant scheduledAt, String title) {

        session(communityId, ORG, status, scheduledAt, title);
    }

    private void session(long communityId, String organizationId, String status, Instant scheduledAt, String title) {

        jdbc.update("""
                INSERT INTO community_session (organization_id, community_id, status, scheduled_at,
                        title, notes, recording_url, has_time, created_at, updated_at, version)
                VALUES (:org, :communityId, :status, :scheduledAt, :title, '', '', true, now(), now(), 0)
                """,
                new MapSqlParameterSource().addValue("communityId", communityId).addValue("status", status)
                        .addValue("scheduledAt", Timestamp.from(scheduledAt)).addValue("org", organizationId)
                        .addValue("title", title));
    }

    private void topic(long communityId, String status, String title, Instant createdAt) {

        topic(communityId, ORG, status, title, createdAt);
    }

    private void topic(long communityId, String organizationId, String status, String title, Instant createdAt) {

        jdbc.update("""
                INSERT INTO topic (organization_id, community_id, title, description, proposer_user_id, status,
                        created_at, updated_at, version)
                VALUES (:org, :communityId, :title, '', 'proposer', :status, :createdAt, now(), 0)
                """,
                new MapSqlParameterSource().addValue("communityId", communityId).addValue("status", status)
                        .addValue("title", title).addValue("org", organizationId)
                        .addValue("createdAt", Timestamp.from(createdAt)));
    }
    private void timed(long communityId, String status, Instant scheduledAt, Integer durationMinutes, String title) {

        jdbc.update("""
                INSERT INTO community_session (organization_id, community_id, status, scheduled_at, duration_minutes,
                        title, notes, recording_url, has_time, created_at, updated_at, version)
                VALUES (:org, :communityId, :status, :scheduledAt, :duration, :title, '', '', true, now(), now(), 0)
                """,
                new MapSqlParameterSource().addValue("communityId", communityId).addValue("status", status)
                        .addValue("scheduledAt", Timestamp.from(scheduledAt)).addValue("org", ORG)
                        .addValue("duration", durationMinutes).addValue("title", title));
    }

    private void untimed(long communityId, Instant scheduledAt, String timeZone, Long seriesId) {

        jdbc.update("""
                INSERT INTO community_session (organization_id, community_id, series_id, occurrence_date, status,
                        scheduled_at, title, notes, recording_url, has_time, time_zone, created_at, updated_at, version)
                VALUES (:org, :communityId, :seriesId, :occurrenceDate, 'SCHEDULED', :scheduledAt, 'Untimed', '', '',
                        false, :timeZone, now(), now(), 0)
                """,
                new MapSqlParameterSource().addValue("communityId", communityId).addValue("org", ORG)
                        .addValue("seriesId", seriesId)
                        .addValue("occurrenceDate", seriesId == null ? null : Date.valueOf("2026-11-17"))
                        .addValue("scheduledAt", Timestamp.from(scheduledAt)).addValue("timeZone", timeZone));
    }

    private long series(long communityId, String timeZone) {

        return jdbc.queryForObject("""
                INSERT INTO event_series (organization_id, community_id, title, cadence_count, cadence_unit,
                        starts_on, time_zone, status, created_at, updated_at, version)
                VALUES (:org, :communityId, 'Series', 1, 'WEEK', '2026-01-01', :timeZone, 'ACTIVE', now(), now(), 0)
                RETURNING id
                """, new MapSqlParameterSource().addValue("org", ORG).addValue("communityId", communityId)
                .addValue("timeZone", timeZone), Long.class);
    }
}
