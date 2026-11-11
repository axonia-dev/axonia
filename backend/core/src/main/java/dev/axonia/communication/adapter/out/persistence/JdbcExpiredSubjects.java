package dev.axonia.communication.adapter.out.persistence;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.communication.api.ExpiredSubjects;
import dev.axonia.communication.api.MessageType;
import lombok.RequiredArgsConstructor;

/**
 * Compares the subject's tail as text, never as a pattern, so a suffix carrying {@code _} or {@code %} matches itself.
 */
@Repository
@RequiredArgsConstructor
public class JdbcExpiredSubjects implements ExpiredSubjects {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional
    public int forgetEndingWith(String organizationId, MessageType type, String suffix) {

        return jdbc.update("""
                DELETE FROM communication_expired_message
                 WHERE organization_id = :org
                   AND message_type    = :type
                   AND right(subject_ref, length(:suffix)) = :suffix
                """, new MapSqlParameterSource().addValue("org", organizationId).addValue("type", type.code())
                .addValue("suffix", suffix));
    }
}
