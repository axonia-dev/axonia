package dev.axonia.community.adapter.out.privacy;

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
 * {@code memberships} is community's only personal-data table, and it has no {@code MemberDataEraser} - erasure folds
 * it into {@code MemberPurge} instead - but export is a per-module responsibility regardless, so it gets its own
 * exporter here.
 *
 * <p>
 * A raw {@code community_id} means nothing to the person reading their own export, so the query joins across to
 * {@code communities} for its name. That join is bespoke SQL rather than {@link ExportQueries#byOrganizationAndUser}
 * because that helper writes no joins.
 */
@Component
@RequiredArgsConstructor
public class CommunityPersonalDataExporter implements PersonalDataExporter {

    private final NamedParameterJdbcTemplate jdbc;
    private final CommunityPersonalDataFootprint footprint;

    @Override
    @Transactional(readOnly = true)
    public ExportFragment export(ExportScope scope) {

        return switch (scope) {
            case ExportScope.InOrganization organization -> organizationFragment(organization);
            case ExportScope.Person _ -> ExportFragment.nothing("community.json");
        };
    }

    private ExportFragment organizationFragment(ExportScope.InOrganization scope) {

        return new ExportFragment("community.json", Map.of("memberships", memberships(scope)), List.of());
    }

    private List<Map<String, Object>> memberships(ExportScope.InOrganization scope) {

        var columns = ExportQueries.qualifiedColumnList(footprint.table("memberships"), scope, "user_id", "m");
        var sql = new StringBuilder("SELECT ").append(columns).append(", c.name AS community")
                .append(" FROM memberships m")
                .append(" LEFT JOIN communities c ON c.id = m.community_id AND c.organization_id = m.organization_id")
                .append(" WHERE m.organization_id = :org");
        var params = new MapSqlParameterSource("org", scope.organizationId());
        if (scope instanceof ExportScope.OneMember member) {
            sql.append(" AND m.user_id = :user");
            params.addValue("user", member.userId());
        }
        return jdbc.queryForList(sql.toString(), params);
    }
}
