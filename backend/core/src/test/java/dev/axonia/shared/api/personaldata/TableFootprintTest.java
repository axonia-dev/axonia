package dev.axonia.shared.api.personaldata;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TableFootprintTest {

    @Test
    void declaredColumnsIsTheUnionOfBothBuckets() {

        var footprint = TableFootprint.builder().table("bookmark").category(DataCategory.WHAT_YOU_SAVED)
                .personal(
                        List.of(new ColumnFootprint("member_user_id", Classification.SUBJECT, Disposition.DELETE_ROW)))
                .notPersonal(Set.of("id", "organization_id")).build();

        assertThat(footprint.declaredColumns()).containsExactlyInAnyOrder("member_user_id", "id", "organization_id");
    }

    @Test
    void aColumnCannotSitInBothBuckets() {

        assertThatThrownBy(() -> TableFootprint.builder().table("bookmark").category(DataCategory.WHAT_YOU_SAVED)
                .personal(
                        List.of(new ColumnFootprint("member_user_id", Classification.SUBJECT, Disposition.DELETE_ROW)))
                .notPersonal(Set.of("member_user_id")).build()).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("member_user_id");
    }

    @Test
    void aWithheldPersonalColumnIsErasedButNeverExported() {

        var table = TableFootprint.builder().table("identity_user").category(DataCategory.WHO_YOU_ARE)
                .personal(List.of(new ColumnFootprint("user_id", Classification.SUBJECT, Disposition.DELETE_ROW),
                        new ColumnFootprint("scim_id", Classification.PERSONAL_CONTENT, Disposition.DELETE_ROW,
                                ExportTreatment.WITHHELD)))
                .notPersonal(Set.of("id")).exportedContext(Set.of()).exportedLabels(Set.of()).build();

        assertThat(table.exportedColumns()).containsExactly("user_id");
        assertThat(table.declaredColumns()).describedAs("withheld from export, still declared, still erased")
                .contains("scim_id");
    }

    @Test
    void anExportOnlyColumnCannotBeWithheld() {

        assertThatThrownBy(() -> new ColumnFootprint("facts", Classification.PERSONAL_CONTENT, Disposition.EXPORT_ONLY,
                ExportTreatment.WITHHELD)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("'facts'")
                .hasMessageContaining("EXPORT_ONLY");
    }

    @Test
    void aRetainedColumnCannotBeWithheld() {

        assertThatThrownBy(() -> new ColumnFootprint("closed_by", Classification.SUBJECT, Disposition.RETAIN,
                ExportTreatment.WITHHELD)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'closed_by'").hasMessageContaining("RETAIN");
    }

    @Test
    void aBuilderLeavingTheExportSetsUnsetCarriesNeitherContextNorLabels() {

        var table = TableFootprint.builder().table("role").category(DataCategory.ACCESS_AND_SECURITY)
                .personal(List.of()).notPersonal(Set.of("id", "name")).build();

        assertThat(table.exportedContext()).isEmpty();
        assertThat(table.exportedLabels()).isEmpty();
    }

    /**
     * The default that makes the projection safe: a column nobody has opted into is absent from the export, so a
     * migration adding one only has to satisfy {@code PersonalDataFootprintTest} to be excluded from every export
     * package until somebody separately decides it belongs.
     */
    @Test
    void aNonPersonalColumnIsWithheldFromTheExportUntilItIsOptedIn() {

        var footprint = TableFootprint.builder().table("personal_access_token")
                .category(DataCategory.ACCESS_AND_SECURITY)
                .personal(List.of(new ColumnFootprint("user_id", Classification.SUBJECT, Disposition.DELETE_ROW)))
                .notPersonal(Set.of("id", "token_hash", "name")).exportedContext(Set.of("id", "name")).build();

        assertThat(footprint.exportedColumns()).containsExactly("id", "name", "user_id").doesNotContain("token_hash");
    }

    @Test
    void exportedContextMustBeDeclaredNotPersonalFirst() {

        assertThatThrownBy(() -> TableFootprint.builder().table("personal_access_token")
                .category(DataCategory.ACCESS_AND_SECURITY).personal(List.of()).notPersonal(Set.of("id"))
                .exportedContext(Set.of("id", "recovery_secret")).build()).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recovery_secret");
    }

    /**
     * An axonia operator's identity on a tenant's row is another identified person's data, so no export carries it -
     * the same reading of Art. 15(4) that {@link Disposition#RETAIN}'s own javadoc states.
     */
    @Test
    void aRetainedColumnNeverReachesTheExport() {

        var footprint = TableFootprint.builder().table("case_note").category(DataCategory.ACCESS_AND_SECURITY)
                .personal(List.of(new ColumnFootprint("member_user_id", Classification.SUBJECT, Disposition.DELETE_ROW),
                        new ColumnFootprint("operator_email", Classification.PERSONAL_CONTENT, Disposition.RETAIN)))
                .notPersonal(Set.of("id")).exportedContext(Set.of("id")).build();

        assertThat(footprint.exportedColumns()).containsExactly("id", "member_user_id");
        assertThat(footprint.exportedSubjectColumns()).containsExactly("member_user_id");
    }

    /**
     * A column declared twice because it carries two dispositions - {@code topic.assignee_user_id} is cleared while an
     * action item is open and anonymised once it is done - must still appear once in the projection, or the generated
     * SQL selects it twice and the row map silently keeps only one of them.
     */
    @Test
    void aColumnDeclaredUnderTwoDispositionsIsProjectedOnce() {

        var footprint = TableFootprint.builder().table("topic").category(DataCategory.WHAT_YOU_CONTRIBUTE)
                .personal(List.of(new ColumnFootprint("assignee_user_id", Classification.SUBJECT, Disposition.CLEAR),
                        new ColumnFootprint("assignee_user_id", Classification.SUBJECT, Disposition.ANONYMISE)))
                .notPersonal(Set.of("id")).exportedContext(Set.of("id")).build();

        assertThat(footprint.exportedColumns()).containsExactly("assignee_user_id", "id");
    }

    /**
     * {@link ExportQueries} withholds subject columns other than the matched one from a member-scoped export;
     * {@link TableFootprint#exportedSubjectColumns()} is what tells it which those are, so a
     * {@link Classification#PERSONAL_CONTENT} column must not be mistaken for one and dropped.
     */
    @Test
    void onlySubjectColumnsCountAsIdentifiersOfPeople() {

        var footprint = TableFootprint.builder().table("bookmark").category(DataCategory.WHAT_YOU_SAVED)
                .personal(List.of(new ColumnFootprint("member_user_id", Classification.SUBJECT, Disposition.DELETE_ROW),
                        new ColumnFootprint("source_author_user_id", Classification.SUBJECT, Disposition.ANONYMISE),
                        new ColumnFootprint("note", Classification.PERSONAL_CONTENT, Disposition.DELETE_ROW)))
                .notPersonal(Set.of("id")).exportedContext(Set.of("id")).build();

        assertThat(footprint.exportedSubjectColumns()).containsExactlyInAnyOrder("member_user_id",
                "source_author_user_id");
    }

    @Test
    void carriesTheCategoryTheNoticeDescribesItUnder() {

        var footprint = TableFootprint.builder().table("identity_user").category(DataCategory.WHO_YOU_ARE)
                .personal(List.of(new ColumnFootprint("user_id", Classification.SUBJECT, Disposition.DELETE_ROW)))
                .notPersonal(Set.of("id")).build();

        assertThat(footprint.category()).isEqualTo(DataCategory.WHO_YOU_ARE);
    }

    /**
     * A {@code null} category would otherwise compile and construct cleanly, then NPE later inside the manifest gate's
     * {@code .map(DataCategory::section)} - far from the declaration that omitted it. Failing at construction names the
     * table right there, as a declaration that cannot compile without one would.
     */
    @Test
    void aTableCannotDeclareANullCategory() {

        assertThatThrownBy(() -> TableFootprint.builder().table("bookmark").category(null)
                .personal(
                        List.of(new ColumnFootprint("member_user_id", Classification.SUBJECT, Disposition.DELETE_ROW)))
                .notPersonal(Set.of("id")).build()).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("bookmark");
    }

    /**
     * A label is an alias an exporter emits in place of a key, so one named after a column that really exists would
     * quietly overwrite that column's own value in the row map - the label would win, and the fact it displaced would
     * leave the export with nothing to say it was ever there. The guard spans every declared column, personal and not:
     * checking only {@code notPersonal} would let a label called {@code title} shadow a member's own artifact title.
     */
    @Test
    void aLabelCannotBeNamedAfterAColumnThatAlreadyExists() {

        assertThatThrownBy(() -> TableFootprint.builder().table("artifact").category(DataCategory.WHAT_YOU_CONTRIBUTE)
                .personal(List
                        .of(new ColumnFootprint("contributor_user_id", Classification.SUBJECT, Disposition.ANONYMISE)))
                .notPersonal(Set.of("id", "community_id")).exportedLabels(Set.of("community_id")).build())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("artifact")
                .hasMessageContaining("community_id");

        assertThatThrownBy(() -> TableFootprint.builder().table("artifact").category(DataCategory.WHAT_YOU_CONTRIBUTE)
                .personal(List
                        .of(new ColumnFootprint("contributor_user_id", Classification.SUBJECT, Disposition.ANONYMISE)))
                .notPersonal(Set.of("id")).exportedLabels(Set.of("contributor_user_id")).build())
                .describedAs("a personal column is a real column too").isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contributor_user_id");
    }
}
