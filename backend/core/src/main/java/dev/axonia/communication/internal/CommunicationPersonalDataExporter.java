package dev.axonia.communication.internal;

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
 * The member's own preferences and queued mail: which message types they turned off, and what has been sent or is
 * pending for them, including {@code communication_message.facts} - {@link CommunicationPersonalDataFootprint} explains
 * why that column is exported rather than withheld.
 */
@Component
@RequiredArgsConstructor
public class CommunicationPersonalDataExporter implements PersonalDataExporter {

    private final NamedParameterJdbcTemplate jdbc;
    private final CommunicationPersonalDataFootprint footprint;

    @Override
    @Transactional(readOnly = true)
    public ExportFragment export(ExportScope scope) {

        return switch (scope) {
            case ExportScope.InOrganization organization -> organizationFragment(organization);
            case ExportScope.Person _ -> ExportFragment.nothing("communication.json");
        };
    }

    private ExportFragment organizationFragment(ExportScope.InOrganization scope) {

        Map<String, Object> payload = Map.of("communication_preference", rowsOf("communication_preference", scope),
                "communication_delivery", rowsOf("communication_delivery", scope), "communication_message",
                messageRows(scope));
        return new ExportFragment("communication.json", payload, List.of());
    }

    private List<Map<String, Object>> rowsOf(String table, ExportScope.InOrganization scope) {

        return ExportQueries.rowsByOrganizationAndUser(jdbc, footprint.table(table), "user_id", scope);
    }

    /**
     * The message carries no {@code user_id} of its own, so a member-scoped export reaches it through the deliveries
     * addressed to that member: the content of every message they were sent, and nothing sent only to somebody else.
     */
    private List<Map<String, Object>> messageRows(ExportScope.InOrganization scope) {

        var table = footprint.table("communication_message");
        var sql = new StringBuilder("SELECT DISTINCT ")
                .append(ExportQueries.qualifiedColumnList(table, scope, "user_id", "m"))
                .append(" FROM communication_message m WHERE m.organization_id = :org");
        var params = new MapSqlParameterSource("org", scope.organizationId());
        if (scope instanceof ExportScope.OneMember member) {
            sql.append(" AND EXISTS (SELECT 1 FROM communication_delivery d"
                    + " WHERE d.message_id = m.id AND d.user_id = :user)");
            params.addValue("user", member.userId());
        }
        return jdbc.queryForList(sql.toString(), params);
    }
}
