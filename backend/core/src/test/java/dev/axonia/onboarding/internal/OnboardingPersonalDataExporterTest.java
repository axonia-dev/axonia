package dev.axonia.onboarding.internal;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.ExportScope;
import dev.axonia.FootprintTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Connected as the Testcontainers table owner, so this class proves the SQL and the projection and deliberately proves
 * nothing about RLS - an owner bypasses every policy by ownership. {@code RlsIsolationTest} is where the policy half is
 * proven for both tables.
 */
class OnboardingPersonalDataExporterTest extends FootprintTestSupport {

    private static final String ORG = "default";
    private static final String TARGET = "export-target";
    private static final String NEIGHBOUR = "export-neighbour";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private OnboardingPersonalDataExporter exporter;

    @Test
    void carriesThePersonsOwnPathAndSteps() {

        seedPath(TARGET, "ORGANIZER");
        seedStep(TARGET, 0, "LOOK_AND_FEEL", "/communities/platform-engineering/look-and-feel", null);
        seedStep(TARGET, 1, "FIRST_SESSION", "/communities/platform-engineering/schedule", "2026-09-20");
        seedPath(NEIGHBOUR, "MEMBER");
        seedStep(NEIGHBOUR, 0, "DECLARE_EXPERTISE", "/communities/platform-engineering/me/expertise", null);

        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        assertThat(fragment.fileName()).isEqualTo("onboarding.json");
        assertThat(rowsOf(fragment, "onboarding_path")).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("subject", TARGET).containsEntry("variant", "ORGANIZER")
                        .containsEntry("community_slug", "platform-engineering"));
        var steps = rowsOf(fragment, "onboarding_step");
        assertThat(steps).hasSize(2).extracting(row -> row.get("step_key")).containsExactlyInAnyOrder("LOOK_AND_FEEL",
                "FIRST_SESSION");
        assertThat(steps).filteredOn(row -> "FIRST_SESSION".equals(row.get("step_key"))).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("detail", "2026-09-20").containsKey("route"));
    }

    /**
     * A path belongs to no Organization, so a whole-Organization export must answer with nothing rather than hand an
     * administrator somebody else's onboarding progress.
     */
    @Test
    void anOrganizationScopedExportCarriesNoPathAtAll() {

        seedPath(TARGET, "ORGANIZER");
        seedStep(TARGET, 0, "LOOK_AND_FEEL", "/communities/platform-engineering/look-and-feel", null);

        var fragment = exporter.export(new ExportScope.WholeOrganization(ORG));

        assertThat(rowsOf(fragment, "onboarding_path")).isEmpty();
        assertThat(rowsOf(fragment, "onboarding_step")).isEmpty();
    }

    /**
     * The projection is named from the footprint, so a column nobody decided belonged in an export is absent by
     * construction rather than by anybody remembering. {@code updated_at} and both {@code anchor_*} columns are
     * withheld on {@code onboarding_path}: bookkeeping and idempotency plumbing the person never asked for.
     * {@code created_at} is present rather than withheld - it is opted into {@code exportedContext} - so this also
     * proves the projection is not simply every non-personal column.
     */
    @Test
    void theProjectionWithholdsWhatTheFootprintNeverOptedIn() {

        seedPath(TARGET, "ORGANIZER");
        seedStep(TARGET, 0, "LOOK_AND_FEEL", "/communities/platform-engineering/look-and-feel", null);

        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        assertThat(rowsOf(fragment, "onboarding_path").getFirst())
                .doesNotContainKeys("updated_at", "anchor_organization_id", "anchor_community_id")
                .containsEntry("subject", TARGET).containsKey("created_at");
    }

    private void seedPath(String subject, String variant) {

        jdbc.update("""
                INSERT INTO onboarding_path (subject, variant, community_slug, anchor_organization_id,
                                             anchor_community_id, closed_at, created_at, updated_at)
                VALUES (:subject, :variant, 'platform-engineering', 'org-1', 1, NULL, now(), now())
                """, Map.of("subject", subject, "variant", variant));
    }

    private void seedStep(String subject, int ordinal, String stepKey, String route, String detail) {

        var params = new MapSqlParameterSource().addValue("subject", subject).addValue("ordinal", ordinal)
                .addValue("stepKey", stepKey).addValue("route", route).addValue("detail", detail);
        jdbc.update("""
                INSERT INTO onboarding_step (subject, ordinal, step_key, route, detail, visited_at)
                VALUES (:subject, :ordinal, :stepKey, :route, :detail, NULL)
                """, params);
    }
}
