package dev.axonia.events.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.CommunityTagWriter;
import dev.axonia.TagUsage;
import dev.axonia.TagUsageKind;
import lombok.RequiredArgsConstructor;

/**
 * The events context's slice of a curator's rename or delete: the tags on proposed topics and on talk slots.
 *
 * <p>
 * A topic names its community directly; a slot reaches it through its section's session. Both children carry no
 * organization of their own - their aggregate root does - so RLS applies through the root, and the community bound is
 * expressed by the join.
 */
@Component
@RequiredArgsConstructor
class EventsTagWriter implements CommunityTagWriter {

    private static final String COUNT_TOPICS_SQL = """
            SELECT count(*)
              FROM topic_tag t
              JOIN topic p ON p.id = t.topic_id
             WHERE p.community_id = :communityId AND t.tag = :tag
            """;

    private static final String COUNT_TALKS_SQL = withCommunitySlots("""
            SELECT count(*)
              FROM event_slot_tag t
             WHERE t.slot_id IN (<community-slots>) AND t.tag = :tag
            """);

    private static final String TOPIC_TAGS_SQL = """
            SELECT DISTINCT t.tag
              FROM topic_tag t
              JOIN topic p ON p.id = t.topic_id
             WHERE p.community_id = :communityId
            """;

    private static final String TALK_TAGS_SQL = withCommunitySlots("""
            SELECT DISTINCT t.tag
              FROM event_slot_tag t
             WHERE t.slot_id IN (<community-slots>)
            """);

    private static final String DELETE_COLLIDING_TOPIC_SQL = """
            DELETE FROM topic_tag t
             USING topic p
             WHERE t.topic_id = p.id
               AND p.community_id = :communityId
               AND t.tag = :from
               AND EXISTS (SELECT 1 FROM topic_tag o WHERE o.topic_id = t.topic_id AND o.tag = :to)
            """;

    private static final String RENAME_TOPIC_SQL = """
            UPDATE topic_tag t
               SET tag = :to
              FROM topic p
             WHERE t.topic_id = p.id AND p.community_id = :communityId AND t.tag = :from
            """;

    private static final String REMOVE_TOPIC_SQL = """
            DELETE FROM topic_tag t
             USING topic p
             WHERE t.topic_id = p.id AND p.community_id = :communityId AND t.tag = :tag
            """;

    private static final String DELETE_COLLIDING_TALK_SQL = withCommunitySlots("""
            DELETE FROM event_slot_tag t
             WHERE t.slot_id IN (<community-slots>)
               AND t.tag = :from
               AND EXISTS (SELECT 1 FROM event_slot_tag o WHERE o.slot_id = t.slot_id AND o.tag = :to)
            """);

    private static final String RENAME_TALK_SQL = withCommunitySlots("""
            UPDATE event_slot_tag t
               SET tag = :to
             WHERE t.slot_id IN (<community-slots>) AND t.tag = :from
            """);

    private static final String REMOVE_TALK_SQL = withCommunitySlots("""
            DELETE FROM event_slot_tag t
             WHERE t.slot_id IN (<community-slots>) AND t.tag = :tag
            """);

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional(readOnly = true)
    public List<String> tagsIn(long communityId) {

        var params = new MapSqlParameterSource("communityId", communityId);
        return Stream.concat(jdbc.queryForList(TOPIC_TAGS_SQL, params, String.class).stream(),
                jdbc.queryForList(TALK_TAGS_SQL, params, String.class).stream()).distinct().sorted().toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagUsage> usage(long communityId, String tag) {

        var params = new MapSqlParameterSource("communityId", communityId).addValue("tag", tag);
        var usages = new ArrayList<TagUsage>(2);
        addIfAny(usages, TagUsageKind.TOPIC, jdbc.queryForObject(COUNT_TOPICS_SQL, params, Integer.class));
        addIfAny(usages, TagUsageKind.TALK, jdbc.queryForObject(COUNT_TALKS_SQL, params, Integer.class));
        return List.copyOf(usages);
    }

    @Override
    public void rename(long communityId, String from, String to) {

        var params = new MapSqlParameterSource("communityId", communityId).addValue("from", from).addValue("to", to);
        jdbc.update(DELETE_COLLIDING_TOPIC_SQL, params);
        jdbc.update(RENAME_TOPIC_SQL, params);
        jdbc.update(DELETE_COLLIDING_TALK_SQL, params);
        jdbc.update(RENAME_TALK_SQL, params);
    }

    @Override
    public void remove(long communityId, String tag) {

        var params = new MapSqlParameterSource("communityId", communityId).addValue("tag", tag);
        jdbc.update(REMOVE_TOPIC_SQL, params);
        jdbc.update(REMOVE_TALK_SQL, params);
    }

    private static String withCommunitySlots(String sql) {

        return sql.replace("<community-slots>", CommunitySlots.SQL);
    }

    private static void addIfAny(List<TagUsage> usages, TagUsageKind kind, Integer count) {

        if (count != null && count > 0) {
            usages.add(new TagUsage(kind, count));
        }
    }

    /**
     * The subquery held apart from the statements that embed it: a nested class is initialized on first read, so no
     * declaration order among this class's own fields can hand {@link #withCommunitySlots} a null.
     */
    private static final class CommunitySlots {

        private static final String SQL = """
                SELECT s.id
                  FROM event_slot s
                  JOIN event_section sec ON sec.id = s.section_id
                  JOIN community_session cs ON cs.id = sec.session_id
                 WHERE cs.community_id = :communityId
                """;

        private CommunitySlots() {
        }
    }
}
