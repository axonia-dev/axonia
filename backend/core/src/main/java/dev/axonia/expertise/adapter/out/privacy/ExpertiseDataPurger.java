package dev.axonia.expertise.adapter.out.privacy;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import dev.axonia.shared.api.personaldata.OrganizationDataPurger;
import lombok.RequiredArgsConstructor;

/**
 * Removes every expertise row for one organization - hub tenant deletion's expertise slice. Every table carries its own
 * {@code organization_id}. The one intra-module foreign key, a named area's membership rows onto the named area,
 * cascades on delete, so the membership rows go first only to keep each statement's reach explicit.
 */
@Component
@RequiredArgsConstructor
public class ExpertiseDataPurger implements OrganizationDataPurger {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public void purge(String organizationId) {

        var params = Map.of("org", organizationId);
        jdbc.update("DELETE FROM expertise_signal WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM expertise_tag_alias WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM expertise_opt_out WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM expertise_hidden_tag WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM expertise_dismissed_signal WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM expertise_declaration WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM expertise_tag_dismissal WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM expertise_named_area_tag WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM expertise_named_area WHERE organization_id = :org", params);
    }
}
