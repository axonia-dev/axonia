package dev.axonia.expertise.internal;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class VocabularyLockImpl implements VocabularyLock {

    private static final String SHARE_SQL = """
            SELECT pg_advisory_xact_lock_shared(hashtextextended(
                       'expertise_vocabulary:' || :org || ':' || CAST(:communityId AS text), 0))
            """;

    private static final String LOCK_SQL = """
            SELECT pg_advisory_xact_lock(hashtextextended(
                       'expertise_vocabulary:' || :org || ':' || CAST(:communityId AS text), 0))
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public void share(String org, long communityId) {

        jdbc.queryForList(SHARE_SQL, scope(org, communityId));
    }

    @Override
    public void lock(String org, long communityId) {

        jdbc.queryForList(LOCK_SQL, scope(org, communityId));
    }

    private static MapSqlParameterSource scope(String org, long communityId) {

        return new MapSqlParameterSource("org", org).addValue("communityId", communityId);
    }
}
