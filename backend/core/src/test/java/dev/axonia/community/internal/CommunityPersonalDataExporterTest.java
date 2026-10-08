package dev.axonia.community.internal;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.ExportScope;
import dev.axonia.FootprintTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

class CommunityPersonalDataExporterTest extends FootprintTestSupport {

    private static final String ORG = "default";
    private static final String TARGET = "target-user";
    private static final String NEIGHBOUR = "neighbour-user";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private CommunityPersonalDataExporter exporter;

    @Test
    void memberScopedExportCarriesOnlyTheMembersOwnMemberships() {

        seedMembership(TARGET);
        seedMembership(NEIGHBOUR);

        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        assertThat(fragment.fileName()).isEqualTo("community.json");
        var rows = rowsOf(fragment, "memberships");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsOnlyKeys("user_id", "role", "joined_at", "updated_at", "community");
        assertThat(rows.get(0)).containsEntry("user_id", TARGET);
        assertThat(rows.get(0)).containsEntry("community", "Exporter Test Community");
    }

    @Test
    void organizationScopedExportCarriesEveryMembersMemberships() {

        seedMembership(TARGET);
        seedMembership(NEIGHBOUR);

        var fragment = exporter.export(new ExportScope.WholeOrganization(ORG));

        assertThat(rowsOf(fragment, "memberships")).hasSize(2);
    }

    private void seedMembership(String userId) {

        var communityId = seedCommunity(userId);
        jdbc.update(
                "INSERT INTO memberships (organization_id, community_id, user_id, role, joined_at, updated_at, "
                        + "version) VALUES (:org, :communityId, :user, 'MEMBER', now(), now(), 0)",
                Map.of("org", ORG, "communityId", communityId, "user", userId));
    }

    private Long seedCommunity(String discriminator) {

        var kindId = jdbc.queryForObject(
                "INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                        + "VALUES (:org, :label, now(), now(), 0) RETURNING id",
                Map.of("org", ORG, "label", "Exporter Test Guild " + discriminator), Long.class);
        return jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, "
                        + "updated_at, version) VALUES (:org, :slug, 'Exporter Test Community', :kindId, '', "
                        + "'ACTIVE', now(), now(), 0) RETURNING id",
                Map.of("org", ORG, "slug", "exporter-test-community-" + discriminator, "kindId", kindId), Long.class);
    }
}
