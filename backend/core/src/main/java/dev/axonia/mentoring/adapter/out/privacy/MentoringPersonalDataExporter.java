package dev.axonia.mentoring.adapter.out.privacy;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.RowMapper;
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
 * A member's mentoring, as they took part in it: their availability, their listing among the mentors, the proposals
 * they sent or received, their pairings with their role but never the partner, the topics of those pairings, the notes
 * they wrote, with their meeting day and the labels of the topics they name, and not the partner's, the meetings they
 * planned, with their day, the labels of their topics and when they were planned, the pointers on those topics and
 * notes, and the words they wrote as a mentee or that were written to them as a mentor. A whole-Organization export
 * carries every row, each child row with its pairing's two people. A person scope has nothing here: every row belongs
 * to an Organization.
 */
@Component
@RequiredArgsConstructor
public class MentoringPersonalDataExporter implements PersonalDataExporter {

    private static final String FILE_NAME = "mentoring.json";

    private static final String NO_ONE = "";

    private static final String COMMUNITY = ", c.name AS " + MentoringPersonalDataFootprint.COMMUNITY_LABEL;

    private static final String IN_PAIRING = " JOIN mentoring_pairing p ON p.id = %1$s.pairing_id"
            + " AND p.organization_id = %1$s.organization_id";

    private static final String OF_COMMUNITY = " LEFT JOIN communities c ON c.id = %1$s.community_id"
            + " AND c.organization_id = %1$s.organization_id";

    private static final String MEMBER_OF_PAIRING = " AND (p.mentor_user_id = :user OR p.mentee_user_id = :user)";

    private static final String TOPICS_OF_NOTE = "ARRAY(SELECT t.label FROM mentoring_note_topic nt"
            + " JOIN mentoring_topic t ON t.id = nt.topic_id AND t.organization_id = nt.organization_id"
            + " WHERE nt.note_id = n.id AND nt.organization_id = n.organization_id ORDER BY t.label)";

    private static final String TOPICS_OF_MEETING = "ARRAY(SELECT t.label FROM mentoring_meeting_topic mt"
            + " JOIN mentoring_topic t ON t.id = mt.topic_id AND t.organization_id = mt.organization_id"
            + " WHERE mt.meeting_id = m.id AND mt.organization_id = m.organization_id ORDER BY t.label)";

    private static final String ROLE_IN_PAIRING = "CASE WHEN p.mentor_user_id = :user THEN 'MENTOR' ELSE 'MENTEE' END";

    /** A proposal's {@code topics} is a SQL array, which an export carries as a plain list. */
    private static final RowMapper<Map<String, Object>> ROWS = new ColumnMapRowMapper() {

        @Override
        protected Object getColumnValue(ResultSet rs, int index) throws SQLException {

            var value = super.getColumnValue(rs, index);
            return value instanceof Array array ? Arrays.asList((Object[]) array.getArray()) : value;
        }
    };

    private final NamedParameterJdbcTemplate jdbc;
    private final MentoringPersonalDataFootprint footprint;

    @Override
    @Transactional(readOnly = true)
    public ExportFragment export(ExportScope scope) {

        return switch (scope) {
            case ExportScope.InOrganization organization ->
                new ExportFragment(FILE_NAME, payload(organization), List.of());
            case ExportScope.Person _ -> ExportFragment.nothing(FILE_NAME);
        };
    }

    private Map<String, Object> payload(ExportScope.InOrganization scope) {

        var payload = new LinkedHashMap<String, Object>();
        payload.put(MentoringPersonalDataFootprint.AVAILABILITY, availability(scope));
        payload.put(MentoringPersonalDataFootprint.LISTING, listings(scope));
        payload.put(MentoringPersonalDataFootprint.PROPOSAL, proposals(scope));
        payload.put(MentoringPersonalDataFootprint.PAIRING, pairings(scope));
        payload.put(MentoringPersonalDataFootprint.TOPIC, topics(scope));
        payload.put(MentoringPersonalDataFootprint.NOTE, notes(scope));
        payload.put(MentoringPersonalDataFootprint.MEETING, meetings(scope));
        payload.put(MentoringPersonalDataFootprint.POINTER, pointers(scope));
        payload.put(MentoringPersonalDataFootprint.WORDS, words(scope));
        return payload;
    }

    private List<Map<String, Object>> availability(ExportScope.InOrganization scope) {

        var select = columns(MentoringPersonalDataFootprint.AVAILABILITY, scope, "user_id", "a") + COMMUNITY;
        var from = "mentoring_availability a" + OF_COMMUNITY.formatted("a") + " WHERE a.organization_id = :org";
        return rows(scope, select, from, " AND a.user_id = :user");
    }

    private List<Map<String, Object>> listings(ExportScope.InOrganization scope) {

        var select = columns(MentoringPersonalDataFootprint.LISTING, scope, "user_id", "l") + COMMUNITY;
        var from = "mentoring_mentor_listing l" + OF_COMMUNITY.formatted("l") + " WHERE l.organization_id = :org";
        return rows(scope, select, from, " AND l.user_id = :user");
    }

