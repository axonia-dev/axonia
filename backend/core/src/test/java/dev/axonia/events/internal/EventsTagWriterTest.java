package dev.axonia.events.internal;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.TagUsage;
import dev.axonia.TagUsageKind;

import static org.assertj.core.api.Assertions.assertThat;

class EventsTagWriterTest extends CoreJdbcTestSupport {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private EventsTagWriter writer;

    private long community;
    private long otherCommunity;

    @BeforeEach
    void setUp() {

        writer = new EventsTagWriter(jdbc);
        seedReferenceCatalogs();
        community = community("backend-guild");
        otherCommunity = community("data-guild");
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
    void renamingRetagsTopicsAndTalksOfTheCommunity() {

        var topic = topicWith(community, "kafak");
        var slot = slotWith(community, "kafak");

        writer.rename(community, "kafak", "kafka");

        assertThat(topicTagsOf(topic)).containsExactly("kafka");
        assertThat(slotTagsOf(slot)).containsExactly("kafka");
    }

    @Test
    void renamingFoldsATopicThatAlreadyCarriesTheTarget() {

        var topic = topicWith(community, "kafak", "kafka");

        writer.rename(community, "kafak", "kafka");

        assertThat(topicTagsOf(topic)).containsExactly("kafka");
    }

    @Test
    void renamingFoldsATalkThatAlreadyCarriesTheTarget() {

        var slot = slotWith(community, "kafak", "kafka");

        writer.rename(community, "kafak", "kafka");

        assertThat(slotTagsOf(slot)).containsExactly("kafka");
    }

    @Test
    void renamingLeavesAnotherCommunitysTopicsAlone() {

        var theirs = topicWith(otherCommunity, "kafak");
        var theirSlot = slotWith(otherCommunity, "kafak");

        writer.rename(community, "kafak", "kafka");

        assertThat(topicTagsOf(theirs)).containsExactly("kafak");
        assertThat(slotTagsOf(theirSlot)).containsExactly("kafak");
    }

    @Test
    void removingStripsTheTagFromTopicsAndTalks() {

        var topic = topicWith(community, "misc", "kafka");
        var slot = slotWith(community, "misc");

        writer.remove(community, "misc");

        assertThat(topicTagsOf(topic)).containsExactly("kafka");
        assertThat(slotTagsOf(slot)).isEmpty();
    }

    @Test
    void removingLeavesAnotherCommunitysContentAlone() {

        var theirTopic = topicWith(otherCommunity, "misc");
        var theirSlot = slotWith(otherCommunity, "misc");

        writer.remove(community, "misc");

        assertThat(topicTagsOf(theirTopic)).containsExactly("misc");
        assertThat(slotTagsOf(theirSlot)).containsExactly("misc");
    }

    @Test
    void usageReportsBothKindsAndOmitsTheEmptyOne() {

        topicWith(community, "kafka");
        topicWith(community, "kafka");

        assertThat(writer.usage(community, "kafka")).containsExactly(new TagUsage(TagUsageKind.TOPIC, 2));
    }

    @Test
    void usageReportsTalksAlongsideTopics() {

        topicWith(community, "kafka");
        slotWith(community, "kafka");
        slotWith(otherCommunity, "kafka");

        assertThat(writer.usage(community, "kafka")).containsExactly(new TagUsage(TagUsageKind.TOPIC, 1),
                new TagUsage(TagUsageKind.TALK, 1));
    }

    @Test
    void usageIsEmptyWhenNothingCarriesTheTag() {

        assertThat(writer.usage(community, "kafka")).isEmpty();
    }

    @Test
    void theTagsInACommunityAreThoseItsTopicsAndTalksCarryEachOnce() {

        topicOfKind(community, "REQUEST", "grpc", "kafka");
        topicWith(community, "flink", "kafka", "k8s");
        slotWith(community, "helm", "k8s");
        topicWith(otherCommunity, "rust");

        assertThat(writer.tagsIn(community)).containsExactly("flink", "grpc", "helm", "k8s", "kafka");
    }

    private long community(String slug) {

        return jdbc.queryForObject("""
                INSERT INTO communities (organization_id, slug, name, kind_id, description, status,
                        can_start_series, can_edit_structure, can_queue_topics, created_at, updated_at, version)
                VALUES ('default', :slug, 'Guild', (SELECT min(id) FROM community_kind), 'd', 'ACTIVE',
                        false, false, false, now(), now(), 0)
                RETURNING id
                """, new MapSqlParameterSource("slug", slug), Long.class);
    }

    private long topicWith(long communityId, String... tags) {

        return topicOfKind(communityId, "OFFER", tags);
    }

    private long topicOfKind(long communityId, String kind, String... tags) {

        var topicId = jdbc.queryForObject("""
                INSERT INTO topic (organization_id, community_id, kind, title, description, proposer_user_id, status,
                        created_at, updated_at, version)
                VALUES ('default', :communityId, :kind, 'T', 'd', 'alice', 'PROPOSED', now(), now(), 0)
                RETURNING id
                """, new MapSqlParameterSource("communityId", communityId).addValue("kind", kind), Long.class);

        for (var tag : tags) {
            jdbc.update("INSERT INTO topic_tag (topic_id, tag) VALUES (:topicId, :tag)",
                    new MapSqlParameterSource("topicId", topicId).addValue("tag", tag));
        }
        return topicId;
    }

    private long slotWith(long communityId, String... tags) {

        var slotId = jdbc.queryForObject("""
                INSERT INTO event_slot (section_id, position, title, duration_minutes)
                VALUES (:sectionId, 0, 'Slot', 15)
                RETURNING id
                """, new MapSqlParameterSource("sectionId", section(communityId)), Long.class);

        for (var tag : tags) {
            jdbc.update("INSERT INTO event_slot_tag (slot_id, tag) VALUES (:slotId, :tag)",
                    new MapSqlParameterSource("slotId", slotId).addValue("tag", tag));
        }
        return slotId;
    }

    private long section(long communityId) {

        var seriesId = jdbc.queryForObject("""
                INSERT INTO event_series (organization_id, community_id, title, cadence_count, cadence_unit,
                        starts_on, time_zone, status, created_at, updated_at, version)
                VALUES ('default', :communityId, 'Talks', 1, 'WEEK', '2026-01-01', 'UTC', 'ACTIVE', now(), now(), 0)
                RETURNING id
                """, new MapSqlParameterSource("communityId", communityId), Long.class);

        var sessionId = jdbc.queryForObject(
                """
                        INSERT INTO community_session (organization_id, series_id, community_id, status, scheduled_at,
                                occurrence_date, notes, recording_url, has_time, created_at, updated_at, version)
                        VALUES ('default', :seriesId, :communityId, 'SCHEDULED', now(), current_date, '', '', true, now(), now(), 0)
                        RETURNING id
                        """,
                new MapSqlParameterSource("seriesId", seriesId).addValue("communityId", communityId), Long.class);

        return jdbc.queryForObject("""
                INSERT INTO event_section (session_id, position, section_type_id, title)
                VALUES (:sessionId, 0, (SELECT min(id) FROM event_section_type), 'Talks')
                RETURNING id
                """, new MapSqlParameterSource("sessionId", sessionId), Long.class);
    }

    private List<String> topicTagsOf(long topicId) {

        return jdbc.queryForList("SELECT tag FROM topic_tag WHERE topic_id = :topicId",
                new MapSqlParameterSource("topicId", topicId), String.class);
    }

    private List<String> slotTagsOf(long slotId) {

        return jdbc.queryForList("SELECT tag FROM event_slot_tag WHERE slot_id = :slotId",
                new MapSqlParameterSource("slotId", slotId), String.class);
    }
}
