package dev.axonia.installation.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/** The mail integration's rows of {@code installation_settings_change}, newest first. */
@Repository
@RequiredArgsConstructor
class SettingsChangeHistory {

    private static final String MAIL = "mail";

    private static final String NEWEST_FIRST = """
            SELECT id, occurred_at, actor_user_id, changes FROM installation_settings_change
            WHERE integration = :integration ORDER BY occurred_at DESC, id DESC
            """;

    private final NamedParameterJdbcTemplate jdbc;

    private final JsonMapper jsonMapper;

    /** {@code changes} is the row's JSON as stored. */
    record Entry(long id, Instant occurredAt, String actor, String changes) {
    }

    void append(String actor, Instant at, List<MailFieldChange> changes) {

        jdbc.update("""
                INSERT INTO installation_settings_change (occurred_at, actor_user_id, integration, changes)
                VALUES (:at, :actor, :integration, CAST(:changes AS jsonb))
                """, new MapSqlParameterSource("at", at.atOffset(ZoneOffset.UTC)).addValue("actor", actor)
                .addValue("integration", MAIL).addValue("changes", jsonMapper.writeValueAsString(changes)));
    }

    Optional<Entry> latest() {

        return jdbc.query(NEWEST_FIRST + " LIMIT 1", Map.of("integration", MAIL), (rs, rowNum) -> entry(rs)).stream()
                .findFirst();
    }

    Page<Entry> page(Pageable pageable) {

        var total = jdbc.queryForObject(
                "SELECT count(*) FROM installation_settings_change WHERE integration = :integration",
                Map.of("integration", MAIL), Long.class);
        var content = jdbc.query(
                NEWEST_FIRST + " LIMIT :limit OFFSET :offset", new MapSqlParameterSource("integration", MAIL)
                        .addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset()),
                (rs, rowNum) -> entry(rs));
        return new PageImpl<>(content, pageable, Objects.requireNonNull(total));
    }

    private static Entry entry(ResultSet rs) throws SQLException {

        return new Entry(rs.getLong("id"), rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                rs.getString("actor_user_id"), rs.getString("changes"));
    }
}
