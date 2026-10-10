package dev.axonia.roundup.adapter.out.privacy;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import dev.axonia.shared.api.personaldata.OrganizationDataPurger;
import lombok.RequiredArgsConstructor;

/**
 * Removes a closed tenant's roundup setting. {@code roundup_settings} carries no personal data (see
 * {@link RoundupPersonalDataFootprint}), but it is still organization-scoped bookkeeping that must not survive a closed
 * tenant, the same reason {@code CommunicationDataPurger} deletes {@code communication_production_watermark} and
 * {@code AccessDataPurger} deletes {@code role}.
 */
@Component
@RequiredArgsConstructor
public class RoundupDataPurger implements OrganizationDataPurger {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public void purge(String organizationId) {

        jdbc.update("DELETE FROM roundup_settings WHERE organization_id = :org", Map.of("org", organizationId));
    }
}
