package dev.axonia.bookmarks.internal;

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
 * The member's own saves are theirs to export. A colleague's save of the member's own artifact is that colleague's
 * behaviour, not the member's, so it is reached by {@code member_user_id}, never by {@code source_author_user_id} -
 * exporting by the latter would answer one person's access request with somebody else's saving behaviour.
 *
 * <p>
 * The reverse leak is closed by {@link ExportQueries}' member-scope rule rather than by anything written here: on the
 * member's own saved rows, {@code source_author_user_id} is the colleague who authored what they saved, and that column
 * is withheld from a member-scoped projection along with every other subject column the query did not match on. A
 * whole-Organization export keeps it.
 */
@Component
@RequiredArgsConstructor
public class BookmarksPersonalDataExporter implements PersonalDataExporter {

    private final NamedParameterJdbcTemplate jdbc;
    private final BookmarksPersonalDataFootprint footprint;

    @Override
    @Transactional(readOnly = true)
    public ExportFragment export(ExportScope scope) {

        return switch (scope) {
            case ExportScope.InOrganization organization -> organizationFragment(organization);
            case ExportScope.Person _ -> ExportFragment.nothing("bookmarks.json");
        };
    }

    private ExportFragment organizationFragment(ExportScope.InOrganization scope) {

        return new ExportFragment("bookmarks.json", Map.of("bookmark", bookmarks(scope)), List.of());
    }

    /**
     * A raw {@code community_id} or {@code saveable_id} means nothing to the person reading their own export, so this
     * moves off {@link ExportQueries#rowsByOrganizationAndUser} to bespoke SQL that joins the community's name and the
     * saved thing's own title. {@code saveable_id} is polymorphic - it names a row in {@code artifact} or in
     * {@code community_session} depending on {@code saveable_type} - so both joins are present and each is constrained
     * on the type as well as the surrogate key, and exactly one of them matches per row.
     */
    private List<Map<String, Object>> bookmarks(ExportScope.InOrganization scope) {

        var columns = ExportQueries.qualifiedColumnList(footprint.table("bookmark"), scope, "member_user_id", "b");
        var sql = new StringBuilder("SELECT ").append(columns)
                .append(", c.name AS community, COALESCE(a.title, s.title) AS title").append(" FROM bookmark b")
                .append(" LEFT JOIN communities c ON c.id = b.community_id AND c.organization_id = b.organization_id")
                .append(" LEFT JOIN artifact a ON b.saveable_type = 'ARTIFACT' AND a.id = b.saveable_id"
                        + " AND a.organization_id = b.organization_id")
                .append(" LEFT JOIN community_session s ON b.saveable_type = 'SESSION' AND s.id = b.saveable_id"
                        + " AND s.organization_id = b.organization_id")
                .append(" WHERE b.organization_id = :org");
        var params = new MapSqlParameterSource("org", scope.organizationId());
        if (scope instanceof ExportScope.OneMember member) {
            sql.append(" AND b.member_user_id = :user");
            params.addValue("user", member.userId());
        }
        return jdbc.queryForList(sql.toString(), params);
    }
}
