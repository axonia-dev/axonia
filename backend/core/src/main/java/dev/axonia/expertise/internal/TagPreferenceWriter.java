package dev.axonia.expertise.internal;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * A member's privacy choices about a tag, kept in step with a curator's rename, delete, merge and split.
 *
 * <p>
 * A hidden tag names an area: search honours it on the tag a row's community reads it as, which is also the only name
 * the member's own page shows and unhides. Every curation that changes what some of this community's rows read as
 * applies one rule, before it changes them. For the members with those rows, it adds the new area name when they hid
 * the area the rows read as, and drops the old row once no other row of theirs, anywhere in the organization, still
 * reads as it. A row no area carries hides nothing, so the drop is hygiene rather than protection.
 *
 * <p>
 * A dismissal names a source, and a source belongs to exactly one community, so it is rewritten in place wherever that
 * source carries the renamed spelling in this community, and copied onto a split-off spelling, whether or not the
 * dismisser still holds the signal.
 *
 * <p>
 * Every method reads the signals and declarations it is about, so all of them must run <em>before</em> either moves.
 */
@Component
@RequiredArgsConstructor
class TagPreferenceWriter {

    private static final String TOUCHED = "t.community_id = :communityId "
            + "AND :spelling IN (t.tag, COALESCE(a.canonical_tag, t.tag))";
    private static final String READS_AS_AREA = "COALESCE(a.canonical_tag, t.tag) = :area";
    private static final String TOUCHED_READING_AS_AREA = TOUCHED + " AND " + READS_AS_AREA;
    private static final String UNTOUCHED_READING_AS_AREA = "NOT (" + TOUCHED + ") AND " + READS_AS_AREA;

    private static final String ADD_HIDDEN_AREA_SQL = """
            INSERT INTO expertise_hidden_tag (organization_id, user_id, tag)
            SELECT DISTINCT h.organization_id, h.user_id, :to
              FROM expertise_hidden_tag h
             WHERE h.organization_id = :org
               AND h.tag = :area
               AND ({signal} OR {declaration})
            ON CONFLICT DO NOTHING
            """.replace("{signal}", rowOf("expertise_signal", TOUCHED_READING_AS_AREA)).replace("{declaration}",
            rowOf("expertise_declaration", TOUCHED_READING_AS_AREA));

    private static final String DROP_HIDDEN_AREA_SQL = """
            DELETE FROM expertise_hidden_tag h
             WHERE h.organization_id = :org
               AND h.tag = :area
               AND ({signal} OR {declaration})
               AND NOT {otherSignal}
               AND NOT {otherDeclaration}
            """.replace("{signal}", rowOf("expertise_signal", TOUCHED))
            .replace("{declaration}", rowOf("expertise_declaration", TOUCHED))
            .replace("{otherSignal}", rowOf("expertise_signal", UNTOUCHED_READING_AS_AREA))
            .replace("{otherDeclaration}", rowOf("expertise_declaration", UNTOUCHED_READING_AS_AREA));

    private static final String CARRY_DISMISSAL_ON_SPLIT_SQL = """
            INSERT INTO expertise_dismissed_signal (organization_id, user_id, source_type, source_id, tag)
            SELECT d.organization_id, d.user_id, d.source_type, d.source_id, :variant
              FROM expertise_dismissed_signal d
             WHERE d.organization_id = :org
               AND d.tag = :canonical
               AND {sourceSignalled}
            ON CONFLICT DO NOTHING
            """.replace("{sourceSignalled}", sourceSignalledAs(":variant"));

    private static final String DELETE_COLLIDING_DISMISSAL_SQL = """
            DELETE FROM expertise_dismissed_signal d
             WHERE d.organization_id = :org
               AND d.tag = :from
               AND EXISTS (SELECT 1 FROM expertise_dismissed_signal o
                            WHERE o.organization_id = d.organization_id
                              AND o.user_id         = d.user_id
                              AND o.source_type     = d.source_type
                              AND o.source_id       = d.source_id
                              AND o.tag             = :to)
               AND {sourceSignalled}
            """.replace("{sourceSignalled}", sourceSignalledAs(":from"));

    private static final String RENAME_DISMISSAL_SQL = """
            UPDATE expertise_dismissed_signal d
               SET tag = :to
             WHERE d.organization_id = :org
               AND d.tag = :from
               AND {sourceSignalled}
            """.replace("{sourceSignalled}", sourceSignalledAs(":from"));

