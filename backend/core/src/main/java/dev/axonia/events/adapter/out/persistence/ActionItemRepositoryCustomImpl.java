package dev.axonia.events.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.events.api.ActionItemOutcome;
import dev.axonia.events.application.ActionItemFilter;
import dev.axonia.events.application.CheckpointResolver;
import lombok.RequiredArgsConstructor;

/**
 * Hand-written, matching {@code ArtifactSearchRepositoryImpl}'s own shape and reasoning:
 * {@code JdbcAggregateTemplate.findAll} batches the root {@code action_item} rows through its own {@code IN}, but still
 * loads each row's {@code tags} {@code @MappedCollection} with its own {@code WHERE action_item_id = ?} - one statement
 * per row, not the single batched query its aggregate-loading machinery seems to promise. So both the roots and the
 * tags are read here as plain SQL, one query each for the whole page.
 *
 * <p>
 * {@code pageable}'s sort is never spliced into SQL as a raw identifier: {@link #orderClause} recognises exactly three
 * server-known keys ({@code created_at}, {@code checkpoint}, {@code updated_at}) and maps each to its own hardcoded
 * {@code ORDER BY} fragment, so a request can pick between them but can never inject an arbitrary column or expression
 * - {@code Pageables.requireSortIn}, run in the controller, only proves the requested key is one of these three; this
 * is what makes that promise safe to trust.
 */
