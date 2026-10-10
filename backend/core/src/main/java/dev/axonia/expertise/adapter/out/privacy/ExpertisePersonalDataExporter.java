package dev.axonia.expertise.adapter.out.privacy;

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
 * Every signal, curation and declaration table keys on {@code user_id} and exists only to describe that one member's
 * expertise graph, so all five are theirs to export. The vocabulary tables carry no personal column and are not part of
 * this fragment.
 *
 * <p>
 * {@code expertise_signal} and {@code expertise_dismissed_signal} both point at one of four tables through a
 * {@code source_type} discriminator rather than a foreign key, so neither an inner join nor a single {@code LEFT JOIN}
 * can resolve the human-readable {@code source} title - the branch has to be picked in SQL, and a missed
 * {@code SourceType} value would fail silently as a null label rather than as a build error. {@code event_slot} carries
 * no {@code organization_id} of its own, so its branch joins on {@code sl.id} alone; the other three constrain
 * {@code organization_id} as well, matching every other label join in this module.
 */
@Component
@RequiredArgsConstructor
public class ExpertisePersonalDataExporter implements PersonalDataExporter {

    private static final String SOURCE_LABEL = "COALESCE(sl.title, art.title, top.title, ai.title) AS source";

    private static final String SOURCE_JOINS = " LEFT JOIN event_slot sl ON %1$s.source_type = 'SLOT'"
            + " AND sl.id = %1$s.source_id"
            + " LEFT JOIN artifact art ON %1$s.source_type = 'ARTIFACT' AND art.id = %1$s.source_id"
            + " AND art.organization_id = %1$s.organization_id"
            + " LEFT JOIN topic top ON %1$s.source_type = 'TOPIC' AND top.id = %1$s.source_id"
            + " AND top.organization_id = %1$s.organization_id"
            + " LEFT JOIN action_item ai ON %1$s.source_type = 'ACTION_ITEM' AND ai.id = %1$s.source_id"
            + " AND ai.organization_id = %1$s.organization_id";

    private static final String COMMUNITY_JOIN = " LEFT JOIN communities c ON c.id = %1$s.community_id"
            + " AND c.organization_id = %1$s.organization_id";

    private final NamedParameterJdbcTemplate jdbc;
    private final ExpertisePersonalDataFootprint footprint;

    @Override
    @Transactional(readOnly = true)
    public ExportFragment export(ExportScope scope) {

        return switch (scope) {
            case ExportScope.InOrganization organization -> organizationFragment(organization);
            case ExportScope.Person _ -> ExportFragment.nothing("expertise.json");
        };
    }

    private ExportFragment organizationFragment(ExportScope.InOrganization scope) {

        Map<String, Object> payload = Map.of("expertise_signal", signals(scope), "expertise_hidden_tag",
                rowsOf("expertise_hidden_tag", scope), "expertise_opt_out", rowsOf("expertise_opt_out", scope),
                "expertise_dismissed_signal", dismissedSignals(scope), "expertise_declaration", declarations(scope));
        return new ExportFragment("expertise.json", payload, List.of());
    }

    private List<Map<String, Object>> rowsOf(String table, ExportScope.InOrganization scope) {

        var query = ExportQueries.byOrganizationAndUser(footprint.table(table), "user_id", scope);
        return jdbc.queryForList(query.sql(), query.params());
    }

    private List<Map<String, Object>> signals(ExportScope.InOrganization scope) {

        var columns = ExportQueries.qualifiedColumnList(footprint.table("expertise_signal"), scope, "user_id", "es");
        var sql = new StringBuilder("SELECT ").append(columns).append(", c.name AS community, ").append(SOURCE_LABEL)
                .append(" FROM expertise_signal es").append(COMMUNITY_JOIN.formatted("es"))
                .append(SOURCE_JOINS.formatted("es")).append(" WHERE es.organization_id = :org");
        var params = new MapSqlParameterSource("org", scope.organizationId());
        if (scope instanceof ExportScope.OneMember member) {
            sql.append(" AND es.user_id = :user");
            params.addValue("user", member.userId());
        }
        return jdbc.queryForList(sql.toString(), params);
    }

    private List<Map<String, Object>> dismissedSignals(ExportScope.InOrganization scope) {

        var columns = ExportQueries.qualifiedColumnList(footprint.table("expertise_dismissed_signal"), scope, "user_id",
                "eds");
        var sql = new StringBuilder("SELECT ").append(columns).append(", ").append(SOURCE_LABEL)
                .append(" FROM expertise_dismissed_signal eds").append(SOURCE_JOINS.formatted("eds"))
                .append(" WHERE eds.organization_id = :org");
        var params = new MapSqlParameterSource("org", scope.organizationId());
        if (scope instanceof ExportScope.OneMember member) {
            sql.append(" AND eds.user_id = :user");
            params.addValue("user", member.userId());
        }
        return jdbc.queryForList(sql.toString(), params);
    }

    private List<Map<String, Object>> declarations(ExportScope.InOrganization scope) {

        var columns = ExportQueries.qualifiedColumnList(footprint.table("expertise_declaration"), scope, "user_id",
                "ed");
        var sql = new StringBuilder("SELECT ").append(columns)
                .append(", c.name AS community FROM expertise_declaration ed").append(COMMUNITY_JOIN.formatted("ed"))
                .append(" WHERE ed.organization_id = :org");
        var params = new MapSqlParameterSource("org", scope.organizationId());
        if (scope instanceof ExportScope.OneMember member) {
            sql.append(" AND ed.user_id = :user");
            params.addValue("user", member.userId());
        }
        return jdbc.queryForList(sql.toString(), params);
    }
}
