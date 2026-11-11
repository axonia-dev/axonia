package dev.axonia.events.adapter.out.health;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.health.api.BucketCredit;
import dev.axonia.health.api.BucketTalks;
import dev.axonia.health.api.FeedbackAverages;
import dev.axonia.health.api.SeriesPulse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class EventsSignalsAdapterTest extends CoreJdbcTestSupport {

    private static final Instant FROM = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant INSIDE = Instant.parse("2026-04-15T18:00:00Z");
    private static final Instant ALSO_INSIDE = Instant.parse("2026-05-01T18:00:00Z");
    private static final Instant BEFORE = Instant.parse("2026-02-01T18:00:00Z");
    private static final Instant NOW = Instant.parse("2026-06-03T12:00:00Z");

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
    void heldSessionDates_inWindowNeitherCancelledNorSkipped() {

        // given
        var communityId = community("backend-guild");
        var seriesId = series(communityId, "ACTIVE", 1, "WEEK");
        session(seriesId, communityId, "SCHEDULED", INSIDE);
        session(seriesId, communityId, "SCHEDULED", BEFORE);
        session(seriesId, communityId, "CANCELLED", ALSO_INSIDE);
        session(seriesId, communityId, "SKIPPED", Instant.parse("2026-05-08T18:00:00Z"));

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
    void talkCounts_excludesSkippedAndCancelledSessions() {

        // given alice presents on a held session, bob on a skipped one, carol on a cancelled one
        var communityId = community("backend-guild");
        var seriesId = series(communityId, "ACTIVE", 1, "WEEK");
        presenter(slot(section(session(seriesId, communityId, "SCHEDULED", INSIDE)), 0), "alice");
        presenter(slot(section(session(seriesId, communityId, "SKIPPED", ALSO_INSIDE)), 0), "bob");
        presenter(slot(section(session(seriesId, communityId, "CANCELLED", Instant.parse("2026-05-08T18:00:00Z"))), 0),
                "carol");
        presenter(slot(section(session(seriesId, communityId, "SCHEDULED", BEFORE)), 0), "dave");
        topic(communityId, "erin", INSIDE);

        // when
        var actual = sut.talkCounts(communityId, FROM, TO);

        // then topics are not talks, and only held in-window sessions count
        assertThat(actual).containsOnly(entry("alice", 1L));
    }

    @Test
    void talkCounts_countsOneOffSessionsWithoutSeries() {

        // given a one-off session with two slots presented by alice
        var communityId = community("backend-guild");
        var sectionId = section(oneOffSession(communityId, "SCHEDULED", INSIDE));
        presenter(slot(sectionId, 0), "alice");
        presenter(slot(sectionId, 1), "alice");

        // when
        var actual = sut.talkCounts(communityId, FROM, TO);

        // then
        assertThat(actual).containsOnly(entry("alice", 2L));
    }

    @Test
    void topicProposals_perProposerInWindow() {

        // given
        var communityId = community("backend-guild");
        var otherCommunityId = community("frontend-guild");
        topic(communityId, "alice", INSIDE);
        topic(communityId, "alice", ALSO_INSIDE);
        topic(communityId, "bob", INSIDE);
        topic(communityId, "carol", BEFORE);
        topic(otherCommunityId, "dave", INSIDE);
        presenter(slot(section(oneOffSession(communityId, "SCHEDULED", INSIDE)), 0), "erin");

        // when
        var actual = sut.topicProposals(communityId, FROM, TO);

        // then
        assertThat(actual).containsOnly(entry("alice", 2L), entry("bob", 1L));
    }

    @Test
    void nextSessionAt_earliestFutureNotCancelled() {

        // given a cancelled and a skipped session before a scheduled one, a past one, and another community's sooner
        // one
        var communityId = community("backend-guild");
        var otherCommunityId = community("frontend-guild");
        var now = INSIDE;
        oneOffSession(communityId, "SCHEDULED", now.minus(Duration.ofDays(1)));
        oneOffSession(communityId, "CANCELLED", now.plus(Duration.ofDays(2)));
        oneOffSession(communityId, "SKIPPED", now.plus(Duration.ofDays(3)));
        oneOffSession(communityId, "SCHEDULED", now.plus(Duration.ofDays(5)));
        oneOffSession(communityId, "SCHEDULED", now.plus(Duration.ofDays(9)));
        oneOffSession(otherCommunityId, "SCHEDULED", now.plus(Duration.ofDays(1)));

        // when
        var actual = sut.nextSessionAt(communityId, now);

        // then
        assertThat(actual).contains(now.plus(Duration.ofDays(5)));
    }

    @Test
    void nextSessionAt_noneScheduledIsEmpty() {

        // given only a past session
        var communityId = community("backend-guild");
        oneOffSession(communityId, "SCHEDULED", BEFORE);

        // when
        var actual = sut.nextSessionAt(communityId, INSIDE);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void lastHeldSessionAt_latestHeldBeforeNowHoweverOld() {

        // given a held session long ago, a later skipped and cancelled one, a future one, and another community's
        var communityId = community("backend-guild");
        var otherCommunityId = community("frontend-guild");
        var now = INSIDE;
        oneOffSession(communityId, "SCHEDULED", BEFORE.minus(Duration.ofDays(400)));
        oneOffSession(communityId, "SCHEDULED", BEFORE);
        oneOffSession(communityId, "SKIPPED", now.minus(Duration.ofDays(3)));
        oneOffSession(communityId, "CANCELLED", now.minus(Duration.ofDays(2)));
        oneOffSession(communityId, "SCHEDULED", now.plus(Duration.ofDays(5)));
        oneOffSession(otherCommunityId, "SCHEDULED", now.minus(Duration.ofDays(1)));

        // when
        var actual = sut.lastHeldSessionAt(communityId, now);

        // then
        assertThat(actual).contains(BEFORE);
    }

    @Test
    void lastHeldSessionAt_neverHeldIsEmpty() {

        // given only a future session
        var communityId = community("backend-guild");
        oneOffSession(communityId, "SCHEDULED", INSIDE.plus(Duration.ofDays(5)));

        // when
        var actual = sut.lastHeldSessionAt(communityId, INSIDE);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void talkCreditsPerBucket_bucketsBySessionSchedule() {

        // given two talks by alice on day 3, one by bob on day 7, and strays a dropped predicate would let in
        var communityId = community("backend-guild");
        var otherCommunityId = community("frontend-guild");
        var dayThree = section(oneOffSession(communityId, "SCHEDULED", day(3)));
        presenter(slot(dayThree, 0), "alice");
        presenter(slot(dayThree, 1), "alice");
        presenter(slot(section(oneOffSession(communityId, "SCHEDULED", day(7))), 0), "bob");
        presenter(slot(section(oneOffSession(communityId, "SKIPPED", day(5))), 0), "carol");
        presenter(slot(section(oneOffSession(communityId, "SCHEDULED", day(13))), 0), "mallory");
        presenter(slot(section(oneOffSession(otherCommunityId, "SCHEDULED", day(4))), 0), "trudy");

        // when
        var actual = sut.talkCreditsPerBucket(communityId, WINDOW_FROM, WINDOW_TO, BUCKETS);

        // then
        assertThat(actual).containsExactlyInAnyOrder(new BucketTalks(3, "alice", 2), new BucketTalks(7, "bob", 1));
    }

    @Test
    void topicCreditsPerBucket_oneCreditPerProposerPerBucket() {

        // given alice proposes twice on day 2 and once on day 6, bob once on day 6
        var communityId = community("backend-guild");
        topic(communityId, "alice", day(2));
        topic(communityId, "alice", day(2).plus(Duration.ofHours(1)));
        topic(communityId, "alice", day(6));
        topic(communityId, "bob", day(6));
        topic(communityId, "mallory", day(13));

        // when
        var actual = sut.topicCreditsPerBucket(communityId, WINDOW_FROM, WINDOW_TO, BUCKETS);

        // then
        assertThat(actual).containsExactlyInAnyOrder(new BucketCredit(2, "alice"), new BucketCredit(6, "alice"),
                new BucketCredit(6, "bob"));
    }

    @Test
    void engagementCreditsPerBucket_oneCreditPerUserPerBucketByTheActsOwnTimestamp() {

        // given bob votes and attends on day 4, dana votes on a day 1 topic on day 9, and strays outside
        var communityId = community("backend-guild");
        var otherCommunityId = community("frontend-guild");
        var topicId = topic(communityId, "alice", day(1));
        var sessionId = oneOffSession(communityId, "SCHEDULED", day(0));
        vote(topicId, "bob", day(4));
        attendance(sessionId, "bob", day(4));
        vote(topicId, "dana", day(9));
        attendance(sessionId, "mallory", day(13));
        attendance(oneOffSession(otherCommunityId, "SCHEDULED", day(2)), "trudy", day(2));

        // when
        var actual = sut.engagementCreditsPerBucket(communityId, WINDOW_FROM, WINDOW_TO, BUCKETS);

        // then
        assertThat(actual).containsExactlyInAnyOrder(new BucketCredit(4, "bob"), new BucketCredit(9, "dana"));
    }

    @Test
    void feedbackAverages_countsRevealedLiveSessionsOnly() {

        // given one session still in its window, one closed exactly a window ago with three answers, one with two
        var communityId = community("backend-guild");
        var open = heldSession(communityId, Instant.parse("2026-05-30T18:00:00Z"), NOW.minus(Duration.ofDays(2)));
        feedback(open, "alice", 1, 1);
        feedback(open, "bob", 1, 1);
        feedback(open, "carol", 1, 1);
        var revealed = heldSession(communityId, Instant.parse("2026-05-27T10:00:00Z"), NOW.minus(Duration.ofDays(7)));
        feedback(revealed, "alice", 8, 7);
        feedback(revealed, "bob", 9, 6);
        feedback(revealed, "carol", 10, 8);
        var tooFew = heldSession(communityId, INSIDE, INSIDE.plus(Duration.ofHours(2)));
        feedback(tooFew, "alice", 2, 2);
        feedback(tooFew, "bob", 3, 3);

        // when
        var actual = sut.feedbackAverages(communityId, FROM, TO, NOW);

        // then the open session contributes nothing and the two-answer one only its count
        assertThat(actual).isEqualTo(new FeedbackAverages(3, 3, 27, 3, 21, 1, 1));
    }

    @Test
    void feedbackAverages_importedCountsAtOnceOnTheTenPointRange() {

        // given an import on a five-point scale closed an hour ago, each question skipped once, and a two-answer import
        var communityId = community("backend-guild");
        var imported = feedbackImport(heldSession(communityId, INSIDE, NOW.minus(Duration.ofHours(1))), 5);
        importAnswer(imported, 5, 4);
        importAnswer(imported, 3, null);
        importAnswer(imported, null, 1);
        importAnswer(imported, 1, 5);
        var tooFew = feedbackImport(heldSession(communityId, ALSO_INSIDE, ALSO_INSIDE), 10);
        importAnswer(tooFew, 9, 9);
        importAnswer(tooFew, 8, 8);

        // when
        var actual = sut.feedbackAverages(communityId, FROM, TO, NOW);

        // then four answer rows; enjoyed 10 + 5.5 + 1 and learned 7.75 + 1 + 10, each over the answers that hold it
        assertThat(actual).isEqualTo(new FeedbackAverages(4, 3, 16.5, 3, 18.75, 1, 1));
    }

    @Test
    void feedbackAverages_ignoresOtherCommunitiesAndOutsideTheWindow() {

        // given one counted session, then one before the window, one at its exclusive end, one in another community
        // and one closed but not held, each with three answers
        var communityId = community("backend-guild");
        var otherId = community("frontend-guild");
        threeAnswers(heldSession(communityId, INSIDE, INSIDE.plus(Duration.ofHours(2))), 6);
        threeAnswers(heldSession(communityId, BEFORE, BEFORE.plus(Duration.ofHours(2))), 1);
        threeAnswers(heldSession(communityId, TO, TO.plus(Duration.ofHours(2))), 1);
        threeAnswers(heldSession(otherId, INSIDE, INSIDE.plus(Duration.ofHours(2))), 1);
        threeAnswers(closedSession(communityId, "SCHEDULED", ALSO_INSIDE, ALSO_INSIDE.plus(Duration.ofHours(2))), 1);

        // when
        var actual = sut.feedbackAverages(communityId, FROM, TO, NOW);

        // then
        assertThat(actual).isEqualTo(new FeedbackAverages(3, 3, 18, 3, 18, 1, 0));
    }

    @Test
    void feedbackAverages_noneWhenNothingRevealed() {

        // given a held session nobody answered and one still in its window
        var communityId = community("backend-guild");
        heldSession(communityId, INSIDE, INSIDE.plus(Duration.ofHours(2)));
        threeAnswers(heldSession(communityId, Instant.parse("2026-05-31T18:00:00Z"), NOW.minus(Duration.ofDays(1))), 9);

        // when
        var actual = sut.feedbackAverages(communityId, FROM, TO, NOW);

        // then a session without answers is not one with too few
        assertThat(actual).isEqualTo(FeedbackAverages.NONE);
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
                        cadence_monthly_by, starts_on, time_zone, status, created_at, updated_at, version)
                VALUES ('default', :communityId, 'Talks', :cadenceCount, :cadenceUnit,
                        CASE WHEN :cadenceUnit = 'MONTH' THEN 'DAY_OF_MONTH' END, '2026-01-01', 'UTC', :status,
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

    private long oneOffSession(long communityId, String status, Instant scheduledAt) {

        return jdbc.queryForObject("""
                INSERT INTO community_session (organization_id, series_id, community_id, status, scheduled_at,
                        occurrence_date, notes, recording_url, has_time, created_at, updated_at, version)
                VALUES ('default', :seriesId, :communityId, :status, :scheduledAt,
                        NULL, '', '', true, now(), now(), 0)
                RETURNING id
                """,
                new MapSqlParameterSource().addValue("seriesId", null, Types.BIGINT)
                        .addValue("communityId", communityId).addValue("status", status)
                        .addValue("scheduledAt", Timestamp.from(scheduledAt)),
                Long.class);
    }

    private long heldSession(long communityId, Instant scheduledAt, Instant closedAt) {

        return closedSession(communityId, "HELD", scheduledAt, closedAt);
    }

    private long closedSession(long communityId, String status, Instant scheduledAt, Instant closedAt) {

        return jdbc.queryForObject("""
                INSERT INTO community_session (organization_id, series_id, community_id, status, scheduled_at,
                        occurrence_date, notes, recording_url, has_time, closed_at, created_at, updated_at, version)
                VALUES ('default', NULL, :communityId, :status, :scheduledAt, NULL, '', '', true, :closedAt,
                        now(), now(), 0)
                RETURNING id
                """, new MapSqlParameterSource().addValue("communityId", communityId).addValue("status", status)
                .addValue("scheduledAt", Timestamp.from(scheduledAt)).addValue("closedAt", Timestamp.from(closedAt)),
                Long.class);
    }

    private void threeAnswers(long sessionId, int value) {

        feedback(sessionId, "alice", value, value);
        feedback(sessionId, "bob", value, value);
        feedback(sessionId, "carol", value, value);
    }

    private void feedback(long sessionId, String userId, int enjoyed, int learned) {

        jdbc.update("""
                INSERT INTO session_feedback (organization_id, session_id, user_id, enjoyed, learned, created_at,
                        updated_at, version)
                VALUES ('default', :sessionId, :userId, :enjoyed, :learned, now(), now(), 0)
                """, new MapSqlParameterSource().addValue("sessionId", sessionId).addValue("userId", userId)
                .addValue("enjoyed", enjoyed).addValue("learned", learned));
    }

    private long feedbackImport(long sessionId, int scale) {

        return jdbc.queryForObject("""
                INSERT INTO session_feedback_import (organization_id, session_id, scale, source, created_at)
                VALUES ('default', :sessionId, :scale, 'Microsoft Forms', now())
                RETURNING id
                """, new MapSqlParameterSource().addValue("sessionId", sessionId).addValue("scale", scale), Long.class);
    }

    private void importAnswer(long importId, Integer enjoyed, Integer learned) {

        jdbc.update("""
                INSERT INTO session_feedback_import_answer (organization_id, import_id, enjoyed, learned)
                VALUES ('default', :importId, :enjoyed, :learned)
                """, new MapSqlParameterSource().addValue("importId", importId)
                .addValue("enjoyed", enjoyed, Types.SMALLINT).addValue("learned", learned, Types.SMALLINT));
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
                INSERT INTO topic (organization_id, community_id, title, description, proposer_user_id, status,
                        created_at, updated_at, version)
                VALUES ('default', :communityId, 'T', 'd', :proposerUserId, 'PROPOSED',
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
