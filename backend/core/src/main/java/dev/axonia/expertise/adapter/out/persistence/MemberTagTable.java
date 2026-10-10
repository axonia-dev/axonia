package dev.axonia.expertise.adapter.out.persistence;

import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import dev.axonia.expertise.api.MemberTag;
import dev.axonia.expertise.api.MemberTagStore;

/**
 * The SQL every table of {@link MemberTag}s shares. Each is keyed by (organization, community, member, tag), holds the
 * tag as typed and is read through the community's merges, so adding, removing, leaving and a curator's rename or
 * delete work the same way on every one of them. The table name is the owning repository's constant, never input, and
 * is refused unless it is a plain lower-case identifier, since it is spliced into the SQL. Every method passes
 * {@code org} explicitly as defence in depth, even though RLS already scopes each table.
 */
public final class MemberTagTable<T extends MemberTag> implements MemberTagStore<T> {

    private static final Pattern TABLE_NAME = Pattern.compile("[a-z_]+");

    private static final String LOCK_MEMBER_SQL = """
            SELECT pg_advisory_xact_lock(hashtextextended(
                       '{table}:' || :org || ':' || CAST(:communityId AS text) || ':' || :userId, 0))
            """;

    private static final String IN_COMMUNITY_SQL = """
            SELECT t.community_id, t.user_id, t.tag, t.declared_at
              FROM {table} t
             WHERE t.organization_id = :org AND t.community_id = :communityId AND t.user_id = :userId
             ORDER BY t.declared_at, t.tag
            """;

    private static final String ACTIVE_IN_SQL = """
            SELECT t.community_id, t.user_id, t.tag, t.declared_at
              FROM {table} t
              JOIN identity_user u
                     ON u.organization_id = t.organization_id
                    AND u.user_id         = t.user_id
                    AND u.active          = true
              JOIN memberships m
                     ON m.organization_id = t.organization_id
                    AND m.community_id    = t.community_id
                    AND m.user_id         = t.user_id
             WHERE t.organization_id = :org AND t.community_id = :communityId
             ORDER BY t.declared_at, t.user_id, t.tag
            """;

    private static final String INSERT_SQL = """
            INSERT INTO {table} (organization_id, community_id, user_id, tag, declared_at)
            VALUES (:org, :communityId, :userId, :tag, :declaredAt)
            ON CONFLICT DO NOTHING
            """;

    private static final String DELETE_SQL = """
            DELETE FROM {table}
             WHERE organization_id = :org AND community_id = :communityId AND user_id = :userId AND tag IN (:tags)
            """;

    private static final String DELETE_ALL_IN_SQL = """
            DELETE FROM {table}
             WHERE organization_id = :org AND community_id = :communityId AND user_id = :userId
            """;

    private static final String TAGS_IN_SQL = """
            SELECT DISTINCT t.tag
              FROM {table} t
             WHERE t.organization_id = :org AND t.community_id = :communityId
             ORDER BY t.tag
            """;

    private static final String KEEP_EARLIER_DATE_ON_FOLD_SQL = """
            UPDATE {table} kept
               SET declared_at = moved.declared_at
              FROM {table} moved
             WHERE kept.organization_id  = :org
               AND kept.community_id     = :communityId
               AND kept.tag              = :to
               AND moved.organization_id = kept.organization_id
               AND moved.community_id    = kept.community_id
               AND moved.user_id         = kept.user_id
               AND moved.tag             = :from
               AND moved.declared_at     < kept.declared_at
            """;

    private static final String DELETE_COLLIDING_SQL = """
            DELETE FROM {table} moved
             WHERE moved.organization_id = :org
               AND moved.community_id    = :communityId
               AND moved.tag             = :from
               AND EXISTS (SELECT 1 FROM {table} kept
                            WHERE kept.organization_id = moved.organization_id
                              AND kept.community_id    = moved.community_id
                              AND kept.user_id         = moved.user_id
                              AND kept.tag             = :to)
            """;

    private static final String RENAME_SQL = """
            UPDATE {table} SET tag = :to
             WHERE organization_id = :org AND community_id = :communityId AND tag = :from
            """;

