package dev.axonia.communication.adapter.out.privacy;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.communication.api.OutboundMessage;
import dev.axonia.shared.api.personaldata.ExportFragment;
import dev.axonia.shared.api.personaldata.ExportQueries;
import dev.axonia.shared.api.personaldata.ExportScope;
import dev.axonia.shared.api.personaldata.PersonalDataExporter;
import lombok.RequiredArgsConstructor;

/**
 * The member's own preferences and queued mail: which message types they turned off, what has been sent or is pending
 * for them, and what was sent to others about them, including {@code communication_message.facts} -
 * {@link CommunicationPersonalDataFootprint} explains why that column is exported rather than withheld.
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

        var query = ExportQueries.byOrganizationAndUser(footprint.table(table), "user_id", scope);
        return jdbc.queryForList(query.sql(), query.params());
    }

    /**
     * The message carries no {@code user_id} of its own, so a member-scoped export reaches it through the deliveries
     * addressed to that member, and through the fact naming the member a message is about: the content of every message
     * they were sent and of every message about them, and nothing else sent only to somebody else.
     */
    private List<Map<String, Object>> messageRows(ExportScope.InOrganization scope) {

        var table = footprint.table("communication_message");
        var sql = new StringBuilder("SELECT DISTINCT ")
                .append(ExportQueries.qualifiedColumnList(table, scope, "user_id", "m"))
                .append(" FROM communication_message m WHERE m.organization_id = :org");
        var params = new MapSqlParameterSource("org", scope.organizationId());
        if (scope instanceof ExportScope.OneMember member) {
            sql.append(" AND (EXISTS (SELECT 1 FROM communication_delivery d"
                    + " WHERE d.message_id = m.id AND d.user_id = :user) OR m.facts->>:aboutFact = :user)");
            params.addValue("user", member.userId()).addValue("aboutFact", OutboundMessage.ABOUT_USER_FACT);
        }
        return jdbc.queryForList(sql.toString(), params);
    }
}
