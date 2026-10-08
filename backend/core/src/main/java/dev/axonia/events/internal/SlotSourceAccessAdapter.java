package dev.axonia.events.internal;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.recognition.ContributionSource;
import dev.axonia.recognition.SlotSource;
import dev.axonia.recognition.SlotSourceAccess;
import lombok.RequiredArgsConstructor;

/**
 * Resolves a presented slot's session and section type for the recognition impact profile, in a single batched read.
 * Slot ids are globally unique, so no organization scoping is applied; callers pass ids they are already authorized to
 * see. Also the authoritative attribution source for loves: unlike the batched badge lookup,
 * {@link #slotContribution(String, Long)} is org-scoped per call, since a single slot's attribution is resolved on
 * demand rather than in bulk.
 */
@Component
@RequiredArgsConstructor
class SlotSourceAccessAdapter implements SlotSourceAccess {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, SlotSource> slotSources(Collection<Long> slotIds) {

        if (slotIds.isEmpty()) {
            return Map.of();
        }
        var params = new MapSqlParameterSource("ids", slotIds);
        var sources = new HashMap<Long, SlotSource>();
        jdbc.query("""
                SELECT slot.id AS slot_id, sec.session_id AS session_id, st.id AS section_type_id,
                       st.label AS section_type_label, st.icon AS section_type_icon,
                       st.accent_color AS section_type_accent_color
                FROM event_slot slot
                JOIN event_section sec ON sec.id = slot.section_id
                JOIN event_section_type st ON st.id = sec.section_type_id
                WHERE slot.id IN (:ids)
                """, params, rs -> {
            sources.put(rs.getLong("slot_id"),
                    new SlotSource(rs.getLong("session_id"), rs.getLong("section_type_id"),
                            rs.getString("section_type_label"), rs.getString("section_type_icon"),
                            rs.getString("section_type_accent_color")));
        });
        return sources;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ContributionSource> slotContribution(String organizationId, Long slotId) {

        var params = new MapSqlParameterSource("id", slotId).addValue("org", organizationId);
        var rows = jdbc.query("""
                SELECT s.community_id AS community_id, COALESCE(slot.title, s.title) AS title,
                       s.scheduled_at AS occurred_at
                FROM event_slot slot
                JOIN event_section sec ON sec.id = slot.section_id
                JOIN community_session s ON s.id = sec.session_id
                WHERE slot.id = :id AND s.organization_id = :org
                """, params,
                (rs, rowNum) -> ContributionSource.builder().communityId(rs.getLong("community_id"))
                        .title(rs.getString("title")).occurredAt(rs.getTimestamp("occurred_at").toInstant())
                        .contributorUserIds(presenters(slotId)).build());
        return rows.stream().findFirst();
    }

    private List<String> presenters(Long slotId) {

        return jdbc.queryForList("SELECT user_id FROM event_slot_presenter WHERE slot_id = :id",
                new MapSqlParameterSource("id", slotId), String.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> slotsPresentedBy(String organizationId, String userId) {

        var params = new MapSqlParameterSource("userId", userId).addValue("org", organizationId);
        return jdbc.queryForList("""
                SELECT p.slot_id FROM event_slot_presenter p
                JOIN event_slot slot ON slot.id = p.slot_id
                JOIN event_section sec ON sec.id = slot.section_id
                JOIN community_session s ON s.id = sec.session_id
                WHERE p.user_id = :userId AND s.organization_id = :org
                """, params, Long.class);
    }
}