@RequiredArgsConstructor
public class ActionItemRepositoryCustomImpl implements ActionItemRepositoryCustom {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    @Override
    public Page<ActionItemEntity> findFiltered(String organizationId, Long communityId, ActionItemFilter filter,
            Pageable pageable) {

        var where = new StringBuilder(
                " FROM action_item WHERE organization_id = :organizationId AND community_id = :communityId");
        // One where clause feeds both the count and the id query, so no filter can reach one and miss the other.
        var params = new MapSqlParameterSource().addValue("organizationId", organizationId)
                .addValue("communityId", communityId).addValue("now", Timestamp.from(clock.instant()));
        if (filter.outcome() != null) {
            where.append(" AND outcome = :outcome");
            params.addValue("outcome", filter.outcome().name());
        }
        if (filter.originSessionId() != null) {
            where.append(" AND origin_session_id = :originSessionId");
            params.addValue("originSessionId", filter.originSessionId());
        }
        if (filter.checkpointSessionId() != null) {
            where.append(" AND ").append(CHECKPOINT_SESSION_SQL).append(" = :checkpointSessionId");
            params.addValue("checkpointSessionId", filter.checkpointSessionId());
        }

        var total = jdbc.queryForObject("SELECT count(*)" + where, params, Long.class);
        if (total == null || total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        var ids = jdbc.queryForList(
                "SELECT id" + where + orderClause(pageable.getSort()) + " LIMIT :limit OFFSET :offset", params,
                Long.class);
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, total);
        }
        return new PageImpl<>(hydrate(ids, organizationId), pageable, total);
    }

    /**
     * {@code created_at} is the default: whichever direction the caller picked, tied by {@code id DESC}. {@code
     * checkpoint} is "grouped by checkpoint session, ordered by checkpoint date then due date" - a pinned item sorts by
     * the session it is pinned to, an unpinned one by the community's own soonest upcoming session (every unpinned item
     * on a page shares that one date, the same fact {@link CheckpointResolver#resolveAll} relies on), tied by
     * {@code due_date} then {@code id}, neither of which a request can redirect.
     *
     * <p>
     * {@code updated_at} is settle order: a settled item can no longer be edited ({@code update} refuses anything but
     * {@code OPEN}), so for a {@code DONE} or {@code DROPPED} row the last write is the settlement itself.
     */
    private String orderClause(Sort sort) {

        var checkpoint = sort.getOrderFor("checkpoint");
        if (checkpoint != null) {
            var direction = checkpoint.getDirection() == Sort.Direction.DESC ? "DESC" : "ASC";
            return " ORDER BY " + CHECKPOINT_DATE_SQL + " " + direction
                    + " NULLS LAST, due_date ASC NULLS LAST, id ASC";
        }
        var updatedAt = sort.getOrderFor("updated_at");
        if (updatedAt != null) {
            return " ORDER BY updated_at " + descendingUnlessAscending(updatedAt) + ", id DESC";
        }
        return " ORDER BY created_at " + descendingUnlessAscending(sort.getOrderFor("created_at")) + ", id DESC";
    }

    private static String descendingUnlessAscending(Sort.Order order) {

        return order != null && order.getDirection() == Sort.Direction.ASC ? "ASC" : "DESC";
    }

    /**
     * One roots query plus one tags query for the whole page, never one pair per id - see the class javadoc.
     */
    private List<ActionItemEntity> hydrate(List<Long> ids, String organizationId) {

        var rootParams = new MapSqlParameterSource().addValue("ids", ids).addValue("organizationId", organizationId);
        var roots = jdbc.query(ROOTS_SQL, rootParams, ActionItemRepositoryCustomImpl::mapRoot);
        var tagsByItemId = tagsByActionItemId(ids);

        var byId = new HashMap<Long, ActionItemEntity>();
        for (var root : roots) {
            byId.put(root.getId(), root.toBuilder().tags(tagsByItemId.getOrDefault(root.getId(), Set.of())).build());
        }
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private Map<Long, Set<ActionItemTagRef>> tagsByActionItemId(List<Long> ids) {

        var params = new MapSqlParameterSource().addValue("ids", ids);
        var tagsByItemId = new HashMap<Long, Set<ActionItemTagRef>>();
        jdbc.query(TAGS_SQL, params, rs -> {
            tagsByItemId.computeIfAbsent(rs.getLong("action_item_id"), id -> new HashSet<>())
                    .add(ActionItemTagRef.builder().tag(rs.getString("tag")).build());
        });
        return tagsByItemId;
    }

    private static ActionItemEntity mapRoot(ResultSet rs, int rowNum) throws SQLException {

        return ActionItemEntity.builder().id(rs.getLong("id")).organizationId(rs.getString("organization_id"))
                .communityId(rs.getLong("community_id")).title(rs.getString("title"))
                .description(rs.getString("description")).raisedByUserId(rs.getString("raised_by_user_id"))
                .assigneeUserId(rs.getString("assignee_user_id")).dueDate(rs.getObject("due_date", LocalDate.class))
                .originSessionId(rs.getObject("origin_session_id", Long.class))
                .checkpointSessionId(rs.getObject("checkpoint_session_id", Long.class))
                .outcome(ActionItemOutcome.valueOf(rs.getString("outcome")))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .updatedAt(rs.getTimestamp("updated_at").toInstant()).version(rs.getLong("version")).build();
    }

    private static final String ROOTS_SQL = """
            SELECT id, organization_id, community_id, title, description, raised_by_user_id, assignee_user_id,
                   due_date, origin_session_id, checkpoint_session_id, outcome, created_at, updated_at, version
              FROM action_item
             WHERE id IN (:ids) AND organization_id = :organizationId
            """;

    private static final String TAGS_SQL = "SELECT action_item_id, tag FROM action_item_tag WHERE action_item_id IN (:ids)";

    /**
     * Where an item is answered, resolved: a pinned item's own session, an unpinned one's the community's soonest
     * still-upcoming session - the same {@code status NOT IN ('CANCELLED', 'SKIPPED', 'HELD') AND scheduled_at > :now}
     * rule {@code SessionRepository.findSoonestUpcoming} applies, kept in sync with it by hand since this query has no
     * Java caller to share the constant with. {@code :now} is bound from the injected {@link Clock} - the same one
     * {@link CheckpointResolver} reads - never the database's own {@code now()}: the application drives time, SQL
     * {@code now()} is never the source. {@code organization_id} is repeated here even though {@code community_id}
     * alone is already globally unique, matching {@code ArtifactSearchRepositoryImpl}'s {@code ROOTS_SQL} convention of
     * a redundant tenant predicate on every query.
     *
     * <p>
     * The date form sorts a page and the id form filters one; both are this one template with a different column, so
     * the resolution a caller filters on can never disagree with the one it sorts and displays by.
     *
     * <p>
     * The fallback arm only fires for an {@code OPEN} item: a settled one is not waiting on anything, so it late-binds
     * to nothing - only its own pin, if it has one, can still name a session.
     */
    private static String checkpointSql(String column) {

        return """
                COALESCE(
                  (SELECT cs.{column} FROM community_session cs
                    WHERE cs.id = action_item.checkpoint_session_id
                      AND cs.organization_id = action_item.organization_id
                      AND cs.community_id = action_item.community_id),
                  CASE WHEN action_item.outcome = 'OPEN' THEN
                    (SELECT cs.{column} FROM community_session cs
                      WHERE cs.organization_id = action_item.organization_id
                        AND cs.community_id = action_item.community_id
                        AND cs.status NOT IN ('CANCELLED', 'SKIPPED', 'HELD') AND cs.scheduled_at > :now
                      ORDER BY cs.scheduled_at ASC LIMIT 1)
                  END
                )
                """.replace("{column}", column);
    }

    private static final String CHECKPOINT_DATE_SQL = checkpointSql("scheduled_at");

    private static final String CHECKPOINT_SESSION_SQL = checkpointSql("id");
}
