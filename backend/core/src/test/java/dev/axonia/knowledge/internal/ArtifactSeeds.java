package dev.axonia.knowledge.internal;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import lombok.experimental.UtilityClass;

/**
 * A minimal, ACTIVE {@code artifact} row for tests that only need one to exist for a foreign key to point at -
 * {@link ArtifactOpeningWriterTest} and {@link OpeningSignalsAdapterTest} otherwise would each carry their own copy of
 * the same insert.
 */
@UtilityClass
class ArtifactSeeds {

    long artifact(NamedParameterJdbcTemplate jdbc, long communityId, String contributorUserId, Instant createdAt) {

        return artifact(jdbc, communityId, contributorUserId, createdAt, "ACTIVE");
    }

    long artifact(NamedParameterJdbcTemplate jdbc, long communityId, String contributorUserId, Instant createdAt,
            String status) {

        return jdbc.queryForObject("""
                INSERT INTO artifact (organization_id, community_id, type_id, title, description, url,
                        contributor_user_id, status, search_document, created_at, updated_at, version)
                VALUES ('default', :communityId, 1, 'T', 'd', 'https://example.com', :contributorUserId,
                        :status, 'doc', :createdAt, now(), 0)
                RETURNING id
                """,
                new MapSqlParameterSource().addValue("communityId", communityId)
                        .addValue("contributorUserId", contributorUserId)
                        .addValue("createdAt", Timestamp.from(createdAt)).addValue("status", status),
                Long.class);
    }
}
