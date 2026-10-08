package dev.axonia.community.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.PurgerTestSupport;
import dev.axonia.media.MediaStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

class CommunityDataPurgerTest extends PurgerTestSupport {

    private static final String ORG = "default";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private CommunityDataPurger purger;

    @Autowired
    private MediaStorage mediaStorage;

    @BeforeEach
    void resetMediaStorageStub() {
        reset(mediaStorage);
    }

    @Test
    void purgeEmptiesCommunityTablesAndDeletesStoredMedia() {

        // given
        seedCommunityGraph();

        // when
        purger.purge(ORG);

        // then
        assertThat(count("memberships")).isZero();
        assertThat(count("communities")).isZero();
        assertThat(count("community_kind")).isZero();
        verify(mediaStorage).delete("badges/b1");
        verify(mediaStorage).delete("heroes/h1");
    }

    @Test
    void purgeSurvivesAMediaStorageFailure() {

        // given — blob cleanup is best-effort: a storage hiccup must not abort the tenant purge
        seedCommunityGraph();
        doThrow(new UncheckedIOException(new IOException("boom"))).when(mediaStorage).delete(anyString());

        // when
        purger.purge(ORG);

        // then
        assertThat(count("communities")).isZero();
    }

    /**
     * Seeds one community_kind, one communities row (with stored badge/hero media keys) and one memberships row for org
     * "default".
     */
    private void seedCommunityGraph() {

        var kindId = jdbc.queryForObject(
                "INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                        + "VALUES (:org, 'Purger Test Guild', now(), now(), 0) RETURNING id",
                Map.of("org", ORG), Long.class);

        var communityId = jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, badge_key, "
                        + "hero_key, created_at, updated_at, version) VALUES (:org, 'purger-test-community', "
                        + "'Purger Test Community', :kindId, '', 'ACTIVE', 'badges/b1', 'heroes/h1', now(), now(), 0) "
                        + "RETURNING id",
                Map.of("org", ORG, "kindId", kindId), Long.class);

        jdbc.update(
                "INSERT INTO memberships (organization_id, community_id, user_id, role, joined_at, updated_at, "
                        + "version) VALUES (:org, :communityId, 'member-user', 'MEMBER', now(), now(), 0)",
                Map.of("org", ORG, "communityId", communityId));
    }

    private long count(String table) {

        return switch (table) {
            case "memberships" -> jdbc.queryForObject("SELECT count(*) FROM memberships WHERE organization_id = :org",
                    Map.of("org", ORG), Long.class);
            case "communities" -> jdbc.queryForObject("SELECT count(*) FROM communities WHERE organization_id = :org",
                    Map.of("org", ORG), Long.class);
            case "community_kind" -> jdbc.queryForObject(
                    "SELECT count(*) FROM community_kind WHERE organization_id = :org", Map.of("org", ORG), Long.class);
            default -> throw new IllegalArgumentException("Unexpected org-scoped table: " + table);
        };
    }
}
