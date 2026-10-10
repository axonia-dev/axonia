package dev.axonia.events.adapter.out.persistence;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.api.SlotAttendance;
import lombok.RequiredArgsConstructor;

/**
 * How many people attended the session each presented slot belongs to, for the modules that need a contribution's reach
 * (the expertise map). One batched read; a slot that no longer resolves is absent from the result, and a slot nobody
 * attended is present with a count of zero. That distinction is the point: absent means unknown, zero means measured
 * and empty.
 *
 * <p>
 * Like {@link ActivityTitleReader#slotTitles}, this cannot filter on an organization column, because {@code
 * event_slot} carries none; it relies on callers passing ids they are already authorized to see.
 */
@Service
@RequiredArgsConstructor
public class SlotAttendanceReader implements SlotAttendance {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Integer> attendanceBySlot(Collection<Long> slotIds) {

        if (slotIds.isEmpty()) {
            return Map.of();
        }

        var counts = new HashMap<Long, Integer>();
        jdbc.query("""
                SELECT slot.id AS slot_id, COUNT(att.id) AS attendees
                  FROM event_slot slot
                  JOIN event_section sec ON sec.id = slot.section_id
                  LEFT JOIN session_attendance att ON att.session_id = sec.session_id
                 WHERE slot.id IN (:ids)
                 GROUP BY slot.id
                """, new MapSqlParameterSource("ids", slotIds), rs -> {
            counts.put(rs.getLong("slot_id"), rs.getInt("attendees"));
        });
        return counts;
    }
}
