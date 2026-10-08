package dev.axonia.events.internal;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
 * A session's schedule is community history naming several people in several roles. {@code action_item} names a member
 * across two columns - raiser and assignee - so a member's export reaches a row if either names them, not just one: an
 * action item where they are only the assignee is still theirs. {@code topic} names a member across one column,
 * {@code proposer_user_id}, so it fits {@link ExportQueries}' blanket rule like every other single-column table here.
 * {@code event_slot_presenter} carries no {@code organization_id} of its own - it is reached, exactly as
 * {@link EventsMemberDataEraser} reaches it, by joining up through its slot to the session that carries the
 * organization.
 *
 * <p>
 * {@code action_item}'s two-column reach is the one place {@link ExportQueries}' blanket rule does not apply.
 * Everywhere else a member-scoped export matches on exactly one subject column and can withhold the rest of them
 * wholesale; here either column may be the one that made the row theirs, and the answer differs per row. So both are
 * kept in the projection and each is emitted only on the rows where it names the requesting member: an action item
 * somebody else raised and assigned to them is theirs to receive, but the raiser's identity on it is the raiser's own
 * personal data, and Art. 15(4) is explicit that a copy must not adversely affect another person's rights.
 *
 * <p>
 * Every table here now names what a key used to point at, so none of them fits
 * {@link ExportQueries#rowsByOrganizationAndUser} any more. Eight near-identical bespoke methods is the duplication the
 * module's CPD gate exists to catch, so the six that differ only in their table, alias, match column and joins are
 * described as a {@link LabelledTable} and run through one query builder; {@code event_slot_presenter} and
 * {@code action_item} keep their own methods because their shape genuinely differs, one reaching its tenant by joining
 * upward and the other matching on two columns at once.
 *
 * <p>
 * Every label join is a {@code LEFT JOIN} carrying its own {@code organization_id} predicate, and both halves matter.
 * Left, because a session deleted out from under an attendance row must leave the attendance intact with a nameless
 * session rather than drop it from somebody's export. Tenanted, because the tests connect as the table owner, which
 * bypasses row-level security entirely, so an id-only join is green here and reads across tenants in production. The
 * joins inside {@code event_slot_presenter}'s chain are the exception and stay inner: they are the row's only path to
 * an organization, not labels, and a presenter row with no session is not a row anybody can scope.
 *
 * <p>
 * A session's title is nullable, because an occurrence of a series is normally called whatever the series is called.
 * {@code COALESCE(cs.title, ser.title)} is therefore what a person actually knows the session by, and it is what every
 * session-shaped label carries, including {@code action_item}'s origin and checkpoint - a bare {@code title} there
 * renders null for exactly the common case.
 */
@Component
@RequiredArgsConstructor
public class EventsPersonalDataExporter implements PersonalDataExporter {

    private static final String COMMUNITY_LABEL = "c.name AS community";

    private static final String COMMUNITY_JOIN = " LEFT JOIN communities c ON c.id = %1$s.community_id"
            + " AND c.organization_id = %1$s.organization_id";

    private static final String SERIES_JOIN = " LEFT JOIN event_series ser ON ser.id = cs.series_id"
            + " AND ser.organization_id = cs.organization_id";

    private static final String SESSION_JOIN = " LEFT JOIN community_session cs ON cs.id = %1$s.session_id"
            + " AND cs.organization_id = %1$s.organization_id" + SERIES_JOIN;

    private static final String SESSION_LABEL = "COALESCE(cs.title, ser.title) AS session";

    private static final String DATED_SESSION_LABEL = SESSION_LABEL + ", cs.occurrence_date AS session_date";

    private static final String PRESENTER_SQL = " FROM event_slot_presenter esp"
            + " JOIN event_slot sl ON esp.slot_id = sl.id" + " JOIN event_section sec ON sl.section_id = sec.id"
            + " JOIN community_session cs ON sec.session_id = cs.id" + SERIES_JOIN + " WHERE cs.organization_id = :org";

    private static final String ACTION_ITEM_SQL = ", " + COMMUNITY_LABEL
            + ", COALESCE(origin.title, originSeries.title) AS origin_session"
            + ", COALESCE(checkpoint.title, checkpointSeries.title) AS checkpoint_session FROM action_item ai"
            + COMMUNITY_JOIN.formatted("ai") + " LEFT JOIN community_session origin ON origin.id = ai.origin_session_id"
            + " AND origin.organization_id = ai.organization_id"
            + " LEFT JOIN event_series originSeries ON originSeries.id = origin.series_id"
            + " AND originSeries.organization_id = origin.organization_id"
            + " LEFT JOIN community_session checkpoint ON checkpoint.id = ai.checkpoint_session_id"
            + " AND checkpoint.organization_id = ai.organization_id"
            + " LEFT JOIN event_series checkpointSeries ON checkpointSeries.id = checkpoint.series_id"
            + " AND checkpointSeries.organization_id = checkpoint.organization_id" + " WHERE ai.organization_id = :org";

    private static final Set<String> ACTION_ITEM_PARTIES = Set.of("raised_by_user_id", "assignee_user_id");

    private static final LabelledTable TOPIC = new LabelledTable("topic", "t", "proposer_user_id", COMMUNITY_LABEL,
            COMMUNITY_JOIN.formatted("t"));

    private static final LabelledTable TOPIC_VOTE = new LabelledTable("topic_vote", "tv", "user_id", "t.title AS topic",
            " LEFT JOIN topic t ON t.id = tv.topic_id AND t.organization_id = tv.organization_id");

    private static final LabelledTable SESSION_ATTENDANCE = new LabelledTable("session_attendance", "sa", "user_id",
            DATED_SESSION_LABEL, SESSION_JOIN.formatted("sa"));

    private static final LabelledTable COMMUNITY_SESSION = new LabelledTable("community_session", "cs",
            "facilitator_user_id", COMMUNITY_LABEL + ", ser.title AS series",
            COMMUNITY_JOIN.formatted("cs") + SERIES_JOIN);

    private static final LabelledTable CALENDAR_SUBSCRIPTION = new LabelledTable("calendar_subscription", "cal",
            "user_id", COMMUNITY_LABEL, COMMUNITY_JOIN.formatted("cal"));

    private static final LabelledTable ACTION_ITEM_CHECKPOINT = new LabelledTable("action_item_checkpoint", "aic",
            "decided_by_user_id", "ai.title AS action_item, " + SESSION_LABEL,
            " LEFT JOIN action_item ai ON ai.id = aic.action_item_id AND ai.organization_id = aic.organization_id"
                    + SESSION_JOIN.formatted("aic"));

    private final NamedParameterJdbcTemplate jdbc;
    private final EventsPersonalDataFootprint footprint;

    @Override
    @Transactional(readOnly = true)
    public ExportFragment export(ExportScope scope) {

        return switch (scope) {
            case ExportScope.InOrganization organization -> organizationFragment(organization);
            case ExportScope.Person _ -> ExportFragment.nothing("events.json");
        };
    }

    private ExportFragment organizationFragment(ExportScope.InOrganization scope) {

        Map<String, Object> payload = Map.of("topic", rowsWithLabels(TOPIC, scope), "topic_vote",
                rowsWithLabels(TOPIC_VOTE, scope), "session_attendance", rowsWithLabels(SESSION_ATTENDANCE, scope),
                "community_session", rowsWithLabels(COMMUNITY_SESSION, scope), "event_slot_presenter",
                presenters(scope), "calendar_subscription", rowsWithLabels(CALENDAR_SUBSCRIPTION, scope), "action_item",
                actionItems(scope), "action_item_checkpoint", rowsWithLabels(ACTION_ITEM_CHECKPOINT, scope));
        return new ExportFragment("events.json", payload, List.of());
    }

    /**
     * One table carrying its own {@code organization_id} and naming the member in one column, plus the label aliases it
     * emits in place of the keys it no longer exports and the joins those aliases read from.
     */
    private record LabelledTable(String table, String alias, String userColumn, String labels, String joins) {
    }

    private List<Map<String, Object>> rowsWithLabels(LabelledTable spec, ExportScope.InOrganization scope) {

        var columns = ExportQueries.qualifiedColumnList(footprint.table(spec.table()), scope, spec.userColumn(),
                spec.alias());
        var sql = new StringBuilder("SELECT ").append(columns).append(", ").append(spec.labels()).append(" FROM ")
                .append(spec.table()).append(' ').append(spec.alias()).append(spec.joins()).append(" WHERE ")
                .append(spec.alias()).append(".organization_id = :org");
        var params = new MapSqlParameterSource("org", scope.organizationId());
        if (scope instanceof ExportScope.OneMember member) {
            sql.append(" AND ").append(spec.alias()).append('.').append(spec.userColumn()).append(" = :user");
            params.addValue("user", member.userId());
        }
        return jdbc.queryForList(sql.toString(), params);
    }

    /** An action item names a member across two columns - raiser and assignee - so it needs its own masking. */
    private List<Map<String, Object>> actionItems(ExportScope.InOrganization scope) {

        var table = footprint.table("action_item");
        var params = new MapSqlParameterSource("org", scope.organizationId());
        if (!(scope instanceof ExportScope.OneMember member)) {
            var columns = ExportQueries.qualifiedColumnList(table, scope, "raised_by_user_id", "ai");
            return jdbc.queryForList("SELECT " + columns + ACTION_ITEM_SQL, params);
        }
        params.addValue("user", member.userId());
        var projection = table.exportedColumns().stream().map(EventsPersonalDataExporter::onlyWhenItNamesTheMember)
                .collect(Collectors.joining(", "));
        return jdbc.queryForList("SELECT " + projection + ACTION_ITEM_SQL
                + " AND (ai.raised_by_user_id = :user OR ai.assignee_user_id = :user)", params);
    }

    /** Keeps the column and its name, but leaves it null on the rows where it names the co-named other party. */
    private static String onlyWhenItNamesTheMember(String column) {

        if (!ACTION_ITEM_PARTIES.contains(column)) {
            return "ai." + column;
        }
        return "CASE WHEN ai.%1$s = :user THEN ai.%1$s END AS %1$s".formatted(column);
    }

    private List<Map<String, Object>> presenters(ExportScope.InOrganization scope) {

        var columns = ExportQueries.qualifiedColumnList(footprint.table("event_slot_presenter"), scope, "user_id",
                "esp");
        var sql = new StringBuilder("SELECT ").append(columns).append(", sl.title AS slot, ")
                .append(DATED_SESSION_LABEL).append(PRESENTER_SQL);
        var params = new MapSqlParameterSource("org", scope.organizationId());
        if (scope instanceof ExportScope.OneMember member) {
            sql.append(" AND esp.user_id = :user");
            params.addValue("user", member.userId());
        }
        return jdbc.queryForList(sql.toString(), params);
    }
}
