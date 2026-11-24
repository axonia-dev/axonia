package dev.axonia.events.adapter.out.persistence;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.events.api.NamedSession;
import dev.axonia.events.api.SessionHeading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class ActivityTitleReaderTest extends CoreJdbcTestSupport {

    /** No row reaches this far, so it stands for a source the caller's reference outlived. */
    private static final long GONE = 999_000L;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private ActivityTitleReader sut;

    private int occurrences;

    @BeforeEach
    void createReader() {

        sut = new ActivityTitleReader(jdbc, () -> "default");
    }

    @Test
    void slotTitles_returnsTitlePerSlotId() {

        var sectionId = seedSection(seedCommunity());
        var first = seedSlot(sectionId, 1, "Scaling Kafka to 1M msgs/s");
        var second = seedSlot(sectionId, 2, "Kubernetes operators deep-dive");

        assertThat(sut.slotTitles(List.of(first, second))).containsOnly(entry(first, "Scaling Kafka to 1M msgs/s"),
                entry(second, "Kubernetes operators deep-dive"));
    }

    @Test
    void topicTitles_areScopedToTheCallersOrganization() {

        var mine = seedTopic(seedCommunity(), "Kafka backlog");
        var theirs = seedTopicIn("other-org", seedCommunity(), "Their backlog");

        assertThat(sut.topicTitles(List.of(mine, theirs))).containsOnly(entry(mine, "Kafka backlog"));
    }

    @Test
    void topicTitles_returnsTitlePerTopicId() {

        var communityId = seedCommunity();
        var first = seedTopic(communityId, "Kafka backlog");
        var second = seedTopic(communityId, "Kubernetes on-call");

        assertThat(sut.topicTitles(List.of(first, second))).containsOnly(entry(first, "Kafka backlog"),
                entry(second, "Kubernetes on-call"));
    }

    @Test
    void sessionNames_followTheNameEveryOtherSurfaceShows() {

        // given
        var communityId = seedCommunity();
        var seriesId = seedSeries(communityId, "Platform evenings");
        var ownTitle = seedSession("default", communityId, seriesId, "Kafka night");
        var byItsTalk = seedSession("default", communityId, seriesId, null);
        var later = seedSectionIn(byItsTalk, 1);
        var earlier = seedSectionIn(byItsTalk, 0);
        seedSlot(later, 0, "Second section talk");
        seedSlot(earlier, 0, "   ");
        seedSlot(earlier, 1, "Radical candor in reviews");
        var bySeries = seedSession("default", communityId, seriesId, null);
        var oneOff = seedSession("default", communityId, null, null);

        // when
        var actual = sut.sessionNames(List.of(ownTitle, byItsTalk, bySeries, oneOff));

        // then
        assertThat(actual).containsOnly(entry(ownTitle, new NamedSession(communityId, "Kafka night")),
                entry(byItsTalk, new NamedSession(communityId, "Radical candor in reviews")),
                entry(bySeries, new NamedSession(communityId, "Platform evenings")),
                entry(oneOff, new NamedSession(communityId, "Session")));
    }

    @Test
    void sessionNames_areScopedToTheCallersOrganization() {

        // given
        var communityId = seedCommunity();
        var mine = seedSession("default", communityId, null, "Ours");
        var theirs = seedSession("other-org", communityId, null, "Theirs");

        // when
        var actual = sut.sessionNames(List.of(mine, theirs));

        // then
        assertThat(actual).containsOnly(entry(mine, new NamedSession(communityId, "Ours")));
    }

    @Test
    void sessionHeadings_nameTheSeriesAndTheDayEachSessionIsHeldOn() {

        // given
        var communityId = seedCommunity();
        var seriesId = seedSeries(communityId, "Platform evenings");
        var occurrence = seedSession("default", communityId, seriesId, "Kafka night");
        var lateInZurich = seedOneOff(communityId, "2026-03-01T23:30:00Z", "Europe/Zurich");
        var beforeTheZoneColumn = seedOneOff(communityId, "2026-03-01T23:30:00Z", null);

        // when
        var actual = sut.sessionHeadings(List.of(occurrence, lateInZurich, beforeTheZoneColumn));

        // then
        assertThat(actual).containsOnly(
                entry(occurrence, new SessionHeading("Kafka night", "Platform evenings", LocalDate.of(2026, 1, 1))),
                entry(lateInZurich, new SessionHeading("Session", null, LocalDate.of(2026, 3, 2))),
                entry(beforeTheZoneColumn, new SessionHeading("Session", null, LocalDate.of(2026, 3, 1))));
    }

    @Test
    void sessionHeadings_areScopedToTheCallersOrganization() {

        // given
        var communityId = seedCommunity();
        var mine = seedSession("default", communityId, null, "Ours");
        var theirs = seedSession("other-org", communityId, null, "Theirs");

        // when
        var actual = sut.sessionHeadings(List.of(mine, theirs));

        // then
        assertThat(actual).containsOnlyKeys(mine);
    }

    @Test
    void anIdThatNoLongerResolvesIsAbsentRatherThanAThrow() {

        assertThat(sut.slotTitles(List.of(GONE))).isEmpty();
        assertThat(sut.topicTitles(List.of(GONE))).isEmpty();
        assertThat(sut.sessionNames(List.of(GONE))).isEmpty();
        assertThat(sut.sessionHeadings(List.of(GONE))).isEmpty();
    }

    @Test
    void emptyInput_returnsEmptyMap() {

        assertThat(sut.slotTitles(List.of())).isEmpty();
        assertThat(sut.topicTitles(List.of())).isEmpty();
        assertThat(sut.sessionNames(List.of())).isEmpty();
        assertThat(sut.sessionHeadings(List.of())).isEmpty();
    }

    private Long seedCommunity() {

        jdbc.update(
                "INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                        + "VALUES ('default', 'ActivityTitleKind', now(), now(), 0) ON CONFLICT DO NOTHING",
                new MapSqlParameterSource());
        return jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, updated_at, version) "
                        + "SELECT 'default', 'activity-title-' || nextval('communities_id_seq'), 'AT', k.id, '', 'ACTIVE', now(), now(), 0 "
                        + "FROM community_kind k WHERE k.organization_id='default' AND k.label='ActivityTitleKind' RETURNING id",
                new MapSqlParameterSource(), Long.class);
    }

    private Long seedSection(Long communityId) {

        return seedSectionIn(seedSession("default", communityId, seedSeries(communityId, "AT Series"), null), 0);
    }

    private Long seedSeries(Long communityId, String title) {

        return jdbc.queryForObject(
                "INSERT INTO event_series (organization_id, community_id, title, cadence_count, cadence_unit, starts_on, preferred_time, time_zone, status, created_at, updated_at, version) "
                        + "VALUES ('default', :communityId, :title, 1, 'WEEK', '2026-01-01', '18:00', 'UTC', 'ACTIVE', now(), now(), 0) RETURNING id",
                new MapSqlParameterSource("communityId", communityId).addValue("title", title), Long.class);
    }

    private Long seedSession(String organizationId, Long communityId, Long seriesId, String title) {

        return jdbc.queryForObject(
                "INSERT INTO community_session (organization_id, series_id, community_id, status, scheduled_at, occurrence_date, notes, recording_url, has_time, title, created_at, updated_at, version) "
                        + "VALUES (:org, :seriesId, :communityId, 'SCHEDULED', now(), DATE '2026-01-01' + :occurrence, '', '', true, :title, now(), now(), 0) RETURNING id",
                new MapSqlParameterSource("seriesId", seriesId).addValue("communityId", communityId)
                        .addValue("org", organizationId).addValue("title", title).addValue("occurrence", occurrences++),
                Long.class);
    }

    private Long seedOneOff(Long communityId, String scheduledAt, String timeZone) {

        return jdbc.queryForObject(
                "INSERT INTO community_session (organization_id, community_id, status, scheduled_at, notes, recording_url, has_time, time_zone, created_at, updated_at, version) "
                        + "VALUES ('default', :communityId, 'SCHEDULED', CAST(:scheduledAt AS timestamptz), '', '', true, :timeZone, now(), now(), 0) RETURNING id",
                new MapSqlParameterSource("communityId", communityId).addValue("scheduledAt", scheduledAt)
                        .addValue("timeZone", timeZone),
                Long.class);
    }

    private Long seedSectionIn(Long sessionId, int position) {

        var sectionTypeId = jdbc.queryForObject("INSERT INTO event_section_type "
                + "(organization_id, label, expects_presenter, created_at, updated_at, version, icon, accent_color) "
                + "VALUES ('default', 'ActivityTitleSection ' || :sessionId || ' ' || :position, true, now(), now(), 0, 'wrench', '#123456') RETURNING id",
                new MapSqlParameterSource("sessionId", sessionId).addValue("position", position), Long.class);
        return jdbc.queryForObject(
                "INSERT INTO event_section (session_id, section_type_id, title, position, duration_minutes) "
                        + "VALUES (:sessionId, :sectionTypeId, 'Talks', :position, 30) RETURNING id",
                new MapSqlParameterSource("sessionId", sessionId).addValue("sectionTypeId", sectionTypeId)
                        .addValue("position", position),
                Long.class);
    }

    private Long seedSlot(Long sectionId, int position, String title) {

        return jdbc.queryForObject(
                "INSERT INTO event_slot (section_id, position, title, duration_minutes) "
                        + "VALUES (:sectionId, :position, :title, 20) RETURNING id",
                new MapSqlParameterSource("sectionId", sectionId).addValue("position", position).addValue("title",
                        title),
                Long.class);
    }

    private Long seedTopic(Long communityId, String title) {

        return seedTopicIn("default", communityId, title);
    }

    private Long seedTopicIn(String organizationId, Long communityId, String title) {

        return jdbc.queryForObject(
                "INSERT INTO topic (organization_id, community_id, title, description, proposer_user_id, status, created_at, updated_at, version) "
                        + "VALUES (:org, :communityId, :title, '', 'activity-title-proposer', 'PROPOSED', now(), now(), 0) RETURNING id",
                new MapSqlParameterSource("org", organizationId).addValue("communityId", communityId).addValue("title",
                        title),
                Long.class);
    }
}
