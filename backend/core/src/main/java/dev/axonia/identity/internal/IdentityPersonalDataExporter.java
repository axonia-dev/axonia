package dev.axonia.identity.internal;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.ExportFragment;
import dev.axonia.ExportQueries;
import dev.axonia.ExportScope;
import dev.axonia.PersonalDataExporter;
import dev.axonia.identity.IdentityExportFile;
import lombok.RequiredArgsConstructor;

/**
 * Identity owns the person record itself, so its export reads, in one read-only transaction: the profile row itself,
 * personal access tokens, agent connections and directory group membership. The avatar is personal data too, and it
 * lives in object storage rather than in a column: its key is surfaced through {@link ExportFragment#mediaKeys()} so
 * the caller can bundle the blob itself into the export package. Every table here is rooted at an Organization, so a
 * {@link ExportScope.Person}'s export finds nothing here.
 *
 * <p>
 * {@code personal_access_token} is exactly why the projection is named column by column out of
 * {@link IdentityPersonalDataFootprint} rather than selected with a star: it carries a credential digest that does not
 * belong in a package handed to a member.
 *
 * <p>
 * The two remaining raw keys are resolved rather than printed. A {@code directory_group_member} row names its member by
 * the customer's SCIM id and its group by a bigint, so the query joins both across: {@code identity_user} for the
 * canonical subject the rest of the package is keyed on, and {@code directory_group} for the name the person would
 * recognise. That join is also how a member-scoped export finds its rows at all, since it reads
 * {@code identity_user.user_id} rather than matching the SCIM id through a subquery.
 *
 * <p>
 * Both joins are a {@code LEFT JOIN} carrying its own {@code organization_id} predicate. Left, so a row whose group has
 * since been deleted keeps its own facts and carries a null label instead of vanishing from somebody's export.
 * Tenant-scoped, because tests connect as the table owner and bypass row-level security entirely, so an id-only join is
 * green here and crosses tenants in production.
 *
 * <p>
 * {@code mediaKeys} runs its own query rather than reading the exported rows. The two avatar keys are withheld from the
 * projection - they are handles into object storage, not facts about a person - so the rows do not carry them, and
 * reading them off the projection would silently stop bundling the image.
 */
@Component
@RequiredArgsConstructor
public class IdentityPersonalDataExporter implements PersonalDataExporter {

    private final NamedParameterJdbcTemplate jdbc;
    private final IdentityPersonalDataFootprint footprint;

    @Override
    @Transactional(readOnly = true)
    public ExportFragment export(ExportScope scope) {

        return switch (scope) {
            case ExportScope.InOrganization organization -> organizationFragment(organization);
            case ExportScope.Person _ -> ExportFragment.nothing(IdentityExportFile.NAME);
        };
    }

    private ExportFragment organizationFragment(ExportScope.InOrganization scope) {

        Map<String, Object> payload = Map.of("identity_user",
                ExportQueries.rowsByOrganizationAndUser(jdbc, footprint.table("identity_user"), "user_id", scope),
                "personal_access_token",
                ExportQueries.rowsByOrganizationAndUser(jdbc, footprint.table("personal_access_token"), "user_id",
                        scope),
                "agent_connection",
                ExportQueries.rowsByOrganizationAndUser(jdbc, footprint.table("agent_connection"), "user_id", scope),
                "directory_group_member", directoryGroupMembers(scope));
        return new ExportFragment(IdentityExportFile.NAME, payload, mediaKeys(scope));
    }

    private List<Map<String, Object>> directoryGroupMembers(ExportScope.InOrganization scope) {

        var columns = ExportQueries.qualifiedColumnList(footprint.table("directory_group_member"), scope,
                "user_scim_id", "dgm");
        var sql = new StringBuilder("SELECT ").append(columns)
                .append(", iu.user_id AS user_id, dg.display_name AS directory_group")
                .append(" FROM directory_group_member dgm")
                .append(" LEFT JOIN identity_user iu ON iu.organization_id = dgm.organization_id"
                        + " AND iu.scim_id = dgm.user_scim_id")
                .append(" LEFT JOIN directory_group dg ON dg.id = dgm.group_id"
                        + " AND dg.organization_id = dgm.organization_id")
                .append(" WHERE dgm.organization_id = :org");
        var params = new MapSqlParameterSource("org", scope.organizationId());
        if (scope instanceof ExportScope.OneMember member) {
            sql.append(" AND iu.user_id = :user");
            params.addValue("user", member.userId());
        }
        return jdbc.queryForList(sql.toString(), params);
    }

    private List<String> mediaKeys(ExportScope.InOrganization scope) {

        var sql = new StringBuilder(
                "SELECT avatar_media_key, avatar_override_key FROM identity_user WHERE organization_id = :org");
        var params = new MapSqlParameterSource("org", scope.organizationId());
        if (scope instanceof ExportScope.OneMember member) {
            sql.append(" AND user_id = :user");
            params.addValue("user", member.userId());
        }
        return jdbc.queryForList(sql.toString(), params).stream()
                .flatMap(
                        row -> Stream.of((String) row.get("avatar_media_key"), (String) row.get("avatar_override_key")))
                .filter(Objects::nonNull).toList();
    }
}
