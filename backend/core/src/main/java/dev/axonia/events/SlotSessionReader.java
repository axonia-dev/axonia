package dev.axonia.events;

import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Resolves which session a slot belongs to, for other modules that need to validate a slot reference against a session.
 * Slot ids are globally unique, so no organization scoping is applied here; callers pass ids they are already
 * authorized to see.
 */
@Service
@RequiredArgsConstructor
public class SlotSessionReader {

    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Optional<Long> sessionIdOf(Long slotId) {

        var ids = jdbc.queryForList("""
                SELECT sec.session_id
                FROM event_slot slot
                JOIN event_section sec ON sec.id = slot.section_id
                WHERE slot.id = :id
                """, new MapSqlParameterSource("id", slotId), Long.class);
        return ids.stream().findFirst();
    }
}
