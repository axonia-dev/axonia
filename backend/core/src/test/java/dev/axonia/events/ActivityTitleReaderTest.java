package dev.axonia.events;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class ActivityTitleReaderTest extends CoreJdbcTestSupport {

    /** No row reaches this far, so it stands for a source the caller's reference outlived. */
    private static final long GONE = 999_000L;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private ActivityTitleReader sut;

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
    void anIdThatNoLongerResolvesIsAbsentRatherThanAThrow() {

        assertThat(sut.slotTitles(List.of(GONE))).isEmpty();
        assertThat(sut.topicTitles(List.of(GONE))).isEmpty();
    }

    @Test
    void emptyInput_returnsEmptyMap() {

        assertThat(sut.slotTitles(List.of())).isEmpty();
        assertThat(sut.topicTitles(List.of())).isEmpty();
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

        var seriesId = jdbc.queryForObject(
                "INSERT INTO event_series (organization_id, community_id, title, cadence_count, cadence_unit, starts_on, preferred_time, time_zone, status, created_at, updated_at, version) "
                        + "VALUES ('default', :communityId, 'AT Series', 1, 'WEEK', '2026-01-01', '18:00', 'UTC', 'ACTIVE', now(), now(), 0) RETURNING id",
                new MapSqlParameterSource("communityId", communityId), Long.class);
        var sessionId = jdbc.queryForObject(
                "INSERT INTO community_session (organization_id, series_id, community_id, status, scheduled_at, occurrence_date, notes, recording_url, has_time, created_at, updated_at, version) "
                        + "VALUES ('default', :seriesId, :communityId, 'SCHEDULED', now(), now()::date, '', '', true, now(), now(), 0) RETURNING id",
                new MapSqlParameterSource("seriesId", seriesId).addValue("communityId", communityId), Long.class);
        var sectionTypeId = jdbc.queryForObject("INSERT INTO event_section_type "
                + "(organization_id, label, expects_presenter, created_at, updated_at, version, icon, accent_color) "
                + "VALUES ('default', 'ActivityTitleSection', true, now(), now(), 0, 'wrench', '#123456') RETURNING id",
                new MapSqlParameterSource(), Long.class);
        return jdbc.queryForObject(
                "INSERT INTO event_section (session_id, section_type_id, title, position, duration_minutes) "
                        + "VALUES (:sessionId, :sectionTypeId, 'Talks', 0, 30) RETURNING id",
                new MapSqlParameterSource("sessionId", sessionId).addValue("sectionTypeId", sectionTypeId), Long.class);
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
                "INSERT INTO topic (organization_id, community_id, kind, title, description, proposer_user_id, status, created_at, updated_at, version) "
                        + "VALUES (:org, :communityId, 'OFFER', :title, '', 'activity-title-proposer', 'PROPOSED', now(), now(), 0) RETURNING id",
                new MapSqlParameterSource("org", organizationId).addValue("communityId", communityId).addValue("title",
                        title),
                Long.class);
    }
}
