package dev.axonia.knowledge.internal;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import dev.axonia.OrganizationDataPurger;
import lombok.RequiredArgsConstructor;

/**
 * Removes every knowledge row for one organization - hub tenant deletion's knowledge slice. Attachment and tag children
 * cascade from {@code artifact}.
 */
@Component
@RequiredArgsConstructor
public class KnowledgeDataPurger implements OrganizationDataPurger {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public void purge(String organizationId) {

        var params = Map.of("org", organizationId);
        jdbc.update("DELETE FROM artifact_opening WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM artifact WHERE organization_id = :org", params); // attachments/tags cascade
        jdbc.update("DELETE FROM artifact_type WHERE organization_id = :org", params);
    }
}
