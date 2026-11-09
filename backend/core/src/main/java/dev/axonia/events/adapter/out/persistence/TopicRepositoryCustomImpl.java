package dev.axonia.events.adapter.out.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.events.api.TopicKind;
import dev.axonia.events.api.TopicStatus;
import dev.axonia.events.application.TopicFilter;
import dev.axonia.events.domain.TopicCounts;
import dev.axonia.events.domain.TopicSchedule;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class TopicRepositoryCustomImpl implements TopicRepositoryCustom {

    private final JdbcAggregateTemplate jdbcAggregateTemplate;
    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Plain SQL because the kind is a predicate on the speakers ({@link TopicSpeakerSql#HAS_SPEAKER}), which no
     * {@code Criteria} can express. The page is chosen by id here and the aggregates, tags and speakers included, are
     * loaded for exactly those ids. {@code created_at} is the only sortable property, and the {@code id DESC}
     * tiebreaker is hardcoded so two topics sharing a {@code created_at} keep a defined order across page boundaries.
     */
    @Override
    public Page<TopicEntity> findFiltered(String organizationId, Long communityId, TopicFilter filter,
            Pageable pageable) {

        var where = new StringBuilder(" WHERE t.organization_id = :organizationId AND t.community_id = :communityId");
        var params = new MapSqlParameterSource().addValue("organizationId", organizationId).addValue("communityId",
                communityId);
        if (filter.kind() != null) {
            where.append(filter.kind() == TopicKind.OFFER ? " AND " : " AND NOT ").append(TopicSpeakerSql.HAS_SPEAKER);
        }
        if (filter.status() != null) {
            where.append(" AND t.status = :status");
            params.addValue("status", filter.status().name());
        }

        var ids = jdbc.queryForList(
                "SELECT t.id FROM topic t" + where + orderBy(pageable.getSort()) + " LIMIT :limit OFFSET :offset",
                new MapSqlParameterSource(params.getValues()).addValue("limit", pageable.getPageSize())
                        .addValue("offset", pageable.getOffset()),
                Long.class);
        Long total = jdbc.queryForObject("SELECT count(*) FROM topic t" + where, params, Long.class);

        return new PageImpl<>(inOrder(ids), pageable, Objects.requireNonNullElse(total, 0L));
    }

    private static String orderBy(Sort sort) {

        var createdAt = new StringBuilder();
        for (var order : sort) {
            if (!"created_at".equals(order.getProperty())) {
                throw new IllegalArgumentException("Topics sort by created_at only, not " + order.getProperty());
            }
            createdAt.append("t.created_at ").append(order.isAscending() ? "ASC" : "DESC").append(", ");
        }
        return " ORDER BY " + createdAt + "t.id DESC";
    }

    private List<TopicEntity> inOrder(List<Long> ids) {

        var byId = new HashMap<Long, TopicEntity>();
        jdbcAggregateTemplate.findAllById(ids, TopicEntity.class).forEach(e -> byId.put(e.getId(), e));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    @Override
    public Map<Long, List<String>> findSpeakers(String organizationId, Collection<Long> topicIds) {

        if (topicIds.isEmpty()) {
            return Map.of();
        }
        var sql = "SELECT t.id AS topic_id, s.user_id FROM topic t CROSS JOIN LATERAL (" + TopicSpeakerSql.SPEAKERS_OF_T
                + ") s WHERE t.organization_id = :organizationId AND t.id IN (:topicIds) ORDER BY t.id, s.user_id";
        var params = new MapSqlParameterSource().addValue("organizationId", organizationId).addValue("topicIds",
                topicIds);
        var speakers = new LinkedHashMap<Long, List<String>>();
        jdbc.query(sql, params, rs -> {
            speakers.computeIfAbsent(rs.getLong("topic_id"), id -> new ArrayList<>()).add(rs.getString("user_id"));
        });
        return speakers;
    }

    // Rows are ordered preferred-first per topic: soonest upcoming session, else latest past one -
    // consumers taking findFirst rely on this.
    @Override
    public List<TopicSchedule> findSchedules(String organizationId, Collection<Long> topicIds) {

        if (topicIds.isEmpty()) {
            return List.of();
        }
        // The session's name, the one its page shows, down to the generic "Session" the backlog has no other state for.
        var sql = """
                SELECT sl.topic_id, cs.id AS session_id, sec.id AS section_id, sl.id AS slot_id,
                       cs.scheduled_at, COALESCE(""" + SessionNameSql.NAME + """
                , 'Session') AS schedule_label
                FROM event_slot sl
                JOIN event_section sec ON sl.section_id = sec.id
                JOIN community_session cs ON sec.session_id = cs.id
                """ + SessionNameSql.SOURCES + """
                WHERE cs.organization_id = :organizationId AND cs.status <> 'CANCELLED' AND sl.topic_id IN (:topicIds)
                ORDER BY sl.topic_id, (cs.scheduled_at < now()),
                         CASE WHEN cs.scheduled_at < now()
                              THEN -EXTRACT(EPOCH FROM cs.scheduled_at)
                              ELSE EXTRACT(EPOCH FROM cs.scheduled_at) END
                """;
        var params = new MapSqlParameterSource().addValue("organizationId", organizationId).addValue("topicIds",
                topicIds);
        return jdbc.query(sql, params,
                (rs, n) -> new TopicSchedule(rs.getLong("topic_id"), rs.getLong("session_id"), rs.getLong("section_id"),
                        rs.getLong("slot_id"), rs.getTimestamp("scheduled_at").toInstant(),
                        rs.getString("schedule_label")));
    }

    @Override
    public Page<TopicEntity> findPast(String organizationId, Long communityId, String q, Pageable pageable) {

        var search = q != null && !q.isBlank();
        var where = """
                WHERE t.organization_id = :organizationId AND t.community_id = :communityId
                  AND ( t.status = 'DONE'
                     OR ( t.status = 'SCHEDULED' AND EXISTS (
                            SELECT 1 FROM event_slot sl
                            JOIN event_section sec ON sl.section_id = sec.id
                            JOIN community_session cs ON sec.session_id = cs.id
                            WHERE sl.topic_id = t.id AND cs.status <> 'CANCELLED'
                              AND cs.scheduled_at < now() ) ) )
                """ + (search ? " AND (t.title ILIKE :like OR t.description ILIKE :like)" : "");

        var sql = "SELECT t.id, t.organization_id, t.community_id, t.title, t.description, "
                + "t.proposer_user_id, t.status, t.created_at, t.updated_at, t.version FROM topic t " + where + """
                         ORDER BY (SELECT MAX(cs.scheduled_at) FROM event_slot sl
                                   JOIN event_section sec ON sl.section_id = sec.id
                                   JOIN community_session cs ON sec.session_id = cs.id
                                   WHERE sl.topic_id = t.id AND cs.status <> 'CANCELLED') DESC NULLS LAST,
                                  t.updated_at DESC
                         LIMIT :limit OFFSET :offset
                        """;

        var countSql = "SELECT count(*) FROM topic t " + where;

        var params = new MapSqlParameterSource().addValue("organizationId", organizationId).addValue("communityId",
                communityId);
        if (search) {
            params.addValue("like", "%" + q.trim() + "%");
        }

        // separate copy so :limit/:offset don't leak into the count query's params
        var content = jdbc.query(sql,
                new MapSqlParameterSource(params.getValues()).addValue("limit", pageable.getPageSize())
                        .addValue("offset", pageable.getOffset()),
                (rs, n) -> TopicEntity.builder().id(rs.getLong("id")).organizationId(rs.getString("organization_id"))
                        .communityId(rs.getLong("community_id")).title(rs.getString("title"))
                        .description(rs.getString("description")).proposerUserId(rs.getString("proposer_user_id"))
                        .status(TopicStatus.valueOf(rs.getString("status")))
                        .createdAt(rs.getTimestamp("created_at").toInstant())
                        .updatedAt(rs.getTimestamp("updated_at").toInstant()).version(rs.getLong("version")).build());

        Long total = jdbc.queryForObject(countSql, params, Long.class);

        return new PageImpl<>(content, pageable, Objects.requireNonNullElse(total, 0L));
    }

    @Override
    public TopicCounts countByLifecycle(String organizationId, Long communityId) {

        var sql = """
                SELECT COUNT(*) FILTER (WHERE t.status = 'PROPOSED') AS proposed,
                       COUNT(*) FILTER (WHERE t.status = 'SCHEDULED' AND EXISTS (
                            SELECT 1 FROM event_slot sl
                            JOIN event_section sec ON sl.section_id = sec.id
                            JOIN community_session cs ON sec.session_id = cs.id
                            WHERE sl.topic_id = t.id AND cs.status <> 'CANCELLED'
                              AND cs.scheduled_at >= now() )) AS scheduled,
                       COUNT(*) FILTER (WHERE t.status = 'DONE'
                         OR ( t.status = 'SCHEDULED' AND EXISTS (
                            SELECT 1 FROM event_slot sl
                            JOIN event_section sec ON sl.section_id = sec.id
                            JOIN community_session cs ON sec.session_id = cs.id
                            WHERE sl.topic_id = t.id AND cs.status <> 'CANCELLED'
                              AND cs.scheduled_at < now() ))) AS done
                FROM topic t
                WHERE t.organization_id = :organizationId AND t.community_id = :communityId
                """;
        var params = new MapSqlParameterSource().addValue("organizationId", organizationId).addValue("communityId",
                communityId);
        return Objects.requireNonNull(jdbc.queryForObject(sql, params, (rs, n) -> TopicCounts.builder()
                .proposed(rs.getLong("proposed")).scheduled(rs.getLong("scheduled")).done(rs.getLong("done")).build()));
    }
}
