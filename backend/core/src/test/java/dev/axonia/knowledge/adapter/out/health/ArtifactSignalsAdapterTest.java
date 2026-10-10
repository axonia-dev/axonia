package dev.axonia.knowledge.adapter.out.health;

import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class ArtifactSignalsAdapterTest extends CoreJdbcTestSupport {

    private static final Instant FROM = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant INSIDE = Instant.parse("2026-04-15T18:00:00Z");
    private static final Instant BEFORE = Instant.parse("2026-02-01T18:00:00Z");

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private ArtifactSignalsAdapter sut;

    @BeforeEach
    void setUp() {

        sut = new ArtifactSignalsAdapter(jdbc, () -> "default");
    }

    @Test
    void artifactCounts_perContributorInWindow() {

        // given
        artifact(7L, "alice", INSIDE);
        artifact(7L, "alice", INSIDE.plusSeconds(3600));
        artifact(7L, "bob", INSIDE);
        artifact(7L, "carol", BEFORE);
        artifact(8L, "dana", INSIDE); // other community

        // when
        var actual = sut.artifactCounts(7L, FROM, TO);

        // then
        assertThat(actual).containsOnly(entry("alice", 2L), entry("bob", 1L));
    }

    private void artifact(long communityId, String contributorUserId, Instant createdAt) {

        jdbc.update("""
                INSERT INTO artifact (organization_id, community_id, type_id, title, description, url,
                        contributor_user_id, status, search_document, created_at, updated_at, version)
                VALUES ('default', :communityId, 1, 'T', 'd', 'https://example.com', :contributorUserId,
                        'ACTIVE', 'doc', :createdAt, now(), 0)
                """, new MapSqlParameterSource().addValue("communityId", communityId)
                .addValue("contributorUserId", contributorUserId).addValue("createdAt", Timestamp.from(createdAt)));
    }
}
