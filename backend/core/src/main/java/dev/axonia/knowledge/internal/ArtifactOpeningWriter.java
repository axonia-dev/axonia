package dev.axonia.knowledge.internal;

import java.sql.Date;
import java.sql.Timestamp;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * The only writer of {@code artifact_opening}. There is no entity and no repository behind it: the row is write-only
 * from the application's side, read back solely by the aggregate in {@link OpeningSignalsAdapter} and by the personal
 * data export, both raw SQL.
 *
 * <p>
 * {@code ON CONFLICT DO NOTHING} against the day-grain unique key is what makes the dedup rule an invariant of the
 * database rather than a convention callers have to remember. Both instants arrive from the service layer's Clock as
 * plain named parameters; nothing here reads the database's clock.
 */
@Component
@RequiredArgsConstructor
public class ArtifactOpeningWriter {

    private final NamedParameterJdbcTemplate jdbc;

    public void record(RecordOpeningCommand command) {

        var sql = """
                INSERT INTO artifact_opening (organization_id, community_id, artifact_id, member_user_id,
                                              opened_on, created_at)
                VALUES (:organizationId, :communityId, :artifactId, :memberUserId, :openedOn, :createdAt)
                ON CONFLICT (organization_id, artifact_id, member_user_id, opened_on) DO NOTHING
                """;
        var params = new MapSqlParameterSource().addValue("organizationId", command.organizationId())
                .addValue("communityId", command.communityId()).addValue("artifactId", command.artifactId())
                .addValue("memberUserId", command.memberUserId()).addValue("openedOn", Date.valueOf(command.openedOn()))
                .addValue("createdAt", Timestamp.from(command.createdAt()));
        jdbc.update(sql, params);
    }
}
