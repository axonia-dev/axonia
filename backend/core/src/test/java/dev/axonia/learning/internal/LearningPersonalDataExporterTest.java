package dev.axonia.learning.internal;

import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.ExportScope;
import dev.axonia.FootprintTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

class LearningPersonalDataExporterTest extends FootprintTestSupport {

    private static final String ORG = "default";
    private static final String TARGET = "target-user";
    private static final String NEIGHBOUR = "neighbour-user";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private LearningPersonalDataExporter exporter;

    @Test
    void memberScopedExportCarriesTheMembersOwnIntentsWithTheCommunitysName() {

        // given
        var communityId = seedCommunity("Learning Community");
        seedIntent(TARGET, "target-tag", communityId);
        seedIntent(NEIGHBOUR, "neighbour-tag", communityId);

        // when
        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        // then
        assertThat(fragment.fileName()).isEqualTo("learning.json");
        assertThat(json(fragment)).contains("target-tag").doesNotContain("neighbour-tag", NEIGHBOUR);
        assertThat(rowsOf(fragment, "learning_intent")).singleElement()
                .satisfies(row -> assertThat(row).containsOnlyKeys("user_id", "tag", "declared_at", "community")
                        .containsEntry("user_id", TARGET).containsEntry("tag", "target-tag")
                        .containsEntry("community", "Learning Community"));
    }

    @Test
    void organizationScopedExportCarriesEveryMembersIntents() {

        // given
        var communityId = seedCommunity("Org Learning Community");
        seedIntent(TARGET, "target-tag", communityId);
        seedIntent(NEIGHBOUR, "neighbour-tag", communityId);

        // when
        var fragment = exporter.export(new ExportScope.WholeOrganization(ORG));

        // then
        assertThat(rowsOf(fragment, "learning_intent")).extracting(row -> row.get("user_id"))
                .containsExactlyInAnyOrder(TARGET, NEIGHBOUR);
    }

    @Test
    void anIntentWhoseCommunityIsGoneCarriesANullCommunity() {

        // given
        seedIntent(TARGET, "orphan-community-tag", 999_999L);

        // when
        var rows = rowsOf(exporter.export(new ExportScope.OneMember(ORG, TARGET)), "learning_intent");

        // then
        assertThat(rows).singleElement().satisfies(row -> assertThat(row).containsEntry("community", null));
    }

    @Test
    void aPersonScopeExportsNothing() {

        // when
        var fragment = exporter.export(new ExportScope.Person(TARGET));

        // then
        assertThat(fragment.fileName()).isEqualTo("learning.json");
        assertThat(fragment.holdsNothing()).isTrue();
    }

    private void seedIntent(String userId, String tag, long communityId) {

        jdbc.update(
                "INSERT INTO learning_intent (organization_id, community_id, user_id, tag, declared_at) "
                        + "VALUES (:org, :communityId, :user, :tag, now())",
                Map.of("org", ORG, "communityId", communityId, "user", userId, "tag", tag));
    }

    private long seedCommunity(String name) {

        var kindId = jdbc.queryForObject(
                "INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) VALUES "
                        + "(:org, :label, now(), now(), 0) RETURNING id",
                Map.of("org", ORG, "label", name + " Kind"), Long.class);
        return jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, "
                        + "updated_at, version) VALUES (:org, :slug, :name, :kindId, '', 'ACTIVE', now(), now(), 0) "
                        + "RETURNING id",
                Map.of("org", ORG, "slug", name.toLowerCase(Locale.ROOT).replace(' ', '-'), "name", name, "kindId",
                        kindId),
                Long.class);
    }
}
