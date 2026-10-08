package dev.axonia.knowledge.internal;

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
 * An artifact and its attachments belong to the community's library, but contributing or attaching one is still an
 * action the contributor's export carries. {@code artifact_attachment} carries its own {@code organization_id} - as
 * {@link KnowledgeMemberDataEraser} relies on - so it is reached by that column directly, not through its parent
 * artifact.
 * <p>
 * All three tables here join across for a readable label in place of a raw key - an artifact's community and type, an
 * attachment's artifact and session, an opening's community and artifact - so each is bespoke SQL built from
 * {@link ExportQueries#qualifiedColumnList} rather than {@link ExportQueries#rowsByOrganizationAndUser}. Every join
 * constrains {@code organization_id} as well as the surrogate key, matching how {@link KnowledgeMemberDataEraser}
 * reaches the same rows, and every join is a {@code LEFT JOIN}: a row whose community, type or session was deleted
 * still keeps its own facts and simply carries a null label.
 * <p>
 * Openings are exported including the member's opens of their own artifacts, which the health aggregate excludes. The
 * export is what the product offers in place of an opt-out, so it has to show everything that was recorded, not the
 * subset that happened to be counted.
 */
@Component
@RequiredArgsConstructor
public class KnowledgePersonalDataExporter implements PersonalDataExporter {

    private final NamedParameterJdbcTemplate jdbc;
    private final KnowledgePersonalDataFootprint footprint;

    @Override
    @Transactional(readOnly = true)
    public ExportFragment export(ExportScope scope) {

        return switch (scope) {
            case ExportScope.InOrganization organization -> organizationFragment(organization);
            case ExportScope.Person _ -> ExportFragment.nothing("knowledge.json");
        };
    }

    private ExportFragment organizationFragment(ExportScope.InOrganization scope) {

        Map<String, Object> payload = Map.of("artifact", artifacts(scope), "artifact_attachment", attachments(scope),
                "artifact_opening", openings(scope));
        return new ExportFragment("knowledge.json", payload, List.of());
    }

    private List<Map<String, Object>> artifacts(ExportScope.InOrganization scope) {

        var columns = ExportQueries.qualifiedColumnList(footprint.table("artifact"), scope, "contributor_user_id", "a");
        var sql = new StringBuilder("SELECT ").append(columns)
                .append(", c.name AS community, ty.label AS type,"
                        + " COALESCE(ps.title, pser.title) AS provenance_session FROM artifact a")
                .append(" LEFT JOIN communities c ON c.id = a.community_id AND c.organization_id = a.organization_id")
                .append(" LEFT JOIN artifact_type ty ON ty.id = a.type_id AND ty.organization_id = a.organization_id")
                .append(" LEFT JOIN community_session ps ON ps.id = a.provenance_session_id"
                        + " AND ps.organization_id = a.organization_id")
                .append(" LEFT JOIN event_series pser ON pser.id = ps.series_id"
                        + " AND pser.organization_id = ps.organization_id")
                .append(" WHERE a.organization_id = :org");
        var params = new MapSqlParameterSource("org", scope.organizationId());
        if (scope instanceof ExportScope.OneMember member) {
            sql.append(" AND a.contributor_user_id = :user");
            params.addValue("user", member.userId());
        }
        return jdbc.queryForList(sql.toString(), params);
    }

    private List<Map<String, Object>> attachments(ExportScope.InOrganization scope) {

        var columns = ExportQueries.qualifiedColumnList(footprint.table("artifact_attachment"), scope,
                "attached_by_user_id", "aa");
        var sql = new StringBuilder("SELECT ").append(columns)
                .append(", a.title AS artifact, COALESCE(cs.title, ser.title) AS session FROM artifact_attachment aa")
                .append(" LEFT JOIN artifact a ON a.id = aa.artifact_id AND a.organization_id = aa.organization_id")
                .append(" LEFT JOIN community_session cs ON cs.id = aa.session_id"
                        + " AND cs.organization_id = aa.organization_id")
                .append(" LEFT JOIN event_series ser ON ser.id = cs.series_id"
                        + " AND ser.organization_id = cs.organization_id")
                .append(" WHERE aa.organization_id = :org");
        var params = new MapSqlParameterSource("org", scope.organizationId());
        if (scope instanceof ExportScope.OneMember member) {
            sql.append(" AND aa.attached_by_user_id = :user");
            params.addValue("user", member.userId());
        }
        return jdbc.queryForList(sql.toString(), params);
    }

    private List<Map<String, Object>> openings(ExportScope.InOrganization scope) {

        var columns = ExportQueries.qualifiedColumnList(footprint.table("artifact_opening"), scope, "member_user_id",
                "ao");
        var sql = new StringBuilder("SELECT ").append(columns)
                .append(", c.name AS community, a.title AS artifact FROM artifact_opening ao")
                .append(" LEFT JOIN communities c ON c.id = ao.community_id AND c.organization_id = ao.organization_id")
                .append(" LEFT JOIN artifact a ON a.id = ao.artifact_id AND a.organization_id = ao.organization_id")
                .append(" WHERE ao.organization_id = :org");
        var params = new MapSqlParameterSource("org", scope.organizationId());
        if (scope instanceof ExportScope.OneMember member) {
            sql.append(" AND ao.member_user_id = :user");
            params.addValue("user", member.userId());
        }
        return jdbc.queryForList(sql.toString(), params);
    }
}
