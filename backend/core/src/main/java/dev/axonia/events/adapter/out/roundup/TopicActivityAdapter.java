package dev.axonia.events.adapter.out.roundup;

import java.util.Collection;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.roundup.api.spi.TopicActivity;
import lombok.RequiredArgsConstructor;

/**
 * Events' implementation of the roundup's topic port. Read-only by construction; the organization arrives as a
 * parameter rather than through this module's request-scoped {@code OrganizationContext}, because a roundup is
 * assembled with no JWT in scope.
 *
 * <p>
 * The organization predicate is on {@code community_session} rather than on the tag table, which carries no
 * organization column of its own: the join through the session is what scopes it, and dropping it would count another
 * tenant's tags for any session id that happened to collide.
 */
@Component
@RequiredArgsConstructor
class TopicActivityAdapter implements TopicActivity {

    private static final String TAGS_OF_SESSIONS = """
            SELECT est.tag, count(*) AS tag_count
            FROM event_slot_tag est
            JOIN event_slot sl ON sl.id = est.slot_id
            JOIN event_section sec ON sl.section_id = sec.id
            JOIN community_session cs ON cs.id = sec.session_id
            WHERE cs.organization_id = :organizationId AND cs.id IN (:sessionIds)
            GROUP BY est.tag
            ORDER BY count(*) DESC, est.tag
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional(readOnly = true)
    public List<TagCount> tagsForSessions(String organizationId, Collection<Long> sessionIds) {

        if (sessionIds.isEmpty()) {
            return List.of();
        }
        var params = new MapSqlParameterSource().addValue("organizationId", organizationId).addValue("sessionIds",
                sessionIds);
        return jdbc.query(TAGS_OF_SESSIONS, params,
                (rs, rowNum) -> new TagCount(rs.getString("tag"), rs.getInt("tag_count")));
    }
}
