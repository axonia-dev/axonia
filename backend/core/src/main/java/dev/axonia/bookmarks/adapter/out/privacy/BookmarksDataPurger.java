package dev.axonia.bookmarks.adapter.out.privacy;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import dev.axonia.shared.api.personaldata.OrganizationDataPurger;
import lombok.RequiredArgsConstructor;

/**
 * Removes every bookmark row for one organization — hub tenant deletion's bookmarks slice.
 */
@Component
@RequiredArgsConstructor
public class BookmarksDataPurger implements OrganizationDataPurger {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public void purge(String organizationId) {

        var params = Map.of("org", organizationId);
        jdbc.update("DELETE FROM bookmark WHERE organization_id = :org", params);
    }
}
