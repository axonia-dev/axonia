package dev.axonia.roundup.adapter.out.privacy;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import dev.axonia.shared.api.personaldata.OrganizationDataPurger;
import lombok.RequiredArgsConstructor;

/**
 * Removes a closed tenant's roundup setting, which carries no personal data (see {@link RoundupPersonalDataFootprint}).
 */
@Component
@RequiredArgsConstructor
public class RoundupDataPurger implements OrganizationDataPurger {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public void purge(String organizationId) {

        var params = Map.of("org", organizationId);
        jdbc.update("DELETE FROM roundup_settings WHERE organization_id = :org", params);
    }
}
