package dev.axonia.shared.api.personaldata;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The projection rules on their own, with no database in the way. The whole-package leak test proves the outcome; this
 * proves the reason, so a red build names the rule that broke rather than only the byte that escaped.
 */
class ExportQueriesTest {

    private static final String ORG = "acme";

    private static final TableFootprint BOOKMARK = TableFootprint.builder().table("bookmark")
            .category(DataCategory.WHAT_YOU_SAVED)
            .personal(List.of(new ColumnFootprint("member_user_id", Classification.SUBJECT, Disposition.DELETE_ROW),
                    new ColumnFootprint("source_author_user_id", Classification.SUBJECT, Disposition.ANONYMISE)))
            .notPersonal(Set.of("id", "helped")).exportedContext(Set.of("id", "helped")).build();

    /**
     * The colleague whose artifact the member saved is an identified person named on the member's own row. Their
     * subject is not part of what the member may ask for, so the column is absent from a member-scoped projection
     * entirely rather than nulled - the row is still theirs, the identifier on it is not.
     */
    @Test
    void aMemberScopedProjectionWithholdsEverySubjectColumnItDidNotMatchOn() {

        var columns = ExportQueries.columnList(BOOKMARK, new ExportScope.OneMember(ORG, "target"), "member_user_id");

        assertThat(columns).isEqualTo("helped, id, member_user_id");
    }

    /** No other person to protect: the whole tenant is the subject of an Organization-wide request. */
    @Test
    void anOrganizationScopedProjectionKeepsEverySubjectColumn() {

        var columns = ExportQueries.columnList(BOOKMARK, new ExportScope.WholeOrganization(ORG), "member_user_id");

        assertThat(columns).isEqualTo("helped, id, member_user_id, source_author_user_id");
    }

    @Test
    void aQualifiedProjectionPrefixesEveryColumnWithTheQuerysAlias() {

        var columns = ExportQueries.qualifiedColumnList(BOOKMARK, new ExportScope.OneMember(ORG, "target"),
                "member_user_id", "bm");

        assertThat(columns).isEqualTo("bm.helped, bm.id, bm.member_user_id");
    }

    @Test
    void aMemberScopedQueryMatchesTheOrganizationAndTheMember() {

        var query = ExportQueries.byOrganizationAndUser(BOOKMARK, "member_user_id",
                new ExportScope.OneMember(ORG, "target"));

        assertThat(query.sql()).isEqualTo(
                "SELECT helped, id, member_user_id FROM bookmark WHERE organization_id = :org AND member_user_id = :user");
        assertThat(query.params()).isEqualTo(Map.of("org", ORG, "user", "target"));
    }

    @Test
    void anOrganizationScopedQueryMatchesTheOrganizationOnly() {

        var query = ExportQueries.byOrganizationAndUser(BOOKMARK, "member_user_id",
                new ExportScope.WholeOrganization(ORG));

        assertThat(query.sql()).isEqualTo(
                "SELECT helped, id, member_user_id, source_author_user_id FROM bookmark WHERE organization_id = :org");
        assertThat(query.params()).isEqualTo(Map.of("org", ORG));
    }
}
