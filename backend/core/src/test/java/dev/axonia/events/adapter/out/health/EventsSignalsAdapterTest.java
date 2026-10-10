package dev.axonia.events.adapter.out.health;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.health.api.SeriesPulse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class EventsSignalsAdapterTest extends CoreJdbcTestSupport {

    private static final Instant FROM = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant INSIDE = Instant.parse("2026-04-15T18:00:00Z");
    private static final Instant ALSO_INSIDE = Instant.parse("2026-05-01T18:00:00Z");
    private static final Instant BEFORE = Instant.parse("2026-02-01T18:00:00Z");

    /**
     * A twelve-day window buckets by the day, so bucket n is day n and a fixture reads off the calendar. It
     * deliberately does not start at midnight: with a 06:00 boundary, a date-truncated column such as
     * community_session.occurrence_date falls one bucket before its own timestamp's, so a bucket expression reaching
     * for the truncated column instead of the real one moves the answer. Production windows of 30 or 180 days bucket at
     * 2.5 and 15 days and are never midnight-aligned, so this is the honest shape.
     */
    private static final Instant WINDOW_FROM = Instant.parse("2026-03-01T06:00:00Z");
    private static final Instant WINDOW_TO = WINDOW_FROM.plus(Duration.ofDays(12));
    private static final int BUCKETS = 12;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private EventsSignalsAdapter sut;

    @BeforeEach
    void setUp() {

        sut = new EventsSignalsAdapter(jdbc, () -> "default");
        seedReferenceCatalogs();
    }

    /** The baseline seeds no reference catalogs, so the rows this test's inserts select must be its own. */
    private void seedReferenceCatalogs() {

        jdbc.update(
                "INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                        + "VALUES ('default', 'Guild', now(), now(), 0) ON CONFLICT DO NOTHING",
                new MapSqlParameterSource());
        jdbc.update("INSERT INTO event_section_type "
                + "(organization_id, label, expects_presenter, created_at, updated_at, version, icon, accent_color) "
                + "VALUES ('default', 'Talks', true, now(), now(), 0, 'wrench', '#123456') ON CONFLICT DO NOTHING",
                new MapSqlParameterSource());
    }

    @Test
    void contributionCounts_presenterSpotsOnHeldSessionsAndProposedTopics() {

        // given a held session where alice presents two slots and bob one,
        // plus an in-window topic by alice and an out-of-window topic by carol
        var communityId = community("backend-guild");
        var seriesId = series(communityId, "ACTIVE", 1, "WEEK");
        var sectionId = section(session(seriesId, communityId, "SCHEDULED", INSIDE));
        presenter(slot(sectionId, 0), "alice");
        presenter(slot(sectionId, 1), "alice");
        presenter(slot(sectionId, 2), "bob");
        topic(communityId, "alice", INSIDE);
        topic(communityId, "carol", BEFORE);

        // when
        var actual = sut.contributionCounts(communityId, FROM, TO);

        // then
        assertThat(actual).containsOnly(entry("alice", 3L), entry("bob", 1L));
    }

    @Test
    void contributionCounts_ignoresCancelledAndOutOfWindowSessions() {

        // given presenters on a cancelled in-window session and on a held out-of-window one
        var communityId = community("backend-guild");
        var seriesId = series(communityId, "ACTIVE", 1, "WEEK");
        presenter(slot(section(session(seriesId, communityId, "CANCELLED", INSIDE)), 0), "alice");
        presenter(slot(section(session(seriesId, communityId, "SCHEDULED", BEFORE)), 0), "bob");

        // when
        var actual = sut.contributionCounts(communityId, FROM, TO);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void heldSessionDates_inWindowNonCancelledOnly() {

        // given
        var communityId = community("backend-guild");
        var seriesId = series(communityId, "ACTIVE", 1, "WEEK");
        session(seriesId, communityId, "SCHEDULED", INSIDE);
        session(seriesId, communityId, "SCHEDULED", BEFORE);
        session(seriesId, communityId, "CANCELLED", ALSO_INSIDE);

        // when
        var actual = sut.heldSessionDates(communityId, FROM, TO);

        // then
        assertThat(actual).containsExactly(INSIDE);
    }

    @Test
    void activeSeries_mapsCadenceToExpectedGapDays() {

        // given active series across units, plus an ended one that must be excluded
        var communityId = community("backend-guild");
        series(communityId, "ACTIVE", 1, "WEEK");
        series(communityId, "ACTIVE", 2, "WEEK");
        series(communityId, "ACTIVE", 1, "MONTH");
        series(communityId, "ENDED", 1, "MONTH");

        // when
        var actual = sut.activeSeries(communityId);

        // then ended series excluded; each active cadence maps to its expected gap in days
        assertThat(actual).containsExactlyInAnyOrder(new SeriesPulse(7), new SeriesPulse(14), new SeriesPulse(30));
    }

    @Test
    void engagedUserIds_unionsVotersAndAttendeesInWindow() {

        // given dana votes in window, erin attends in window, frank voted before it
        var communityId = community("backend-guild");
        var seriesId = series(communityId, "ACTIVE", 1, "WEEK");
        var topicId = topic(communityId, "alice", INSIDE);
        vote(topicId, "dana", INSIDE);
        vote(topicId, "frank", BEFORE);
        attendance(session(seriesId, communityId, "SCHEDULED", INSIDE), "erin", ALSO_INSIDE);

        // when
        var actual = sut.engagedUserIds(communityId, FROM, TO);

        // then
        assertThat(actual).containsExactlyInAnyOrder("dana", "erin");
    }

    @Test
    void distinctContributorsPerBucket_countsAPersonOncePerBucketHoweverManyActs() {

        // given alice presents two slots and proposes a topic on day 0, and bob proposes on day 11
        var communityId = community("backend-guild");
        var seriesId = series(communityId, "ACTIVE", 1, "WEEK");
        var sectionId = section(session(seriesId, communityId, "SCHEDULED", day(0)));
        presenter(slot(sectionId, 0), "alice");
        presenter(slot(sectionId, 1), "alice");
        topic(communityId, "alice", day(0));
        topic(communityId, "bob", day(11));

        // when
        var actual = sut.distinctContributorsPerBucket(communityId, WINDOW_FROM, WINDOW_TO, BUCKETS);

        // then bucket 0 is one distinct person, not three acts
        assertThat(actual).containsExactly(1L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 1L);
    }

    @Test
    void distinctContributorsPerBucket_countsTheSamePersonInEachBucketTheyAppearIn() {

        // The series is "how many different people contributed that week", so a steady contributor
        // must show up in every bucket, not only the first.
        var communityId = community("backend-guild");
        topic(communityId, "alice", day(0));
        topic(communityId, "alice", day(6));

        assertThat(sut.distinctContributorsPerBucket(communityId, WINDOW_FROM, WINDOW_TO, BUCKETS)).containsExactly(1L,
                0L, 0L, 0L, 0L, 0L, 1L, 0L, 0L, 0L, 0L, 0L);
    }

    @Test
    void distinctContributorsPerBucket_bucketsEachArmByItsOwnActTimestamp() {

        // given a presenter arm and a topic arm whose acts fall in non-adjacent buckets: one arm bucketed on the
        // other's timestamp column would still return twelve plausible numbers, so pin them apart.
        var communityId = community("backend-guild");
        var seriesId = series(communityId, "ACTIVE", 1, "WEEK");
        presenter(slot(section(session(seriesId, communityId, "SCHEDULED", day(2))), 0), "alice");
        topic(communityId, "bob", day(8));

        // when
        var actual = sut.distinctContributorsPerBucket(communityId, WINDOW_FROM, WINDOW_TO, BUCKETS);

        // then
        assertThat(actual).containsExactly(0L, 0L, 1L, 0L, 0L, 0L, 0L, 0L, 1L, 0L, 0L, 0L);
    }

    @Test
    void distinctContributorsPerBucket_bucketsAPresenterByTheirOwnSessionsSchedule() {

        // given two sessions six buckets apart, each with its own presenter, and a topic in a bucket of its own.
        // Every other timestamp a wrong bucket expression could reach through these joins sits elsewhere: the rows'
        // created_at and updated_at are now(), which clamps to the last bucket, the other session is at day 9, and
        // the session's own occurrence_date truncates to midnight, one bucket short of the 06:00 window boundary.
        var communityId = community("backend-guild");
        var seriesId = series(communityId, "ACTIVE", 1, "WEEK");
        presenter(slot(section(session(seriesId, communityId, "SCHEDULED", day(3))), 0), "alice");
        presenter(slot(section(session(seriesId, communityId, "SCHEDULED", day(9))), 0), "carol");
        topic(communityId, "bob", day(6));

        // when
        var actual = sut.distinctContributorsPerBucket(communityId, WINDOW_FROM, WINDOW_TO, BUCKETS);

        // then
        assertThat(actual).containsExactly(0L, 0L, 0L, 1L, 0L, 0L, 1L, 0L, 0L, 1L, 0L, 0L);
    }

    @Test
    void distinctContributorsPerBucket_ofADormantCommunityIsTwelveZeroes() {

        var communityId = community("backend-guild");

        assertThat(sut.distinctContributorsPerBucket(communityId, WINDOW_FROM, WINDOW_TO, BUCKETS)).hasSize(12)
                .allMatch(count -> count == 0L);
    }

    @Test
    void distinctContributorsPerBucket_answersTheWholeSeriesInOneQueryRatherThanOnePerBucket() {

        // The series must be one grouped query. A loop over twelve sub-windows would pass every
        // behavioural test above and cost twelve round trips.
        var template = mock(NamedParameterJdbcTemplate.class);

        new EventsSignalsAdapter(template, () -> "default").distinctContributorsPerBucket(7L, WINDOW_FROM, WINDOW_TO,
                BUCKETS);

        verify(template, times(1)).queryForList(anyString(), anyMap());
        verifyNoMoreInteractions(template);
    }

    @Test
    void engagedMembersPerBucket_countsVotersAndAttendeesOnce() {

        // given bob both votes and attends on day 0, and carol attends on day 11
        var communityId = community("backend-guild");
        var seriesId = series(communityId, "ACTIVE", 1, "WEEK");
        var topicId = topic(communityId, "alice", day(0));
        var sessionId = session(seriesId, communityId, "SCHEDULED", day(0));
        vote(topicId, "bob", day(0));
        attendance(sessionId, "bob", day(0));
        attendance(sessionId, "carol", day(11));

        // when
        var actual = sut.engagedMembersPerBucket(communityId, WINDOW_FROM, WINDOW_TO, BUCKETS);

        // then bob's two acts are one engaged member
        assertThat(actual).containsExactly(1L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 1L);
    }

    @Test
    void engagedMembersPerBucket_bucketsEachActByItsOwnTimestampNotItsParents() {

        // given a vote whose topic was proposed in a different bucket, and an attendance on a session scheduled in a
        // different bucket again. All four candidate timestamp columns land in four distinct buckets, so bucketing on
        // the parent row instead of the act moves the answer.
        var communityId = community("backend-guild");
        var seriesId = series(communityId, "ACTIVE", 1, "WEEK");
        var topicId = topic(communityId, "alice", day(1));
        var sessionId = session(seriesId, communityId, "SCHEDULED", day(7));
        vote(topicId, "dana", day(4));
        attendance(sessionId, "erin", day(10));

        // when
        var actual = sut.engagedMembersPerBucket(communityId, WINDOW_FROM, WINDOW_TO, BUCKETS);

        // then the buckets are the acts' own, day 4 and day 10, not their parents' day 1 and day 7
        assertThat(actual).containsExactly(0L, 0L, 0L, 0L, 1L, 0L, 0L, 0L, 0L, 0L, 1L, 0L);
    }

    @Test
    void distinctContributorsPerBucket_ignoresActsAfterTheWindowAndInOtherCommunities() {

        // given the acts of the arms-apart fixture, plus strays a dropped predicate would let in: acts after the
        // window, which the bucket clamp would fold into the last point, and acts of another community.
        var communityId = community("backend-guild");
        var otherCommunityId = community("frontend-guild");
        var seriesId = series(communityId, "ACTIVE", 1, "WEEK");
        var otherSeriesId = series(otherCommunityId, "ACTIVE", 1, "WEEK");
        presenter(slot(section(session(seriesId, communityId, "SCHEDULED", day(2))), 0), "alice");
        topic(communityId, "bob", day(8));
        presenter(slot(section(session(seriesId, communityId, "SCHEDULED", day(13))), 0), "mallory");
        topic(communityId, "mallory", day(13));
        presenter(slot(section(session(otherSeriesId, otherCommunityId, "SCHEDULED", day(5))), 0), "trudy");
        topic(otherCommunityId, "trudy", day(5));

        // when
        var actual = sut.distinctContributorsPerBucket(communityId, WINDOW_FROM, WINDOW_TO, BUCKETS);

        // then the strays are absent, and in particular the last bucket stays empty
        assertThat(actual).containsExactly(0L, 0L, 1L, 0L, 0L, 0L, 0L, 0L, 1L, 0L, 0L, 0L);
    }

    @Test
    void engagedMembersPerBucket_ignoresActsAfterTheWindowAndInOtherCommunities() {

        // given the acts of the own-timestamp fixture, plus a vote and an attendance after the window and a vote and
        // an attendance in another community
        var communityId = community("backend-guild");
        var otherCommunityId = community("frontend-guild");
        var seriesId = series(communityId, "ACTIVE", 1, "WEEK");
        var otherSeriesId = series(otherCommunityId, "ACTIVE", 1, "WEEK");
        var topicId = topic(communityId, "alice", day(1));
        var sessionId = session(seriesId, communityId, "SCHEDULED", day(7));
        vote(topicId, "dana", day(4));
        attendance(sessionId, "erin", day(10));
        vote(topicId, "mallory", day(13));
        attendance(sessionId, "mallory", day(13));
        var otherTopicId = topic(otherCommunityId, "alice", day(1));
        var otherSessionId = session(otherSeriesId, otherCommunityId, "SCHEDULED", day(7));
        vote(otherTopicId, "trudy", day(5));
        attendance(otherSessionId, "trudy", day(5));

        // when
        var actual = sut.engagedMembersPerBucket(communityId, WINDOW_FROM, WINDOW_TO, BUCKETS);

        // then the strays are absent, and in particular the last bucket stays empty
        assertThat(actual).containsExactly(0L, 0L, 0L, 0L, 1L, 0L, 0L, 0L, 0L, 0L, 1L, 0L);
    }

    @Test
    void engagedMembersPerBucket_answersTheWholeSeriesInOneQueryRatherThanOnePerBucket() {

        var template = mock(NamedParameterJdbcTemplate.class);

        new EventsSignalsAdapter(template, () -> "default").engagedMembersPerBucket(7L, WINDOW_FROM, WINDOW_TO,
                BUCKETS);

        verify(template, times(1)).queryForList(anyString(), anyMap());
        verifyNoMoreInteractions(template);
    }

    /** Midday on day n of the twelve-day window, so a fixture never sits on a bucket boundary. */
    private static Instant day(int index) {

        return WINDOW_FROM.plus(Duration.ofDays(index)).plus(Duration.ofHours(12));
    }

    // ----- raw-SQL seeding: created_at is auditing-managed, so repositories can't backdate rows -----

    private long community(String slug) {

        return jdbc.queryForObject("""
                INSERT INTO communities (organization_id, slug, name, kind_id, description, status,
                        can_start_series, can_edit_structure, can_queue_topics, created_at, updated_at, version)
                VALUES ('default', :slug, 'Guild', (SELECT min(id) FROM community_kind), 'd', 'ACTIVE',
                        false, false, false, now(), now(), 0)
                RETURNING id
                """, new MapSqlParameterSource("slug", slug), Long.class);
    }

    private long series(long communityId, String status, int cadenceCount, String cadenceUnit) {

        return jdbc.queryForObject("""
                INSERT INTO event_series (organization_id, community_id, title, cadence_count, cadence_unit,
                        starts_on, time_zone, status, created_at, updated_at, version)
                VALUES ('default', :communityId, 'Talks', :cadenceCount, :cadenceUnit, '2026-01-01', 'UTC', :status,
                        now(), now(), 0)
                RETURNING id
                """, new MapSqlParameterSource().addValue("communityId", communityId)
                .addValue("cadenceCount", cadenceCount).addValue("cadenceUnit", cadenceUnit).addValue("status", status),
                Long.class);
    }

    private long session(long seriesId, long communityId, String status, Instant scheduledAt) {

        return jdbc.queryForObject("""
                INSERT INTO community_session (organization_id, series_id, community_id, status, scheduled_at,
                        occurrence_date, notes, recording_url, has_time, created_at, updated_at, version)
                VALUES ('default', :seriesId, :communityId, :status, :scheduledAt,
                        date(:scheduledAt), '', '', true, now(), now(), 0)
                RETURNING id
                """, new MapSqlParameterSource().addValue("seriesId", seriesId).addValue("communityId", communityId)
                .addValue("status", status).addValue("scheduledAt", Timestamp.from(scheduledAt)), Long.class);
    }

    private long section(long sessionId) {

        return jdbc.queryForObject("""
                INSERT INTO event_section (session_id, position, section_type_id, title)
                VALUES (:sessionId, 0, (SELECT min(id) FROM event_section_type), 'Talks')
                RETURNING id
                """, new MapSqlParameterSource("sessionId", sessionId), Long.class);
    }

    private long slot(long sectionId, int position) {

        return jdbc.queryForObject("""
                INSERT INTO event_slot (section_id, position, title, duration_minutes)
                VALUES (:sectionId, :position, 'Slot', 15)
                RETURNING id
                """, new MapSqlParameterSource().addValue("sectionId", sectionId).addValue("position", position),
                Long.class);
    }

    private void presenter(long slotId, String userId) {

        jdbc.update("INSERT INTO event_slot_presenter (slot_id, user_id) VALUES (:slotId, :userId)",
                new MapSqlParameterSource().addValue("slotId", slotId).addValue("userId", userId));
    }

    private long topic(long communityId, String proposerUserId, Instant createdAt) {

        return jdbc.queryForObject("""
                INSERT INTO topic (organization_id, community_id, kind, title, description, proposer_user_id, status,
                        created_at, updated_at, version)
                VALUES ('default', :communityId, 'OFFER', 'T', 'd', :proposerUserId, 'PROPOSED',
                        :createdAt, now(), 0)
                RETURNING id
                """,
                new MapSqlParameterSource().addValue("communityId", communityId)
                        .addValue("proposerUserId", proposerUserId).addValue("createdAt", Timestamp.from(createdAt)),
                Long.class);
    }

    private void vote(long topicId, String userId, Instant createdAt) {

        jdbc.update("""
                INSERT INTO topic_vote (organization_id, topic_id, user_id, created_at, updated_at, version)
                VALUES ('default', :topicId, :userId, :createdAt, now(), 0)
                """, new MapSqlParameterSource().addValue("topicId", topicId).addValue("userId", userId)
                .addValue("createdAt", Timestamp.from(createdAt)));
    }

    private void attendance(long sessionId, String userId, Instant createdAt) {

        jdbc.update("""
                INSERT INTO session_attendance (organization_id, session_id, user_id, created_at, updated_at, version)
                VALUES ('default', :sessionId, :userId, :createdAt, now(), 0)
                """, new MapSqlParameterSource().addValue("sessionId", sessionId).addValue("userId", userId)
                .addValue("createdAt", Timestamp.from(createdAt)));
    }
}
