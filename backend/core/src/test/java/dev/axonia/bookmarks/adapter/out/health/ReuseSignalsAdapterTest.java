package dev.axonia.bookmarks.adapter.out.health;

import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.health.api.ReuseCounts;

import static org.assertj.core.api.Assertions.assertThat;

class ReuseSignalsAdapterTest extends CoreJdbcTestSupport {

    private static final Instant FROM = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant INSIDE = Instant.parse("2026-04-15T18:00:00Z");
    private static final Instant BEFORE = Instant.parse("2026-02-01T18:00:00Z");

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private ReuseSignalsAdapter sut;

    @BeforeEach
    void setUp() {

        sut = new ReuseSignalsAdapter(jdbc, () -> "default");
    }

    @Test
    void reuseCounts_aggregatesDemandAndPayoffExcludingSelfSavesAndSessions() {

        // given artifacts authored by alice; two artifacts reused by others, a helped/not-helped mix
        bookmark("ARTIFACT", 100L, "bob", "alice", true, INSIDE); // helped = true
        bookmark("ARTIFACT", 100L, "carol", "alice", null, INSIDE); // unanswered
        bookmark("ARTIFACT", 101L, "erin", "alice", false, INSIDE); // helped = false
        bookmark("ARTIFACT", 100L, "alice", "alice", null, INSIDE); // self-save — excluded
        bookmark("ARTIFACT", 100L, "dana", "alice", false, BEFORE); // out of window — excluded
        bookmark("SESSION", 200L, "frank", "alice", null, INSIDE); // not an artifact — excluded

        // when
        var actual = sut.reuseCounts(7L, FROM, TO);

        // then bob + carol + erin counted across artifacts {100, 101}; one helped, one not helped
        assertThat(actual).isEqualTo(new ReuseCounts(3L, 2L, 1L, 1L));
    }

    private void bookmark(String saveableType, long saveableId, String memberUserId, String sourceAuthorUserId,
            Boolean helped, Instant createdAt) {

        jdbc.update("""
                INSERT INTO bookmark (organization_id, community_id, saveable_type, saveable_id, member_user_id,
                        source_author_user_id, helped, helped_at, created_at, updated_at, version)
                VALUES ('default', 7, :saveableType, :saveableId, :memberUserId, :sourceAuthorUserId, :helped,
                        CASE WHEN CAST(:helped AS boolean) IS NULL THEN NULL ELSE now() END, :createdAt, now(), 0)
                """,
                new MapSqlParameterSource().addValue("saveableType", saveableType).addValue("saveableId", saveableId)
                        .addValue("memberUserId", memberUserId).addValue("sourceAuthorUserId", sourceAuthorUserId)
                        .addValue("helped", helped).addValue("createdAt", Timestamp.from(createdAt)));
    }
}