    private static final String REMOVE_TAG_SQL = """
            DELETE FROM {table}
             WHERE organization_id = :org AND community_id = :communityId AND tag = :tag
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final RowMapper<T> rows;
    private final String lockMemberSql;
    private final String inCommunitySql;
    private final String activeInSql;
    private final String insertSql;
    private final String deleteSql;
    private final String deleteAllInSql;
    private final String tagsInSql;
    private final String keepEarlierDateOnFoldSql;
    private final String deleteCollidingSql;
    private final String renameSql;
    private final String removeTagSql;

    public MemberTagTable(NamedParameterJdbcTemplate jdbc, String table, MemberTagStore.Factory<T> factory) {

        if (!TABLE_NAME.matcher(table).matches()) {
            throw new IllegalArgumentException("Not a plain table name: " + table);
        }
        this.jdbc = jdbc;
        this.rows = (rs, n) -> factory.of(rs.getLong("community_id"), rs.getString("user_id"), rs.getString("tag"),
                rs.getTimestamp("declared_at").toInstant());
        this.lockMemberSql = resolve(LOCK_MEMBER_SQL, table);
        this.inCommunitySql = resolve(IN_COMMUNITY_SQL, table);
        this.activeInSql = resolve(ACTIVE_IN_SQL, table);
        this.insertSql = resolve(INSERT_SQL, table);
        this.deleteSql = resolve(DELETE_SQL, table);
        this.deleteAllInSql = resolve(DELETE_ALL_IN_SQL, table);
        this.tagsInSql = resolve(TAGS_IN_SQL, table);
        this.keepEarlierDateOnFoldSql = resolve(KEEP_EARLIER_DATE_ON_FOLD_SQL, table);
        this.deleteCollidingSql = resolve(DELETE_COLLIDING_SQL, table);
        this.renameSql = resolve(RENAME_SQL, table);
        this.removeTagSql = resolve(REMOVE_TAG_SQL, table);
    }

    /** The parameters naming one community in one organization, for the owner's own statements too. */
    public static MapSqlParameterSource scope(String org, long communityId) {

        return new MapSqlParameterSource("org", org).addValue("communityId", communityId);
    }

    /** Runs one of the owner's own reads, mapping each row read by its column names. */
    public List<T> query(String sql, SqlParameterSource params) {

        return jdbc.query(sql, params, rows);
    }

    @Override
    public void lockMember(String org, long communityId, String userId) {

        jdbc.queryForList(lockMemberSql, member(org, communityId, userId));
    }

    @Override
    public List<T> inCommunity(String org, long communityId, String userId) {

        return query(inCommunitySql, member(org, communityId, userId));
    }

    @Override
    public List<T> activeIn(String org, long communityId) {

        return query(activeInSql, scope(org, communityId));
    }

    @Override
    public void insert(String org, T row) {

        jdbc.update(insertSql, member(org, row.communityId(), row.userId()).addValue("tag", row.tag())
                .addValue("declaredAt", Timestamp.from(row.declaredAt())));
    }

    @Override
    public void delete(String org, long communityId, String userId, Set<String> tags) {

        jdbc.update(deleteSql, member(org, communityId, userId).addValue("tags", tags));
    }

    @Override
    public void deleteAllIn(String org, long communityId, String userId) {

        jdbc.update(deleteAllInSql, member(org, communityId, userId));
    }

    @Override
    public List<String> tagsIn(String org, long communityId) {

        return jdbc.queryForList(tagsInSql, scope(org, communityId), String.class);
    }

    @Override
    public void renameTag(String org, long communityId, String from, String to) {

        var params = scope(org, communityId).addValue("from", from).addValue("to", to);
        jdbc.update(keepEarlierDateOnFoldSql, params);
        jdbc.update(deleteCollidingSql, params);
        jdbc.update(renameSql, params);
    }

    @Override
    public void removeTag(String org, long communityId, String tag) {

        jdbc.update(removeTagSql, scope(org, communityId).addValue("tag", tag));
    }

    private static String resolve(String template, String table) {

        return template.replace("{table}", table);
    }

    private static MapSqlParameterSource member(String org, long communityId, String userId) {

        return scope(org, communityId).addValue("userId", userId);
    }
}
