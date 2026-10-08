package dev.axonia.agent.internal;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import dev.axonia.OrganizationDataPurger;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AgentDataPurger implements OrganizationDataPurger {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public void purge(String organizationId) {
        jdbc.update("DELETE FROM agent_tool_call WHERE organization_id = :org", Map.of("org", organizationId));
    }
}
