package dev.axonia.expertise.internal;

import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * JDBC implementation of {@link PreferenceRepository}.
 *
 * <p>
 * All five preference tables ({@code expertise_opt_out}, {@code expertise_hidden_tag},
 * {@code expertise_dismissed_signal}) are covered. Each operation passes {@code organization_id} explicitly for
 * defence-in-depth even though RLS already enforces it.
 */
@Repository
@RequiredArgsConstructor
class PreferenceRepositoryImpl implements PreferenceRepository {

    private final NamedParameterJdbcTemplate jdbc;

    // ------------------------------------------------------------------
    // Opt-out
    // ------------------------------------------------------------------

    @Override
    public boolean isOptedOut(String org, String userId) {

        var sql = """
                SELECT COUNT(1)
                  FROM expertise_opt_out
                 WHERE organization_id = :org
                   AND user_id         = :userId
                """;
        var count = jdbc.queryForObject(sql, params(org, userId), Integer.class);
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public void setOptOut(String org, String userId, boolean optedOut) {

        if (optedOut) {
            var sql = """
                    INSERT INTO expertise_opt_out (organization_id, user_id)
                    VALUES (:org, :userId)
                    ON CONFLICT DO NOTHING
                    """;
            jdbc.update(sql, params(org, userId));
        } else {
            var sql = """
                    DELETE FROM expertise_opt_out
                     WHERE organization_id = :org
                       AND user_id         = :userId
                    """;
            jdbc.update(sql, params(org, userId));
        }
    }

    // ------------------------------------------------------------------
    // Hidden tags
    // ------------------------------------------------------------------

    @Override
    public Set<String> hiddenTags(String org, String userId) {

        var sql = """
                SELECT tag
                  FROM expertise_hidden_tag
                 WHERE organization_id = :org
                   AND user_id         = :userId
                """;
        var list = jdbc.queryForList(sql, params(org, userId), String.class);
        return Set.copyOf(list);
    }

    @Override
    @Transactional
    public void hideTag(String org, String userId, String canonicalTag) {

        var sql = """
                INSERT INTO expertise_hidden_tag (organization_id, user_id, tag)
                VALUES (:org, :userId, :tag)
                ON CONFLICT DO NOTHING
                """;
        jdbc.update(sql, params(org, userId).addValue("tag", canonicalTag));
    }

    @Override
    @Transactional
    public void unhideTag(String org, String userId, String canonicalTag) {

        var sql = """
                DELETE FROM expertise_hidden_tag
                 WHERE organization_id = :org
                   AND user_id         = :userId
                   AND tag             = :tag
                """;
        jdbc.update(sql, params(org, userId).addValue("tag", canonicalTag));
    }

    // ------------------------------------------------------------------
    // Dismissed signals
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public void dismissSignal(String org, String userId, PreferenceRepository.SignalRef ref, String canonicalTag) {

        var sql = """
                INSERT INTO expertise_dismissed_signal
                    (organization_id, user_id, source_type, source_id, tag)
                VALUES (:org, :userId, :sourceType, :sourceId, :tag)
                ON CONFLICT DO NOTHING
                """;
        jdbc.update(sql, params(org, userId).addValue("sourceType", ref.sourceType().name())
                .addValue("sourceId", ref.sourceId()).addValue("tag", canonicalTag));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private MapSqlParameterSource params(String org, String userId) {

        return new MapSqlParameterSource().addValue("org", org).addValue("userId", userId);
    }
}
