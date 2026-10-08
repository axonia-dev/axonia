package dev.axonia;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * The query shape behind most {@link PersonalDataExporter} implementations: one table, scoped by
 * {@code organization_id} and, for a {@link ExportScope.OneMember} scope, by one more column naming the member. Not
 * every table fits this shape - one carrying no {@code organization_id} of its own, or naming the member across more
 * than one column, builds its own SQL instead - but most do, and repeating this ten times over would be exactly the
 * near-identical duplication the module's jscpd gate exists to catch.
 *
 * <p>
 * The projection is never {@code SELECT *}. It is named column by column, derived from the module's own
 * {@link TableFootprint} via {@link TableFootprint#exportedColumns()}, so a column nobody has decided belongs in an
 * export - a credential digest, an axonia operator's address - cannot reach one by being added to a table.
 *
 * <p>
 * A projection for one person - a member, or a {@link ExportScope.Person} - is narrower still: it drops every
 * {@link Classification#SUBJECT} column other than the one the query matched the member on. Those columns are
 * identifiers of other identified people sitting on a row that is genuinely the member's -
 * {@code bookmark.source_author_user_id} is the colleague whose artifact they saved - and handing them over answers one
 * person's access request with another person's identity. A whole-Organization export keeps them: there is no other
 * person there, the whole tenant is the subject of the request.
 */
public final class ExportQueries {

    private ExportQueries() {
    }

    public static List<Map<String, Object>> rowsByOrganizationAndUser(NamedParameterJdbcTemplate jdbc,
            TableFootprint table, String userColumn, ExportScope.InOrganization scope) {

        var sql = new StringBuilder("SELECT ").append(columnList(table, scope, userColumn)).append(" FROM ")
                .append(table.table()).append(" WHERE organization_id = :org");
        var params = new MapSqlParameterSource("org", scope.organizationId());
        if (scope instanceof ExportScope.OneMember member) {
            sql.append(" AND ").append(userColumn).append(" = :user");
            params.addValue("user", member.userId());
        }
        return jdbc.queryForList(sql.toString(), params);
    }

    /** The {@code SELECT} list for a table this scope's export reads, matched on {@code userColumn}. */
    public static String columnList(TableFootprint table, ExportScope scope, String userColumn) {

        return String.join(", ", exportedColumns(table, scope, userColumn));
    }

    /**
     * The same list, qualified by a query alias, for the exporters whose table carries no {@code organization_id} of
     * its own and has to join up through its parents to find one.
     */
    public static String qualifiedColumnList(TableFootprint table, ExportScope scope, String userColumn, String alias) {

        return exportedColumns(table, scope, userColumn).stream().map(column -> alias + "." + column)
                .collect(Collectors.joining(", "));
    }

    private static List<String> exportedColumns(TableFootprint table, ExportScope scope, String userColumn) {

        var columns = table.exportedColumns();
        if (scope instanceof ExportScope.WholeOrganization) {
            return columns;
        }
        var otherPeople = table.exportedSubjectColumns();
        return columns.stream().filter(column -> column.equals(userColumn) || !otherPeople.contains(column)).toList();
    }
}
