package dev.axonia.mentoring.adapter.out.privacy;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import dev.axonia.shared.api.personaldata.OrganizationDataPurger;
import lombok.RequiredArgsConstructor;

/** Removes every mentoring row of one organization, children before the pairings and proposals they hang from. */
@Component
@RequiredArgsConstructor
public class MentoringDataPurger implements OrganizationDataPurger {

    private static final List<String> TABLES = List.of("mentoring_words", "mentoring_pointer", "mentoring_note_topic",
            "mentoring_note", "mentoring_meeting_topic", "mentoring_meeting", "mentoring_topic", "mentoring_pairing",
            "mentoring_proposal", "mentoring_availability");

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public void purge(String organizationId) {

        TABLES.forEach(table -> jdbc.update("DELETE FROM " + table + " WHERE organization_id = :org",
                Map.of("org", organizationId)));
    }
}
