package dev.axonia.access.adapter.out.privacy;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.shared.api.personaldata.ExportFragment;
import dev.axonia.shared.api.personaldata.ExportScope;
import dev.axonia.shared.api.personaldata.PersonalDataExporter;
import lombok.RequiredArgsConstructor;

/**
 * A {@code GROUP} binding exports no {@code subject_id}, only a {@code group} named by its label or, failing that, its
 * key. A member's own export keeps only {@code USER} bindings on their id, whatever a group key collides with.
 */
@Component
@RequiredArgsConstructor
public class AccessPersonalDataExporter implements PersonalDataExporter {

    private final NamedParameterJdbcTemplate jdbc;
    private final AccessPersonalDataFootprint footprint;

    @Override
    @Transactional(readOnly = true)
    public ExportFragment export(ExportScope scope) {

        return switch (scope) {
            case ExportScope.InOrganization organization -> organizationFragment(organization);
            case ExportScope.Person _ -> ExportFragment.nothing("access.json");
        };
    }

    private ExportFragment organizationFragment(ExportScope.InOrganization scope) {

        return new ExportFragment("access.json", Map.of("role_binding", roleBindings(scope)), List.of());
    }

    private List<Map<String, Object>> roleBindings(ExportScope.InOrganization scope) {

        var projection = footprint.table("role_binding").exportedColumns().stream()
                .map(AccessPersonalDataExporter::projectionOf).collect(Collectors.joining(", "));
        var sql = new StringBuilder("SELECT ").append(projection)
                .append(", CASE WHEN rb.subject_type = 'GROUP' THEN COALESCE(NULLIF(TRIM(rb.subject_label), ''), "
                        + "rb.subject_id) END AS \"group\"")
                .append(", r.name AS role FROM role_binding rb LEFT JOIN role r ON r.id = rb.role_id AND "
                        + "r.organization_id = rb.organization_id WHERE rb.organization_id = :org");
        var params = new MapSqlParameterSource("org", scope.organizationId());
        if (scope instanceof ExportScope.OneMember member) {
            sql.append(" AND rb.subject_type = 'USER' AND rb.subject_id = :user");
            params.addValue("user", member.userId());
        }
        return jdbc.queryForList(sql.toString(), params);
    }

    /** {@code subject_id} names a person; a {@code GROUP} row is labelled by {@code group} instead, never by this. */
    private static String projectionOf(String column) {

        return "subject_id".equals(column)
                ? "CASE WHEN rb.subject_type = 'USER' THEN rb.subject_id END AS subject_id"
                : "rb." + column;
    }
}
