package dev.axonia.events.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
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
     * {@code pageable}'s sort reaches SQL as a raw, unquoted identifier: {@code created_at} is not a
     * {@link TopicEntity} property name (the field is {@code createdAt}), so {@code QueryMapper} cannot resolve it to a
     * column via the entity's property path and instead splices the string straight into {@code ORDER BY}.
     * {@link dev.axonia.shared.api.web.Pageables#requireSortIn} is what keeps that safe today, and it runs in the
     * controller before the request ever reaches here - a future caller of this method must allowlist its own sort
     * input the same way before wiring it in, or a request parameter lands directly in the query.
     * <p>
     * The {@code id DESC} tiebreaker below is deliberately not part of that caller-supplied sort: without it, two
     * topics sharing a {@code created_at} value have no defined relative order, and {@code LIMIT}/{@code OFFSET} over
     * an undefined order can repeat or skip a row across a page boundary - the same failure mode a missing
     * {@code ORDER BY} entirely would cause, just narrower. Keeping it hardcoded means no request parameter can reach
     * it.
     */
    @Override
    public Page<TopicEntity> findFiltered(String organizationId, Long communityId, TopicFilter filter,
            Pageable pageable) {

        var criteria = Criteria.where("organizationId").is(organizationId).and("communityId").is(communityId);
        if (filter.kind() != null) {
            criteria = criteria.and("kind").is(filter.kind());
        }
        if (filter.status() != null) {
            criteria = criteria.and("status").is(filter.status());
        }

        var sort = pageable.getSort().and(Sort.by(Sort.Direction.DESC, "id"));
        var baseQuery = Query.query(criteria);
        var pageQuery = baseQuery.sort(sort).offset(pageable.getOffset()).limit(pageable.getPageSize());

        List<TopicEntity> content = jdbcAggregateTemplate.findAll(pageQuery, TopicEntity.class);
        long total = jdbcAggregateTemplate.count(baseQuery, TopicEntity.class);

        return new PageImpl<>(content, pageable, total);
    }

    // Rows are ordered preferred-first per topic: soonest upcoming session, else latest past one -
    // consumers taking findFirst rely on this.
    @Override
    public List<TopicSchedule> findSchedules(String organizationId, Collection<Long> topicIds) {

        if (topicIds.isEmpty()) {
            return List.of();
        }
        var sql = """
                SELECT sl.topic_id, cs.id AS session_id, sec.id AS section_id, sl.id AS slot_id,
                       cs.scheduled_at, COALESCE(sr.title, cs.title) AS schedule_label
                FROM event_slot sl
                JOIN event_section sec ON sl.section_id = sec.id
                JOIN community_session cs ON sec.session_id = cs.id
                LEFT JOIN event_series sr ON cs.series_id = sr.id
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

        var sql = "SELECT t.id, t.organization_id, t.community_id, t.kind, t.title, t.description, "
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
                        .communityId(rs.getLong("community_id")).kind(TopicKind.valueOf(rs.getString("kind")))
                        .title(rs.getString("title")).description(rs.getString("description"))
                        .proposerUserId(rs.getString("proposer_user_id"))
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
