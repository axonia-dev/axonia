package dev.axonia.access.adapter.out.privacy;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import dev.axonia.shared.api.personaldata.OrganizationDataPurger;
import lombok.RequiredArgsConstructor;

/**
 * Removes every access row for one organization — hub tenant deletion's access slice. {@code role_binding} is deleted
 * before {@code role} because its FK to {@code role} carries no cascade; {@code role_permission} is not deleted
 * explicitly because its FK to {@code role} cascades.
 */
@Component
@RequiredArgsConstructor
public class AccessDataPurger implements OrganizationDataPurger {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public void purge(String organizationId) {

        var params = Map.of("org", organizationId);
        jdbc.update("DELETE FROM role_binding WHERE organization_id = :org", params);
        jdbc.update("DELETE FROM role WHERE organization_id = :org", params); // role_permission cascades
    }
}
