package dev.axonia.installation.adapter.out.privacy;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.axonia.selfhosted.SelfHostedDatabase;
import dev.axonia.selfhosted.SelfHostedSpringBootTest;
import dev.axonia.selfhosted.SelfHostedTestInfrastructure;
import dev.axonia.shared.api.personaldata.ErasureCounts;
import dev.axonia.shared.api.personaldata.ExportScope;
import dev.axonia.shared.api.personaldata.PersonErasure;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/** The history's actor as the runtime role exports and erases it, so the column grant of V121 is what is proven. */
@SelfHostedSpringBootTest
class InstallationPersonalDataTest {

    @Autowired
    private InstallationPersonalDataExporter exporter;

    @Autowired
    private InstallationPersonDataEraser eraser;

    @Test
    void exportsAPersonsOwnChangesWithTheirFieldsAndNoOneElses() throws SQLException {

        // given
        var ana = changed("ana");
        var bob = changed("bob");

        // when
        var actual = exporter.export(new ExportScope.OneMember(SelfHostedTestInfrastructure.ORGANIZATION_ID, ana));

        // then
        assertThat(actual.fileName()).isEqualTo("installation.json");
        assertThat(rows(actual.payload())).singleElement().satisfies(row -> {
            assertThat(row).containsOnlyKeys("actor_user_id", "changes", "integration", "occurred_at")
                    .containsEntry("actor_user_id", ana).containsEntry("integration", "mail");
            assertThat(((JsonNode) row.get("changes")).get(0).path("to").asString()).isEqualTo("smtp.acme.test");
        });
        assertThat(rows(exporter.export(new ExportScope.Person(bob)).payload())).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("actor_user_id", bob));
    }

    @Test
    void aWholeOrganizationExportCarriesEveryChange() throws SQLException {

        // given
        var ana = changed("ana");
        var bob = changed("bob");

        // when
        var actual = exporter.export(new ExportScope.WholeOrganization(SelfHostedTestInfrastructure.ORGANIZATION_ID));

        // then
        assertThat(rows(actual.payload())).extracting(row -> row.get("actor_user_id")).contains(ana, bob);
    }

    @Test
    void erasureClearsTheActorAndKeepsWhatChanged() throws SQLException {

        // given
        var ana = changed("ana");

        // when
        var actual = eraser.erase(PersonErasure.builder().subject(ana).surrogateId("erased-" + ana)
                .erasedAt(Instant.parse("2026-10-04T10:00:00Z")).build());

        // then
        assertThat(actual).isEqualTo(new ErasureCounts(0, 1, 0, 0));
        assertThat(SelfHostedDatabase
                .countAsOwner("SELECT count(*) FROM installation_settings_change WHERE actor_user_id = '" + ana + "'"))
                .isZero();
        assertThat(rows(exporter.export(new ExportScope.Person(ana)).payload())).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> payload) {

        return (List<Map<String, Object>>) payload.get("installation_settings_change");
    }

    private static String changed(String name) throws SQLException {

        var subject = name + "-" + UUID.randomUUID();
        SelfHostedDatabase.asOwner("""
                INSERT INTO installation_settings_change (occurred_at, actor_user_id, integration, changes)
                VALUES (now(), '%s', 'mail',
                        '[{"field":"host","kind":"set","from":null,"to":"smtp.acme.test"}]'::jsonb)
                """.formatted(subject));
        return subject;
    }
}
