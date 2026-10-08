package dev.axonia.communication.internal;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import dev.axonia.OrganizationDataPurger;
import lombok.RequiredArgsConstructor;

/**
 * Removes every preference, every queued message and every producer watermark of one organization, on tenant close.
 * {@code communication_production_watermark} carries no personal data (see {@code CommunicationPersonalDataFootprint}),
 * but it is still organization-scoped bookkeeping that must not survive a closed tenant, the same reason
 * {@code AccessDataPurger} deletes {@code role} on close despite that table holding no personal data either.
 */
@Component
@RequiredArgsConstructor
public class CommunicationDataPurger implements OrganizationDataPurger {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public void purge(String organizationId) {

        var params = Map.of("org", organizationId);
        jdbc.update("DELETE FROM communication_delivery WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM communication_message WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM communication_expired_message WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM communication_preference WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM communication_production_watermark WHERE organization_id = :org", params);
    }
}