    private List<Map<String, Object>> proposals(ExportScope.InOrganization scope) {

        var role = "CASE WHEN m.mentor_user_id = :user THEN 'MENTOR' ELSE 'MENTEE' END";
        var select = columns(MentoringPersonalDataFootprint.PROPOSAL, scope, NO_ONE, "m") + COMMUNITY
                + forMember(scope,
                        ", " + role + " AS " + MentoringPersonalDataFootprint.ROLE_LABEL + ", CASE WHEN "
                                + "m.proposed_by = " + role + " THEN 'SENT' ELSE 'RECEIVED' END AS "
                                + MentoringPersonalDataFootprint.DIRECTION_LABEL);
        var from = "mentoring_proposal m" + OF_COMMUNITY.formatted("m") + " WHERE m.organization_id = :org";
        return rows(scope, select, from, " AND (m.mentor_user_id = :user OR m.mentee_user_id = :user)");
    }

    private List<Map<String, Object>> pairings(ExportScope.InOrganization scope) {

        var select = columns(MentoringPersonalDataFootprint.PAIRING, scope, NO_ONE, "p") + COMMUNITY
                + forMember(scope, ", " + ROLE_IN_PAIRING + " AS " + MentoringPersonalDataFootprint.ROLE_LABEL);
        var from = "mentoring_pairing p" + OF_COMMUNITY.formatted("p") + " WHERE p.organization_id = :org";
        return rows(scope, select, from, MEMBER_OF_PAIRING);
    }

    private List<Map<String, Object>> topics(ExportScope.InOrganization scope) {

        return rows(scope, childColumns(MentoringPersonalDataFootprint.TOPIC, scope, NO_ONE, "t"),
                childOf("mentoring_topic t", "t"), MEMBER_OF_PAIRING);
    }

    private List<Map<String, Object>> notes(ExportScope.InOrganization scope) {

        var select = childColumns(MentoringPersonalDataFootprint.NOTE, scope, "author_user_id", "n") + ", "
                + TOPICS_OF_NOTE + " AS " + MentoringPersonalDataFootprint.TOPICS_LABEL;
        return rows(scope, select, childOf("mentoring_note n", "n"), " AND n.author_user_id = :user");
    }

    private List<Map<String, Object>> meetings(ExportScope.InOrganization scope) {

        var select = childColumns(MentoringPersonalDataFootprint.MEETING, scope, "planned_by", "m") + ", "
                + TOPICS_OF_MEETING + " AS " + MentoringPersonalDataFootprint.TOPICS_LABEL;
        return rows(scope, select, childOf("mentoring_meeting m", "m"), " AND m.planned_by = :user");
    }

    private List<Map<String, Object>> pointers(ExportScope.InOrganization scope) {

        var pointer = "mentoring_pointer x LEFT JOIN mentoring_note n ON n.id = x.note_id"
                + " AND n.organization_id = x.organization_id";
        return rows(scope, childColumns(MentoringPersonalDataFootprint.POINTER, scope, NO_ONE, "x"),
                childOf(pointer, "x"), MEMBER_OF_PAIRING + " AND (x.note_id IS NULL OR n.author_user_id = :user)");
    }

    private List<Map<String, Object>> words(ExportScope.InOrganization scope) {

        var openedAt = scope instanceof ExportScope.OneMember
                ? "CASE WHEN p.mentor_user_id = :user THEN w.opened_at END"
                : "w.opened_at";
        var select = childColumns(MentoringPersonalDataFootprint.WORDS, scope, NO_ONE, "w") + ", " + openedAt + " AS "
                + MentoringPersonalDataFootprint.MENTOR_OPENED_LABEL
                + forMember(scope, ", CASE WHEN p.mentee_user_id = :user THEN 'MENTEE' ELSE 'MENTOR' END AS "
                        + MentoringPersonalDataFootprint.ROLE_LABEL);
        return rows(scope, select, childOf("mentoring_words w", "w"), MEMBER_OF_PAIRING);
    }

    /** A row kept in a pairing: its pairing's community and start, and for the Organization its two people. */
    private String childColumns(String table, ExportScope.InOrganization scope, String userColumn, String alias) {

        var pairingPeople = scope instanceof ExportScope.WholeOrganization
                ? ", p.mentor_user_id, p.mentee_user_id"
                : "";
        return columns(table, scope, userColumn, alias) + COMMUNITY + ", p.started_at AS "
                + MentoringPersonalDataFootprint.PAIRING_STARTED_LABEL + pairingPeople;
    }

    private static String childOf(String from, String alias) {

        return from + IN_PAIRING.formatted(alias) + OF_COMMUNITY.formatted("p") + " WHERE " + alias
                + ".organization_id = :org";
    }

    private String columns(String table, ExportScope.InOrganization scope, String userColumn, String alias) {

        return ExportQueries.qualifiedColumnList(footprint.table(table), scope, userColumn, alias);
    }

    private static String forMember(ExportScope.InOrganization scope, String extra) {

        return scope instanceof ExportScope.OneMember ? extra : "";
    }

    private List<Map<String, Object>> rows(ExportScope.InOrganization scope, String select, String from,
            String memberFilter) {

        var sql = new StringBuilder("SELECT ").append(select).append(" FROM ").append(from);
        var params = new MapSqlParameterSource("org", scope.organizationId());
        if (scope instanceof ExportScope.OneMember member) {
            sql.append(memberFilter);
            params.addValue("user", member.userId());
        }
        return jdbc.query(sql.toString(), params, ROWS);
    }
}
