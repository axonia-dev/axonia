package dev.axonia.agent.internal;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.ExportFragment;
import dev.axonia.ExportQueries;
import dev.axonia.ExportScope;
import dev.axonia.PersonalDataExporter;
import lombok.RequiredArgsConstructor;

/**
 * A tool call is logged against the token holder who made it, so it is theirs to export. {@code token_id} means nothing
 * to the person reading their own export, so the query resolves it to the token's own name; the join carries its own
 * {@code organization_id} predicate for the same reason the recognition module's placement join does - row-level
 * security makes it redundant in production, but tests connect as the table owner, which bypasses it entirely.
 */
@Component
@RequiredArgsConstructor
public class AgentPersonalDataExporter implements PersonalDataExporter {

    private final NamedParameterJdbcTemplate jdbc;
    private final AgentPersonalDataFootprint footprint;

    @Override
    @Transactional(readOnly = true)
    public ExportFragment export(ExportScope scope) {

        return switch (scope) {
            case ExportScope.InOrganization organization -> organizationFragment(organization);
            case ExportScope.Person _ -> ExportFragment.nothing("agent.json");
        };
    }

    private ExportFragment organizationFragment(ExportScope.InOrganization scope) {

        var rows = calls(scope);
        return new ExportFragment("agent.json", Map.of("agent_tool_call", rows), List.of());
    }

    private List<Map<String, Object>> calls(ExportScope.InOrganization scope) {

        var columns = ExportQueries.qualifiedColumnList(footprint.table("agent_tool_call"), scope, "user_id", "atc");
        var sql = new StringBuilder("SELECT ").append(columns)
                .append(", pat.name AS token, ac.client_name AS connection FROM agent_tool_call atc ")
                .append("LEFT JOIN personal_access_token pat ON pat.id = atc.token_id ")
                .append("AND pat.organization_id = atc.organization_id ")
                .append("LEFT JOIN agent_connection ac ON ac.id = atc.connection_id ")
                .append("AND ac.organization_id = atc.organization_id WHERE atc.organization_id = :org");
        var params = new MapSqlParameterSource("org", scope.organizationId());
        if (scope instanceof ExportScope.OneMember member) {
            sql.append(" AND atc.user_id = :user");
            params.addValue("user", member.userId());
        }
        return jdbc.queryForList(sql.toString(), params);
    }
}
