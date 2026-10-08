package dev.axonia.recognition.internal;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.ExportScope;
import dev.axonia.FootprintTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

class RecognitionPersonalDataExporterTest extends FootprintTestSupport {

    private static final String ORG = "default";
    private static final String TARGET = "target-user";
    private static final String NEIGHBOUR = "neighbour-user";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private RecognitionPersonalDataExporter exporter;

    @Test
    void memberScopedExportCarriesOnlyTheMembersOwnLoves() {

        var contribution = seedContribution("ARTIFACT_CAPTURED", 1L, "Loved Contribution");
        seedLove(TARGET, contribution);
        seedLove(NEIGHBOUR, contribution);

        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        assertThat(fragment.fileName()).isEqualTo("recognition.json");
        var rows = rowsOf(fragment, "contribution_love");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsOnlyKeys("lover_user_id", "created_at", "updated_at", "contribution");
        assertThat(rows.get(0)).containsEntry("lover_user_id", TARGET).containsEntry("contribution",
                "Loved Contribution");
    }

    @Test
    void organizationScopedExportCarriesEveryMembersLoves() {

        var contribution = seedContribution("ARTIFACT_CAPTURED", 1L, "Exporter Test Contribution");
        seedLove(TARGET, contribution);
        seedLove(NEIGHBOUR, contribution);

        var fragment = exporter.export(new ExportScope.WholeOrganization(ORG));

        assertThat(rowsOf(fragment, "contribution_love")).hasSize(2);
    }

