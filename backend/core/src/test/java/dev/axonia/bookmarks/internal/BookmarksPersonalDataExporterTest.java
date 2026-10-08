package dev.axonia.bookmarks.internal;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.ExportScope;
import dev.axonia.FootprintTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

class BookmarksPersonalDataExporterTest extends FootprintTestSupport {

    private static final String ORG = "default";
    private static final String TARGET = "target-user";
    private static final String NEIGHBOUR = "neighbour-user";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private BookmarksPersonalDataExporter exporter;

    /**
     * The neighbour's save of the target's own artifact is the neighbour's behaviour, not the target's - it must be
     * withheld from the target's export even though the target is named on that row as the artifact's author. Only the
     * row the target actually saved belongs to them.
     *
     * <p>
     * The target's own save is deliberately of the NEIGHBOUR's artifact, not their own. Seeding the target as their own
     * author, as this fixture used to, made {@code containsEntry("source_author_user_id", TARGET)} unfalsifiable: the
     * column could hold anyone's subject and the assertion could not tell. With the neighbour as the co-named party the
     * assertion below has something real to be wrong about.
     */
    @Test
    void memberScopedExportCarriesOnlyTheMembersOwnSavesAndNotTheAuthorTheySaved() {

        var communityId = seedCommunity();
        var artifactId = seedArtifact(communityId, "Neighbour's artifact");
        seedBookmark(TARGET, NEIGHBOUR, communityId, "ARTIFACT", artifactId); // target's own save of neighbour's
                                                                              // artifact
        seedBookmark(NEIGHBOUR, TARGET, communityId, "ARTIFACT", artifactId); // neighbour's save of target's artifact
        // uq_bookmark_member is (organization_id, member_user_id, saveable_type, saveable_id), so the neighbour's
        // second save needs a saveable of its own rather than a second row against the same artifact.
        seedBookmark(NEIGHBOUR, NEIGHBOUR, communityId, "ARTIFACT",
                seedArtifact(communityId, "Neighbour's own artifact"));

        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        assertThat(fragment.fileName()).isEqualTo("bookmarks.json");
        var rows = rowsOf(fragment, "bookmark");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsOnlyKeys("member_user_id", "saveable_type", "helped", "helped_at", "created_at",
                "updated_at", "community", "title");
        assertThat(rows.get(0)).containsEntry("member_user_id", TARGET);
        assertThat(rows.get(0)).containsEntry("title", "Neighbour's artifact");
        assertThat(json(fragment)).doesNotContain(NEIGHBOUR);
    }

    @Test
    void organizationScopedExportCarriesEveryMembersSavesWithBothPartiesNamed() {

        var communityId = seedCommunity();
        var artifactId = seedArtifact(communityId, "Shared artifact");
        seedBookmark(TARGET, NEIGHBOUR, communityId, "ARTIFACT", artifactId);
        seedBookmark(NEIGHBOUR, NEIGHBOUR, communityId, "ARTIFACT", artifactId);

        var fragment = exporter.export(new ExportScope.WholeOrganization(ORG));

        var rows = rowsOf(fragment, "bookmark");
        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(row -> assertThat(row).containsOnlyKeys("member_user_id", "source_author_user_id",
                "saveable_type", "helped", "helped_at", "created_at", "updated_at", "community", "title"));
    }

    /**
     * {@code saveable_id} is polymorphic: a SESSION bookmark points at {@code community_session.id}, an ARTIFACT one at
     * {@code artifact.id}. The exporter's {@code title} column resolves through two LEFT JOINs, one per
     * {@code SaveableType}, and a missed branch there would surface as a silent null rather than a failure - so both
     * values are seeded and both titles are asserted, not just one.
     */
    @Test
    void savedArtifactAndSavedSessionEachResolveTheirOwnTitle() {

        var communityId = seedCommunity();
        var artifactId = seedArtifact(communityId, "The target's saved article");
        var sessionId = seedSession(communityId, "The target's saved session");
        seedBookmark(TARGET, TARGET, communityId, "ARTIFACT", artifactId);
        seedBookmark(TARGET, TARGET, communityId, "SESSION", sessionId);

        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        var rows = rowsOf(fragment, "bookmark");
        assertThat(rows).hasSize(2);
        var artifactRow = rows.stream().filter(row -> "ARTIFACT".equals(row.get("saveable_type"))).findFirst()
                .orElseThrow();
        var sessionRow = rows.stream().filter(row -> "SESSION".equals(row.get("saveable_type"))).findFirst()
                .orElseThrow();
        assertThat(artifactRow).containsEntry("title", "The target's saved article");
        assertThat(sessionRow).containsEntry("title", "The target's saved session");
    }

    private void seedBookmark(String member, String author, Long communityId, String saveableType, Long saveableId) {

        jdbc.update("""
                INSERT INTO bookmark (organization_id, community_id, member_user_id, source_author_user_id,
                                      saveable_type, saveable_id, created_at, updated_at, version)
                VALUES (:org, :communityId, :member, :author, :saveableType, :saveableId,
                        '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z', 0)
                """, Map.of("org", ORG, "communityId", communityId, "member", member, "author", author, "saveableType",
                saveableType, "saveableId", saveableId));
    }

    private Long seedCommunity() {

        var kindId = jdbc.queryForObject(
                "INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                        + "VALUES (:org, 'Exporter Test Guild', now(), now(), 0) RETURNING id",
                Map.of("org", ORG), Long.class);
        return jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, "
                        + "updated_at, version) VALUES (:org, 'bookmarks-exporter-test-community', "
                        + "'Exporter Test Community', :kindId, '', 'ACTIVE', now(), now(), 0) RETURNING id",
                Map.of("org", ORG, "kindId", kindId), Long.class);
    }

    private Long seedArtifact(Long communityId, String title) {

        return jdbc.queryForObject(
                "INSERT INTO artifact (organization_id, community_id, type_id, title, description, url, "
                        + "contributor_user_id, status, search_document, created_at, updated_at, version) "
                        + "VALUES (:org, :communityId, 1, :title, '', 'https://example.test/artifact', "
                        + "'someone', 'PUBLISHED', 'exporter test artifact', now(), now(), 0) RETURNING id",
                Map.of("org", ORG, "communityId", communityId, "title", title), Long.class);
    }

    private Long seedSession(Long communityId, String title) {

        return jdbc.queryForObject(
                "INSERT INTO community_session (organization_id, community_id, status, scheduled_at, notes, "
                        + "recording_url, has_time, title, created_at, updated_at, version) "
                        + "VALUES (:org, :communityId, 'SCHEDULED', now(), '', '', true, :title, now(), now(), 0) "
                        + "RETURNING id",
                Map.of("org", ORG, "communityId", communityId, "title", title), Long.class);
    }
}
