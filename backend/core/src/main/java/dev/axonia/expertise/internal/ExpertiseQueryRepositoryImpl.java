package dev.axonia.expertise.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.axonia.expertise.ExpertiseSignal;
import dev.axonia.expertise.SignalType;
import dev.axonia.expertise.SourceType;
import lombok.RequiredArgsConstructor;

/**
 * JDBC implementation of {@link ExpertiseQueryRepository}.
 *
 * <p>
 * All queries use the expertise projection tables, which live in {@code V1__baseline.sql}. RLS is always active; the
 * {@code organization_id} column in each table acts as a hard filter even when RLS is relaxed (e.g. Flyway migrations),
 * so we pass it explicitly in every {@code WHERE} clause as a defence-in-depth measure and to satisfy the index
 * {@code idx_expertise_signal_lookup (organization_id, community_id, tag)}.
 *
 * <p>
 * {@link #findPractitioners} inner-joins {@code identity_user} on {@code active = true} to exclude anyone who has left
 * the organisation, whether deprovisioned (row present, inactive) or erased (no row at all), then {@code LEFT JOIN}s
 * the three preference tables with {@code WHERE ... IS NULL} to exclude opted-out users, hidden tags, and dismissed
 * signals in a single pass - no subqueries needed, and the result set is bounded by the tag list size. A hidden tag
 * matches the area a signal's community reads it as; a dismissal matches that area or the stored tag.
 *
 * <p>
 * {@link #findForUser} and {@link #findForUserIn} intentionally skip all preference-table joins so the self-view shows
 * everything that has been recorded, giving users full visibility into their expertise profile.
 *
 * <p>
 * Row-to-domain mapping is done inline via a {@code RowMapper} lambda; enum values are stored as their
 * {@link Enum#name()} strings in the database.
 */
@Repository
@RequiredArgsConstructor
class ExpertiseQueryRepositoryImpl implements ExpertiseQueryRepository {

    /**
     * The joins that decide whether a signal is visible at all: the person is still an active member, has not opted
     * out, has not hidden the area their community reads the tag as, and has not dismissed this particular signal.
     * Shared verbatim by every read that must respect a member's privacy choices, so that adding a fourth such read
     * cannot quietly omit one of them.
     */
    private static final String VISIBILITY_JOINS = """
              JOIN identity_user u
                     ON u.organization_id = s.organization_id
                    AND u.user_id         = s.user_id
                    AND u.active          = true
              LEFT JOIN expertise_opt_out optout
                     ON optout.organization_id = s.organization_id
                    AND optout.user_id         = s.user_id
              LEFT JOIN expertise_tag_alias alias
                     ON alias.organization_id = s.organization_id
                    AND alias.community_id    = s.community_id
                    AND alias.variant_tag     = s.tag
              LEFT JOIN expertise_hidden_tag hidden
                     ON hidden.organization_id = s.organization_id
                    AND hidden.user_id         = s.user_id
                    AND hidden.tag          = COALESCE(alias.canonical_tag, s.tag)
              LEFT JOIN expertise_dismissed_signal dismissed
                     ON dismissed.organization_id = s.organization_id
                    AND dismissed.user_id         = s.user_id
                    AND dismissed.source_type     = s.source_type
                    AND dismissed.source_id       = s.source_id
                    AND dismissed.tag IN (s.tag, alias.canonical_tag)
            """;

    private static final String VISIBILITY_WHERE = """
               AND optout.user_id IS NULL
               AND hidden.tag     IS NULL
               AND dismissed.tag  IS NULL
            """;

    private static final String PRACTITIONERS_SQL = """
            SELECT s.id, s.organization_id, s.community_id, s.user_id, s.tag,
                   s.signal_type, s.source_type, s.source_id, s.occurred_at
              FROM expertise_signal s
            """ + VISIBILITY_JOINS + """
             WHERE s.organization_id = :org
               AND s.community_id    = :community
               AND s.tag IN (:tags)
            """ + VISIBILITY_WHERE + """
             ORDER BY s.user_id, s.id
            """;

    private static final String USER_SIGNALS = """
            SELECT s.id, s.organization_id, s.community_id, s.user_id, s.tag,
                   s.signal_type, s.source_type, s.source_id, s.occurred_at
              FROM expertise_signal s
             WHERE s.organization_id = :org
               AND s.user_id         = :userId
            """;

    private static final String VISIBLE_USER_SIGNALS = """
            SELECT s.id, s.organization_id, s.community_id, s.user_id, s.tag,
                   s.signal_type, s.source_type, s.source_id, s.occurred_at
              FROM expertise_signal s
            """ + VISIBILITY_JOINS + """
             WHERE s.organization_id = :org
               AND s.user_id         = :userId
            """ + VISIBILITY_WHERE;

    private static final String IN_COMMUNITY = """
               AND s.community_id    = :community
            """;

    private static final String BY_ID = """
             ORDER BY s.id
            """;

    private static final String FOR_USER_SQL = USER_SIGNALS + BY_ID;

    private static final String FOR_USER_IN_SQL = USER_SIGNALS + IN_COMMUNITY + BY_ID;

    private static final String VISIBLE_FOR_USER_SQL = VISIBLE_USER_SIGNALS + BY_ID;

    private static final String VISIBLE_FOR_USER_IN_SQL = VISIBLE_USER_SIGNALS + IN_COMMUNITY + BY_ID;

    private static final String ALL_TAGS_SQL = """
            SELECT DISTINCT s.tag
              FROM expertise_signal s
             WHERE s.organization_id = :org
               AND s.community_id    = :community
             ORDER BY s.tag
            """;

