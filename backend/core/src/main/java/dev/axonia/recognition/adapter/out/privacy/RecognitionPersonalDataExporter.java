package dev.axonia.recognition.adapter.out.privacy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.recognition.adapter.out.persistence.ReactionDefinitionRepository;
import dev.axonia.shared.api.personaldata.ExportFragment;
import dev.axonia.shared.api.personaldata.ExportQueries;
import dev.axonia.shared.api.personaldata.ExportScope;
import dev.axonia.shared.api.personaldata.PersonalDataExporter;
import lombok.RequiredArgsConstructor;

/**
 * A love credits the contribution it rides, not the person who gave it, but giving it is still the lover's own action
 * to export. {@code recognized_contribution} has carried no personal column since V4, so it is not part of this
 * fragment as a row of its own, but both a love and a placement join across to it for the contribution's own title - a
 * raw {@code recognized_contribution_id}, or the {@code (contribution_type, contribution_id)} pair a placement carries
 * instead, means nothing to the person reading their own export.
 * <p>
 * A placement also joins across to {@code reaction_definition} for a custom reaction's name - a raw {@code
 * definition_id} means nothing to the person reading their own export either. {@code reaction_definition} names a
 * member across two columns, proposer and approver, exactly as {@code topic} does in the events module: a definition
 * somebody else proposed and this member only approved is still theirs to receive, but the proposer's identity on it is
 * the proposer's own personal data and is left out of a member-scoped row that is not theirs.
 * <p>
 * That join carries its own {@code organization_id} predicate, matching
 * {@link ReactionDefinitionRepository#countUsageByDefinition}. Row-level security makes it redundant in production, but
 * tests connect as the table owner, which bypasses row-level security entirely - without the predicate the two queries
 * would disagree about whether tenancy is the join's business, and no test could tell. It sits in the {@code ON} clause
 * rather than the {@code WHERE} because this is a {@code LEFT JOIN}: in {@code WHERE} it would drop every emoji-arm
 * placement, whose {@code definition_id} is null and which therefore matches no definition row at all.
 */
@Component
@RequiredArgsConstructor
public class RecognitionPersonalDataExporter implements PersonalDataExporter {

    private static final Set<String> DEFINITION_PARTIES = Set.of("proposed_by_user_id", "approved_by_user_id");

    private final NamedParameterJdbcTemplate jdbc;
    private final RecognitionPersonalDataFootprint footprint;

    @Override
    @Transactional(readOnly = true)
    public ExportFragment export(ExportScope scope) {

        return switch (scope) {
            case ExportScope.InOrganization organization -> organizationFragment(organization);
            case ExportScope.Person _ -> ExportFragment.nothing("recognition.json");
        };
    }

    private ExportFragment organizationFragment(ExportScope.InOrganization scope) {

        Map<String, Object> payload = Map.of("contribution_love", loves(scope), "contribution_reaction",
                placements(scope), "reaction_definition", definitions(scope));
        return new ExportFragment("recognition.json", payload, List.of());
    }

    /**
     * {@code contribution_love.recognized_contribution_id} is a direct foreign key to {@code recognized_contribution},
     * so this join is the simple one - unlike {@link #placements}, which reaches the same table through a composite
     * key.
     */
    private List<Map<String, Object>> loves(ExportScope.InOrganization scope) {

        var columns = ExportQueries.qualifiedColumnList(footprint.table("contribution_love"), scope, "lover_user_id",
                "cl");
        var sql = new StringBuilder("SELECT ").append(columns).append(", rc.title AS contribution")
                .append(" FROM contribution_love cl")
                .append(" LEFT JOIN recognized_contribution rc ON rc.id = "
                        + "cl.recognized_contribution_id AND rc.organization_id = cl.organization_id")
                .append(" WHERE cl.organization_id = :org");
        var params = new MapSqlParameterSource("org", scope.organizationId());
        if (scope instanceof ExportScope.OneMember member) {
            sql.append(" AND cl.lover_user_id = :user");
            params.addValue("user", member.userId());
        }
        return jdbc.queryForList(sql.toString(), params);
    }

    /**
     * {@code recognized_contribution} carries no surrogate key a reaction can hold directly: a placement names its
     * contribution by {@code (contribution_type, contribution_id)}, so the join matches both columns, plus
     * {@code organization_id}, rather than {@code contribution_id} alone - an id-only join would collide a
     * {@code SLOT_PRESENTED} contribution with an unrelated {@code ARTIFACT_CAPTURED} one sharing the same id.
     */
    private List<Map<String, Object>> placements(ExportScope.InOrganization scope) {

        var columns = ExportQueries.qualifiedColumnList(footprint.table("contribution_reaction"), scope,
                "reactor_user_id", "cr");
        var sql = new StringBuilder("SELECT ").append(columns).append(", rd.name AS name, rc.title AS contribution")
                .append(" FROM contribution_reaction cr")
                .append(" LEFT JOIN reaction_definition rd ON rd.id = cr.definition_id "
                        + "AND rd.organization_id = cr.organization_id")
                .append(" LEFT JOIN recognized_contribution rc ON rc.organization_id = cr.organization_id "
                        + "AND rc.contribution_type = cr.contribution_type AND rc.contribution_id = cr.contribution_id")
                .append(" WHERE cr.organization_id = :org");
        var params = new MapSqlParameterSource("org", scope.organizationId());
        if (scope instanceof ExportScope.OneMember member) {
            sql.append(" AND cr.reactor_user_id = :user");
            params.addValue("user", member.userId());
        }
        return jdbc.queryForList(sql.toString(), params);
    }

    private List<Map<String, Object>> definitions(ExportScope.InOrganization scope) {

        var columns = footprint.table("reaction_definition").exportedColumns();
        var params = new MapSqlParameterSource("org", scope.organizationId());
        if (!(scope instanceof ExportScope.OneMember member)) {
            return jdbc.queryForList(
                    "SELECT " + String.join(", ", columns) + " FROM reaction_definition WHERE organization_id = :org",
                    params);
        }
        params.addValue("user", member.userId());
        var projection = columns.stream().map(RecognitionPersonalDataExporter::onlyWhenItNamesTheMember)
                .collect(Collectors.joining(", "));
        return jdbc.queryForList("SELECT " + projection + " FROM reaction_definition WHERE organization_id = :org "
                + "AND (proposed_by_user_id = :user OR approved_by_user_id = :user)", params);
    }

    /** Keeps the column and its name, but leaves it null on the rows where it names the co-named other party. */
    private static String onlyWhenItNamesTheMember(String column) {

        if (!DEFINITION_PARTIES.contains(column)) {
            return column;
        }
        return "CASE WHEN %1$s = :user THEN %1$s END AS %1$s".formatted(column);
    }
}