    private static final String PRUNE_DISMISSAL_SQL = """
            DELETE FROM expertise_dismissed_signal d
             WHERE d.organization_id = :org
               AND d.tag = :tag
               AND {sourceSignalled}
            """.replace("{sourceSignalled}", sourceSignalledAs(":tag"));

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * This community's rows stored or read as {@code from} are about to read as {@code to}: a rename, or a merge of
     * {@code from} into {@code to}.
     */
    void renameHiddenArea(String organizationId, long communityId, String from, String to) {

        moveHiddenArea(hiddenAreaParams(organizationId, communityId, from, from).addValue("to", to));
    }

    /**
     * This community's rows stored as {@code variant} are about to read as {@code variant} instead of
     * {@code canonical}. Call before the alias is removed.
     */
    void splitHiddenArea(String organizationId, long communityId, String variant, String canonical) {

        moveHiddenArea(hiddenAreaParams(organizationId, communityId, variant, canonical).addValue("to", variant));
    }

    /** This community's rows stored or read as {@code tag} are about to go. */
    void dropHiddenArea(String organizationId, long communityId, String tag) {

        jdbc.update(DROP_HIDDEN_AREA_SQL, hiddenAreaParams(organizationId, communityId, tag, tag));
    }

    /** Moves the dismissals of this community's {@code from} signals onto {@code to}. Call before they are renamed. */
    void renameDismissals(String organizationId, long communityId, String from, String to) {

        var params = new MapSqlParameterSource("org", organizationId).addValue("communityId", communityId)
                .addValue("from", from).addValue("to", to);
        // The colliding row goes first: the primary key would otherwise reject the rewrite for a member who
        // dismissed both names on the same source.
        jdbc.update(DELETE_COLLIDING_DISMISSAL_SQL, params);
        jdbc.update(RENAME_DISMISSAL_SQL, params);
    }

    /**
     * Copies the dismissals made under {@code canonical} onto this community's signals stored as {@code variant}. Call
     * before the alias is removed.
     */
    void carryDismissalsOnSplit(String organizationId, long communityId, String variant, String canonical) {

        jdbc.update(CARRY_DISMISSAL_ON_SPLIT_SQL, new MapSqlParameterSource("org", organizationId)
                .addValue("communityId", communityId).addValue("variant", variant).addValue("canonical", canonical));
    }

    /** Drops the dismissals of this community's {@code tag} signals. Call before the signals are removed. */
    void pruneDismissals(String organizationId, long communityId, String tag) {

        jdbc.update(PRUNE_DISMISSAL_SQL, new MapSqlParameterSource("org", organizationId)
                .addValue("communityId", communityId).addValue("tag", tag));
    }

    private void moveHiddenArea(MapSqlParameterSource params) {

        jdbc.update(ADD_HIDDEN_AREA_SQL, params);
        jdbc.update(DROP_HIDDEN_AREA_SQL, params);
    }

    private static MapSqlParameterSource hiddenAreaParams(String organizationId, long communityId, String spelling,
            String area) {

        return new MapSqlParameterSource("org", organizationId).addValue("communityId", communityId)
                .addValue("spelling", spelling).addValue("area", area);
    }

    /**
     * Some signal of this community on dismissal {@code d}'s source is stored as {@code tag}, whoever holds it: credit
     * can sit with another member while the curation runs and come back later.
     */
    private static String sourceSignalledAs(String tag) {

        return """
                EXISTS (SELECT 1 FROM expertise_signal s
                         WHERE s.organization_id = d.organization_id
                           AND s.community_id = :communityId
                           AND s.source_type = d.source_type
                           AND s.source_id = d.source_id
                           AND s.tag = {tag})
                """.replace("{tag}", tag);
    }

    /** A signal or declaration row {@code t} of hidden row {@code h}'s member, with its alias {@code a}, matching. */
    private static String rowOf(String table, String condition) {

        return """
                EXISTS (SELECT 1 FROM {table} t
                          LEFT JOIN expertise_tag_alias a
                                 ON a.organization_id = t.organization_id
                                AND a.community_id = t.community_id
                                AND a.variant_tag = t.tag
                         WHERE t.organization_id = h.organization_id
                           AND t.user_id = h.user_id
                           AND {condition})
                """.replace("{table}", table).replace("{condition}", condition);
    }
}
