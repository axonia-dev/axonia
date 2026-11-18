package dev.axonia.knowledge.adapter.out.community;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.community.api.spi.LibraryShowcase.LibrarySignals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class LibraryShowcaseAdapterIntegrationTest extends CoreJdbcTestSupport {

    private static final String ORG = "showcase-library";
    private static final String OTHER_ORG = "showcase-library-other";
    private static final long GUILD = 9_100_001L;
    private static final long CIRCLE = 9_100_002L;
    private static final long QUIET = 9_100_003L;
    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private LibraryShowcaseAdapter sut;

    @BeforeEach
    void setUp() {

        sut = new LibraryShowcaseAdapter(jdbc, () -> ORG);
    }

    @Test
    void countsPublishedArtifactsAndNamesTheNewest() {

        // given
        artifact(ORG, GUILD, "Old notes", "ACTIVE", NOW.minus(Duration.ofDays(30)));
        artifact(ORG, GUILD, "Style guide", "ACTIVE", NOW.minus(Duration.ofDays(2)));
        artifact(ORG, GUILD, "Withdrawn deck", "ARCHIVED", NOW.minus(Duration.ofDays(1)));
        artifact(ORG, CIRCLE, "Only one", "ACTIVE", NOW.minus(Duration.ofDays(5)));

        // when
        var actual = sut.signals(Set.of(GUILD, CIRCLE));

        // then
        assertThat(actual).containsOnly(entry(GUILD, new LibrarySignals(2, "Style guide")),
                entry(CIRCLE, new LibrarySignals(1, "Only one")));
    }

    @Test
    void aCommunityWithOnlyWithdrawnArtifactsIsAbsent() {

        // given
        artifact(ORG, GUILD, "Withdrawn deck", "ARCHIVED", NOW.minus(Duration.ofDays(1)));

        // when
        var actual = sut.signals(Set.of(GUILD));

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void unknownIdsAreAbsent() {

        // given
        artifact(ORG, GUILD, "Style guide", "ACTIVE", NOW.minus(Duration.ofDays(2)));
        artifact(ORG, QUIET, "Not asked for", "ACTIVE", NOW.minus(Duration.ofDays(2)));

        // when
        var actual = sut.signals(Set.of(GUILD, 987_654_321L));

        // then
        assertThat(actual).containsOnlyKeys(GUILD);
    }

    @Test
    void noIdsAskNothing() {

        // when / then
        assertThat(sut.signals(Set.of())).isEmpty();
    }

    @Test
    void otherOrganizationIsInvisible() {

        // given
        artifact(OTHER_ORG, GUILD, "Theirs", "ACTIVE", NOW.minus(Duration.ofDays(2)));

        // when
        var actual = sut.signals(Set.of(GUILD));

        // then
        assertThat(actual).isEmpty();
    }

    private void artifact(String organizationId, long communityId, String title, String status, Instant createdAt) {

        jdbc.update("""
                INSERT INTO artifact (organization_id, community_id, type_id, title, description, url,
                        contributor_user_id, status, search_document, created_at, updated_at, version)
                VALUES (:org, :communityId, 1, :title, 'd', 'https://example.com', 'alice',
                        :status, 'doc', :createdAt, now(), 0)
                """, new MapSqlParameterSource().addValue("org", organizationId).addValue("communityId", communityId)
                .addValue("title", title).addValue("status", status).addValue("createdAt", Timestamp.from(createdAt)));
    }
}