    /**
     * A raw definition id means nothing to the person reading their own export - the join resolves it to its name. No
     * {@code recognized_contribution} row was ever seeded for this contribution, so its label stays null: the join is a
     * {@code LEFT JOIN} precisely because a placement can target a contribution nobody has promoted yet.
     */
    @Test
    void memberScopedExportResolvesACustomReactionToItsNameAndCarriesTheEmojiArmDirectly() {

        var definitionId = seedDefinition("ship-it", TARGET, TARGET);
        seedPlacement(TARGET, "🧠", null);
        seedPlacement(TARGET, null, definitionId);
        seedPlacement(NEIGHBOUR, "🧠", null);

        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        var rows = rowsOf(fragment, "contribution_reaction");
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(row -> row.get("emoji")).containsExactlyInAnyOrder("🧠", null);
        assertThat(rows).extracting(row -> row.get("name")).containsExactlyInAnyOrder(null, "ship-it");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row).containsOnlyKeys("reactor_user_id", "contribution_type", "emoji", "created_at", "name",
                    "contribution");
            assertThat(row).containsEntry("contribution_type", "ARTIFACT_CAPTURED").containsEntry("contribution", null);
            assertThat(row.get("created_at")).isNotNull();
        });
    }

    @Test
    void organizationScopedExportCarriesEveryMembersPlacements() {

        seedPlacement(TARGET, "🧠", null);
        seedPlacement(NEIGHBOUR, "🧠", null);

        var fragment = exporter.export(new ExportScope.WholeOrganization(ORG));

        assertThat(rowsOf(fragment, "contribution_reaction")).hasSize(2);
    }

    /**
     * {@code recognized_contribution} carries no surrogate key a reaction can hold directly, so a placement joins to it
     * on {@code (contribution_type, contribution_id)}. Both {@link dev.axonia.recognition.ContributionType} values seed
     * a contribution sharing the same {@code contribution_id}, so a join matching on id alone would resolve one of
     * these two placements to the wrong title.
     */
    @Test
    void memberScopedExportResolvesAPlacementsContributionAcrossBothContributionTypesWithoutCollision() {

        seedContribution("SLOT_PRESENTED", 1L, "A Presented Slot");
        seedContribution("ARTIFACT_CAPTURED", 1L, "A Captured Artifact");
        seedPlacement(TARGET, "🧠", null, "SLOT_PRESENTED", 1L);
        seedPlacement(TARGET, "🎉", null, "ARTIFACT_CAPTURED", 1L);

        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        var rows = rowsOf(fragment, "contribution_reaction");
        assertThat(rows).hasSize(2);
        assertThat(rows).filteredOn(row -> "SLOT_PRESENTED".equals(row.get("contribution_type"))).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("contribution", "A Presented Slot"));
        assertThat(rows).filteredOn(row -> "ARTIFACT_CAPTURED".equals(row.get("contribution_type"))).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("contribution", "A Captured Artifact"));
    }

    /**
     * A definition names a member across two columns, proposer and approver, exactly as {@code topic} does in the
     * events module: a definition somebody else proposed and this member only approved is still theirs to receive, but
     * the proposer's identity on it is the proposer's own personal data and must not ride along.
     */
    @Test
    void memberScopedExportOfADefinitionMasksTheOtherPartysIdentity() {

        seedDefinition("target-proposed", TARGET, NEIGHBOUR);
        seedDefinition("target-approved", NEIGHBOUR, TARGET);
        seedDefinition("unrelated", NEIGHBOUR, NEIGHBOUR);

        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        var rows = rowsOf(fragment, "reaction_definition");
        assertThat(rows).hasSize(2);
        assertThat(rows).filteredOn(row -> "target-proposed".equals(row.get("name"))).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("proposed_by_user_id", TARGET)
                        .containsEntry("approved_by_user_id", null));
        assertThat(rows).filteredOn(row -> "target-approved".equals(row.get("name"))).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("proposed_by_user_id", null)
                        .containsEntry("approved_by_user_id", TARGET));
    }

    @Test
    void organizationScopedExportOfADefinitionCarriesBothIdentitiesUnmasked() {

        seedDefinition("ship-it", TARGET, NEIGHBOUR);

        var fragment = exporter.export(new ExportScope.WholeOrganization(ORG));

        var rows = rowsOf(fragment, "reaction_definition");
        assertThat(rows).singleElement().satisfies(row -> assertThat(row).containsEntry("proposed_by_user_id", TARGET)
                .containsEntry("approved_by_user_id", NEIGHBOUR));
    }

    private Long seedContribution(String contributionType, long contributionId, String title) {

        var params = new MapSqlParameterSource().addValue("org", ORG).addValue("type", contributionType)
                .addValue("contributionId", contributionId).addValue("title", title);
        return jdbc.queryForObject(
                "INSERT INTO recognized_contribution (organization_id, contribution_type, contribution_id, "
                        + "community_id, title, occurred_at, created_at, updated_at, version) "
                        + "VALUES (:org, :type, :contributionId, 1, :title, now(), now(), now(), 0) RETURNING id",
                params, Long.class);
    }

    private void seedLove(String lover, Long contributionId) {

        jdbc.update(
                "INSERT INTO contribution_love (organization_id, recognized_contribution_id, lover_user_id, "
                        + "created_at, updated_at, version) VALUES (:org, :contributionId, :lover, now(), now(), 0)",
                Map.of("org", ORG, "contributionId", contributionId, "lover", lover));
    }

    private void seedPlacement(String reactor, String emoji, Long definitionId) {

        seedPlacement(reactor, emoji, definitionId, "ARTIFACT_CAPTURED", 1L);
    }

    private void seedPlacement(String reactor, String emoji, Long definitionId, String contributionType,
            long contributionId) {

        var params = new MapSqlParameterSource().addValue("org", ORG).addValue("reactor", reactor)
                .addValue("emoji", emoji).addValue("definitionId", definitionId).addValue("type", contributionType)
                .addValue("contributionId", contributionId);
        jdbc.update(
                "INSERT INTO contribution_reaction (organization_id, community_id, contribution_type, "
                        + "contribution_id, reactor_user_id, emoji, definition_id, created_at, updated_at, version) "
                        + "VALUES (:org, 1, :type, :contributionId, :reactor, :emoji, :definitionId, now(), now(), 0)",
                params);
    }

    private Long seedDefinition(String name, String proposedBy, String approvedBy) {

        return jdbc.queryForObject(
                "INSERT INTO reaction_definition (organization_id, name, image_key, state, "
                        + "proposed_by_user_id, approved_by_user_id, created_at, updated_at, version) VALUES "
                        + "(:org, :name, 'k', 'APPROVED', :proposedBy, :approvedBy, now(), now(), 0) RETURNING id",
                Map.of("org", ORG, "name", name, "proposedBy", proposedBy, "approvedBy", approvedBy), Long.class);
    }
}