    private static final String USER_TAG_AGGREGATES_SQL = """
            SELECT s.user_id,
                   s.tag,
                   COUNT(*) FILTER (WHERE s.signal_type = 'PRESENTED')   AS presented,
                   COUNT(*) FILTER (WHERE s.signal_type = 'CONTRIBUTED') AS contributed,
                   COUNT(*) FILTER (WHERE s.signal_type = 'OFFERED')     AS offered,
                   MAX(s.occurred_at)                                    AS last_occurred_at
              FROM expertise_signal s
            """ + VISIBILITY_JOINS + """
             WHERE s.organization_id = :org
               AND s.community_id    = :community
            """ + VISIBILITY_WHERE + """
             GROUP BY s.user_id, s.tag
             ORDER BY s.user_id, s.tag
            """;

    private static final String VISIBLE_SIGNALS_SQL = """
            SELECT s.id, s.organization_id, s.community_id, s.user_id, s.tag,
                   s.signal_type, s.source_type, s.source_id, s.occurred_at
              FROM expertise_signal s
            """ + VISIBILITY_JOINS + """
             WHERE s.organization_id = :org
               AND s.community_id    = :community
            """ + VISIBILITY_WHERE + """
             ORDER BY s.occurred_at DESC, s.id DESC
             LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public List<UserSignals> findPractitioners(String org, Long community, Set<String> tags) {

        // An empty tag set would render an invalid `IN ()` clause; logically it matches nobody.
        if (tags.isEmpty()) {
            return List.of();
        }

        var params = new MapSqlParameterSource().addValue("org", org).addValue("community", community).addValue("tags",
                tags);

        var rows = jdbc.query(PRACTITIONERS_SQL, params, (rs, n) -> toSignal(rs));
        return groupByUser(rows);
    }

    @Override
    public List<UserSignals> findForUser(String org, String userId) {

        var params = new MapSqlParameterSource().addValue("org", org).addValue("userId", userId);

        var rows = jdbc.query(FOR_USER_SQL, params, (rs, n) -> toSignal(rs));
        return groupByUser(rows);
    }

    @Override
    public List<ExpertiseSignal> findVisibleForUser(String org, String userId) {

        var params = new MapSqlParameterSource().addValue("org", org).addValue("userId", userId);

        return jdbc.query(VISIBLE_FOR_USER_SQL, params, (rs, n) -> toSignal(rs));
    }

    @Override
    public List<ExpertiseSignal> findForUserIn(String org, long community, String userId) {

        return jdbc.query(FOR_USER_IN_SQL, userInCommunity(org, community, userId), (rs, n) -> toSignal(rs));
    }

    @Override
    public List<ExpertiseSignal> findVisibleForUserIn(String org, long community, String userId) {

        return jdbc.query(VISIBLE_FOR_USER_IN_SQL, userInCommunity(org, community, userId), (rs, n) -> toSignal(rs));
    }

    @Override
    public List<String> allTags(String org, Long community) {

        var params = new MapSqlParameterSource().addValue("org", org).addValue("community", community);

        return jdbc.queryForList(ALL_TAGS_SQL, params, String.class);
    }

    @Override
    public List<UserTagAggregate> userTagAggregates(String org, Long community) {

        var params = new MapSqlParameterSource().addValue("org", org).addValue("community", community);

        return jdbc.query(USER_TAG_AGGREGATES_SQL, params,
                (rs, n) -> new UserTagAggregate(rs.getString("user_id"), rs.getString("tag"), rs.getInt("presented"),
                        rs.getInt("contributed"), rs.getInt("offered"),
                        rs.getTimestamp("last_occurred_at").toInstant()));
    }

    @Override
    public List<ExpertiseSignal> visibleSignals(String org, Long community, int limit) {

        if (limit <= 0) {
            return List.of();
        }

        var params = new MapSqlParameterSource().addValue("org", org).addValue("community", community).addValue("limit",
                limit);

        return jdbc.query(VISIBLE_SIGNALS_SQL, params, (rs, n) -> toSignal(rs));
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private static MapSqlParameterSource userInCommunity(String org, long community, String userId) {

        return new MapSqlParameterSource().addValue("org", org).addValue("community", community).addValue("userId",
                userId);
    }

    private ExpertiseSignal toSignal(java.sql.ResultSet rs) throws java.sql.SQLException {

        var ts = rs.getTimestamp("occurred_at");
        return ExpertiseSignal.builder().id(rs.getLong("id")).organizationId(rs.getString("organization_id"))
                .communityId(rs.getLong("community_id")).userId(rs.getString("user_id")).tag(rs.getString("tag"))
                .signalType(SignalType.valueOf(rs.getString("signal_type")))
                .sourceType(SourceType.valueOf(rs.getString("source_type"))).sourceId(rs.getLong("source_id"))
                .occurredAt(ts == null ? null : ts.toInstant()).build();
    }

    private List<UserSignals> groupByUser(List<ExpertiseSignal> rows) {

        // LinkedHashMap preserves the ORDER BY user_id / id ordering from the query
        var byUser = new LinkedHashMap<String, List<ExpertiseSignal>>();
        for (var signal : rows) {
            byUser.computeIfAbsent(signal.userId(), k -> new ArrayList<>()).add(signal);
        }
        var result = new ArrayList<UserSignals>(byUser.size());
        for (var entry : byUser.entrySet()) {
            result.add(new UserSignals(entry.getKey(), List.copyOf(entry.getValue())));
        }
        return List.copyOf(result);
    }
}
