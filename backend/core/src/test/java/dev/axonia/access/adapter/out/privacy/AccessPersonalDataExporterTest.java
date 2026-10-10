package dev.axonia.access.adapter.out.privacy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.FootprintTestSupport;
import dev.axonia.shared.api.personaldata.ExportScope;

import static org.assertj.core.api.Assertions.assertThat;

class AccessPersonalDataExporterTest extends FootprintTestSupport {

    private static final String ORG = "default";
    private static final String TARGET = "target-user";
    private static final String NEIGHBOUR = "neighbour-user";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private AccessPersonalDataExporter exporter;

    @Test
    void memberScopedExportCarriesTheMembersOwnBindingAndNoOneElses() {

        var roleId = seedRole("exporter-test-role");
        seedBinding("USER", TARGET, roleId, "Target Subject");
        seedBinding("USER", NEIGHBOUR, roleId, "Neighbour Subject");
        seedBinding("GROUP", TARGET, roleId, "Group Named Like The Target");

        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        assertThat(fragment.fileName()).isEqualTo("access.json");
        var rows = rowsOf(fragment, "role_binding");
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.keySet()).containsExactlyInAnyOrder("subject_id", "subject_type", "created_at", "updated_at",
                    "role", "group");
            assertThat(row).containsEntry("subject_id", TARGET).containsEntry("subject_type", "USER")
                    .containsEntry("role", "exporter-test-role");
            assertThat(row.get("group")).isNull();
        });
    }

    /**
     * The raw {@code role_id} means nothing to the person reading their own export - the join resolves it to its name.
     */
    @Test
    void organizationScopedExportCarriesEveryMembersBindingWithTheRoleNameResolved() {

        var roleId = seedRole("exporter-test-role");
        seedBinding("USER", TARGET, roleId, "Target Subject");
        seedBinding("USER", NEIGHBOUR, roleId, "Neighbour Subject");

        var fragment = exporter.export(new ExportScope.WholeOrganization(ORG));

        var rows = rowsOf(fragment, "role_binding");
        assertThat(rows).extracting(row -> row.get("subject_id")).containsExactlyInAnyOrder(TARGET, NEIGHBOUR);
        assertThat(rows).allSatisfy(
                row -> assertThat(row).containsEntry("role", "exporter-test-role").doesNotContainKey("subject_label"));
    }

    /** {@code subject_id} is a group's opaque directory key; the group is named by its own {@code subject_label}. */
    @Test
    void aGroupBindingIsLabelledByItsDirectoryDisplayNameAndNeverLeaksItsKeyAsASubject() {

        var roleId = seedRole("group-labelled-role");
        seedBinding("GROUP", "group-guid-123", roleId, "Engineering Leads");

        var fragment = exporter.export(new ExportScope.WholeOrganization(ORG));

        assertThat(rowsOf(fragment, "role_binding")).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("group", "Engineering Leads");
            assertThat(row.get("subject_id")).isNull();
        });
    }

    /** No admin-chosen name yet: the group falls back to its own key rather than to "Deleted member". */
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void aGroupBindingWithNoLabelFallsBackToItsOwnKey(String blankLabel) {

        var roleId = seedRole("group-unlabelled-role");
        seedBinding("GROUP", "group-guid-456", roleId, blankLabel);

        var fragment = exporter.export(new ExportScope.WholeOrganization(ORG));

        assertThat(rowsOf(fragment, "role_binding")).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("group", "group-guid-456"));
    }

    /**
     * The case that made {@code subject_label} withheld rather than exported. {@code JwtClaimsReader} resolves a
     * display name as {@code name}, then the joined name parts, then {@code preferred_username}, then the raw subject -
     * so a member arriving from an identity provider that asserts no name claim has their subject written into this
     * column at join time and kept there. Exporting the column verbatim would hand back the one identifier the whole
     * projection exists to withhold, and it would do it only for those members, which is the worst way to find out.
     */
    @Test
    void withholdsALabelThatIsReallyTheSubjectBecauseTheIdpAssertedNoName() {

        var roleId = seedRole("no-name-idp-role");
        seedBinding("USER", TARGET, roleId, TARGET);

        var fragment = exporter.export(new ExportScope.WholeOrganization(ORG));

        assertThat(json(fragment)).describedAs("the label fell back to the subject, so printing it leaks the subject")
                .doesNotContain("\"subject_label\"");
        assertThat(rowsOf(fragment, "role_binding")).singleElement()
                .satisfies(row -> assertThat(row).doesNotContainKey("subject_label"));
    }

    private Long seedRole(String name) {

        return jdbc.queryForObject("INSERT INTO role (organization_id, name, description, created_at, updated_at, "
                + "version) VALUES (:org, :name, 'seeded for exporter test', now(), now(), 0) " + "RETURNING id",
                Map.of("org", ORG, "name", name), Long.class);
    }

    private void seedBinding(String subjectType, String subjectId, Long roleId, String label) {

        jdbc.update(
                "INSERT INTO role_binding (organization_id, subject_type, subject_id, role_id, subject_label, "
                        + "created_at, updated_at, version) VALUES (:org, :subjectType, :subjectId, :roleId, "
                        + ":label, now(), now(), 0)",
                Map.of("org", ORG, "subjectType", subjectType, "subjectId", subjectId, "roleId", roleId, "label",
                        label));
    }
}
