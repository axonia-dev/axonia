package dev.axonia.learning.adapter.out.privacy;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.shared.api.personaldata.ExportFragment;
import dev.axonia.shared.api.personaldata.ExportQueries;
import dev.axonia.shared.api.personaldata.ExportScope;
import dev.axonia.shared.api.personaldata.PersonalDataExporter;
import lombok.RequiredArgsConstructor;

/**
 * The areas a member named as ones they want to learn are theirs to export, each with the community's name rather than
 * its surrogate id. A community that has since been deleted leaves the intent in the export with a null name. A person
 * scope has nothing here: every intent belongs to an Organization.
 */
@Component
@RequiredArgsConstructor
public class LearningPersonalDataExporter implements PersonalDataExporter {

    private static final String FILE_NAME = "learning.json";

    private final NamedParameterJdbcTemplate jdbc;
    private final LearningPersonalDataFootprint footprint;

    @Override
    @Transactional(readOnly = true)
    public ExportFragment export(ExportScope scope) {

        return switch (scope) {
            case ExportScope.InOrganization organization ->
                new ExportFragment(FILE_NAME, Map.of("learning_intent", intents(organization)), List.of());
            case ExportScope.Person _ -> ExportFragment.nothing(FILE_NAME);
        };
    }

    private List<Map<String, Object>> intents(ExportScope.InOrganization scope) {

        var columns = ExportQueries.qualifiedColumnList(footprint.table("learning_intent"), scope, "user_id", "li");
        var sql = new StringBuilder("SELECT ").append(columns).append(", c.name AS community FROM learning_intent li")
                .append(" LEFT JOIN communities c ON c.id = li.community_id AND c.organization_id = li.organization_id")
                .append(" WHERE li.organization_id = :org");
        var params = new MapSqlParameterSource("org", scope.organizationId());
        if (scope instanceof ExportScope.OneMember member) {
            sql.append(" AND li.user_id = :user");
            params.addValue("user", member.userId());
        }
        return jdbc.queryForList(sql.toString(), params);
    }
}
