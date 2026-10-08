package dev.axonia.events.internal;

import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import dev.axonia.OrganizationDataPurger;
import lombok.RequiredArgsConstructor;

/**
 * Removes every events row for one organization — hub tenant deletion's events slice. Runs before the community purger
 * ({@code @Order}) because sessions, series and topics hold FKs into {@code communities}. Children without an
 * {@code organization_id} column are reached through their parents.
 */
@Component
@Order(EventsDataPurger.PURGE_ORDER)
@RequiredArgsConstructor
public class EventsDataPurger implements OrganizationDataPurger {

    public static final int PURGE_ORDER = 10;

    private final NamedParameterJdbcTemplate jdbc;
    private final CalendarSubscriptionRepository calendarSubscriptionRepository;

    @Override
    public void purge(String organizationId) {

        var params = Map.of("org", organizationId);
        calendarSubscriptionRepository.deleteByOrganizationId(organizationId);
        jdbc.update("""
                DELETE FROM event_slot_presenter WHERE slot_id IN (
                    SELECT sl.id FROM event_slot sl
                    JOIN event_section sec ON sl.section_id = sec.id
                    JOIN community_session cs ON sec.session_id = cs.id
                    WHERE cs.organization_id = :org)""", params);
        jdbc.update("""
                DELETE FROM event_slot WHERE section_id IN (
                    SELECT sec.id FROM event_section sec
                    JOIN community_session cs ON sec.session_id = cs.id
                    WHERE cs.organization_id = :org)""", params);
        jdbc.update("DELETE FROM event_section WHERE session_id IN "
                + "(SELECT id FROM community_session WHERE organization_id = :org)", params);
        jdbc.update("DELETE FROM event_series_section_type WHERE series_id IN "
                + "(SELECT id FROM event_series WHERE organization_id = :org)", params);
        jdbc.update("DELETE FROM session_attendance WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM action_item_checkpoint WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM action_item WHERE organization_id = :org", params); // action_item_tag cascades
        jdbc.update("DELETE FROM community_session WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM topic_vote WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM topic WHERE organization_id = :org", params); // topic_tag cascades
        jdbc.update("DELETE FROM event_series WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM event_section_type WHERE organization_id = :org", params);
    }
}
