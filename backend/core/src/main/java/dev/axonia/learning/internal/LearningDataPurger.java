package dev.axonia.learning.internal;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import dev.axonia.OrganizationDataPurger;
import lombok.RequiredArgsConstructor;

/** Removes every learning intent of one organization: hub tenant deletion's learning slice. */
@Component
@RequiredArgsConstructor
public class LearningDataPurger implements OrganizationDataPurger {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public void purge(String organizationId) {

        jdbc.update("DELETE FROM learning_intent WHERE organization_id = :org", Map.of("org", organizationId));
    }
}
