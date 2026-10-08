package dev.axonia.knowledge.internal;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.ExportScope;
import dev.axonia.FootprintTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgePersonalDataExporterTest extends FootprintTestSupport {

    private static final String ORG = "default";
    private static final String TARGET = "target-user";
    private static final String NEIGHBOUR = "neighbour-user";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private KnowledgePersonalDataExporter exporter;

    @Test
    void memberScopedExportCarriesTheMembersOwnRowsWithLabelsResolvedAndNoOneElses() {

        var communityId = seedCommunity("Exporter Test Community");
        var typeId = seedArtifactType("Exporter Test Type");
        var sessionId = seedSession(communityId, "Exporter Test Session");

        var targetArtifact = seedArtifact(TARGET, "Target's artifact", communityId, typeId, sessionId);
        seedArtifact(NEIGHBOUR, "Neighbour's artifact", communityId, typeId, sessionId);
        seedAttachment(targetArtifact, TARGET, sessionId);
        var neighbourArtifact = seedArtifact(NEIGHBOUR, "Neighbour's second artifact", communityId, typeId, sessionId);
        seedAttachment(neighbourArtifact, NEIGHBOUR, sessionId);

        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        assertThat(fragment.fileName()).isEqualTo("knowledge.json");
        assertThat(json(fragment)).contains("Target's artifact").doesNotContain("Neighbour's artifact")
                .doesNotContain("Neighbour's second artifact");

        var artifactRows = rowsOf(fragment, "artifact");
        assertThat(artifactRows).singleElement().satisfies(row -> {
            assertThat(row.keySet()).containsExactlyInAnyOrder("contributor_user_id", "title", "description", "url",
                    "provenance_label", "status", "love_count", "created_at", "updated_at", "community", "type",
                    "provenance_session");
            assertThat(row).containsEntry("contributor_user_id", TARGET)
                    .containsEntry("community", "Exporter Test Community").containsEntry("type", "Exporter Test Type")
                    .containsEntry("provenance_session", "Exporter Test Session");
        });

        var attachmentRows = rowsOf(fragment, "artifact_attachment");
        assertThat(attachmentRows).singleElement().satisfies(row -> {
            assertThat(row.keySet()).containsExactlyInAnyOrder("attached_by_user_id", "created_at", "updated_at",
                    "artifact", "session");
            assertThat(row).containsEntry("attached_by_user_id", TARGET).containsEntry("artifact", "Target's artifact")
                    .containsEntry("session", "Exporter Test Session");
        });
    }

    /**
     * The export deliberately shows the member's opens of their own artifact too, even though the health aggregate
     * excludes self-opens: the export is what the product offers in place of an opt-out, so it has to show everything
     * recorded, not the subset that happened to be counted.
     */
    @Test
    void memberScopedExportCarriesOnlyTheMembersOwnOpeningsWithLabelsResolved() {

        var communityId = seedCommunity("Opening Test Community");
        var typeId = seedArtifactType("Opening Test Type");
        var artifactId = seedArtifact(TARGET, "Opened Artifact", communityId, typeId, null);
        seedOpening(communityId, artifactId, TARGET);
        seedOpening(communityId, artifactId, NEIGHBOUR);

        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        var rows = rowsOf(fragment, "artifact_opening");
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.keySet()).containsExactlyInAnyOrder("member_user_id", "opened_on", "created_at", "community",
                    "artifact");
            assertThat(row).containsEntry("member_user_id", TARGET).containsEntry("community", "Opening Test Community")
                    .containsEntry("artifact", "Opened Artifact");
        });
    }

    private void seedOpening(Long communityId, Long artifactId, String memberUserId) {

        jdbc.update("""
                INSERT INTO artifact_opening (organization_id, community_id, artifact_id, member_user_id,
                                              opened_on, created_at)
                VALUES (:org, :communityId, :artifactId, :memberUserId, DATE '2026-07-27', now())
                """,
                Map.of("org", ORG, "communityId", communityId, "artifactId", artifactId, "memberUserId", memberUserId));
    }

    @Test
    void organizationScopedExportCarriesEveryMembersRows() {

        var communityId = seedCommunity("Whole Org Community");
        var typeId = seedArtifactType("Whole Org Type");

        seedArtifact(TARGET, "Target's artifact", communityId, typeId, null);
        seedArtifact(NEIGHBOUR, "Neighbour's artifact", communityId, typeId, null);

        var fragment = exporter.export(new ExportScope.WholeOrganization(ORG));

        assertThat(json(fragment)).contains("Target's artifact").contains("Neighbour's artifact");
    }

    private Long seedCommunity(String name) {

        var kindId = jdbc.queryForObject(
                "INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                        + "VALUES (:org, :label, now(), now(), 0) RETURNING id",
                Map.of("org", ORG, "label", name + " Guild"), Long.class);
        return jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, "
                        + "updated_at, version) VALUES (:org, :slug, :name, :kindId, '', 'ACTIVE', now(), now(), 0) "
                        + "RETURNING id",
                Map.of("org", ORG, "slug", "exporter-test-" + name.hashCode(), "name", name, "kindId", kindId),
                Long.class);
    }

    private Long seedArtifactType(String label) {

        return jdbc.queryForObject(
                "INSERT INTO artifact_type (organization_id, label, created_at, updated_at, version) "
                        + "VALUES (:org, :label, now(), now(), 0) RETURNING id",
                Map.of("org", ORG, "label", label), Long.class);
    }

    private Long seedSession(Long communityId, String title) {

        return jdbc.queryForObject(
                "INSERT INTO community_session (organization_id, community_id, status, scheduled_at, notes, "
                        + "recording_url, has_time, title, created_at, updated_at, version) VALUES (:org, "
                        + ":communityId, 'SCHEDULED', now(), '', '', true, :title, now(), now(), 0) RETURNING id",
                Map.of("org", ORG, "communityId", communityId, "title", title), Long.class);
    }

    private Long seedArtifact(String contributor, String title, Long communityId, Long typeId,
            Long provenanceSessionId) {

        return jdbc.queryForObject(
                "INSERT INTO artifact (organization_id, community_id, type_id, title, description, url, "
                        + "contributor_user_id, provenance_session_id, provenance_label, status, search_document, "
                        + "created_at, updated_at, version) VALUES (:org, :communityId, :typeId, :title, '', "
                        + "'https://example.test/artifact', :contributor, :provenanceSessionId, 'seeded', "
                        + "'PUBLISHED', 'exporter test artifact', now(), now(), 0) RETURNING id",
                new MapSqlParameterSource().addValue("org", ORG).addValue("communityId", communityId)
                        .addValue("typeId", typeId).addValue("title", title).addValue("contributor", contributor)
                        .addValue("provenanceSessionId", provenanceSessionId),
                Long.class);
    }

    private void seedAttachment(Long artifactId, String attachedBy, Long sessionId) {

        jdbc.update(
                "INSERT INTO artifact_attachment (organization_id, artifact_id, session_id, "
                        + "attached_by_user_id, created_at, updated_at, version) VALUES (:org, :artifactId, "
                        + ":sessionId, :attachedBy, now(), now(), 0)",
                Map.of("org", ORG, "artifactId", artifactId, "sessionId", sessionId, "attachedBy", attachedBy));
    }
}
