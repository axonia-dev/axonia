package dev.axonia.selfhosted.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * The installation's one row of {@code selfhosted_settings}: the Organization it founded, the subject claim it matches
 * accounts on, and when. Written once, at the first start; the runtime role holds no right to change or remove it.
 */
@Repository
@RequiredArgsConstructor
class SelfHostedSettings {

    private static final String SERIALIZE_SQL = """
            SELECT pg_advisory_xact_lock(hashtextextended('axonia.selfhosted.founding', 0))
            """;

    private static final String FIND_SQL = """
            SELECT organization_id, subject_claim, founded_at FROM selfhosted_settings
            """;

    private static final String RECORD_SQL = """
            INSERT INTO selfhosted_settings (organization_id, subject_claim, founded_at)
            VALUES (:organizationId, :subjectClaim, :foundedAt)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /** Holds the founding lock until the current transaction ends, so replicas starting together found once. */
    void serializeFounding() {

        jdbc.queryForList(SERIALIZE_SQL, Map.of());
    }

    Optional<Founded> find() {

        return jdbc.query(FIND_SQL, (row, number) -> new Founded(row.getString(1), row.getString(2),
                row.getObject(3, OffsetDateTime.class).toInstant())).stream().findFirst();
    }

    void record(Founded founded) {

        jdbc.update(RECORD_SQL,
                new MapSqlParameterSource().addValue("organizationId", founded.organizationId())
                        .addValue("subjectClaim", founded.subjectClaim())
                        .addValue("foundedAt", Timestamp.from(founded.foundedAt())));
    }

    record Founded(String organizationId, String subjectClaim, Instant foundedAt) {
    }
}
