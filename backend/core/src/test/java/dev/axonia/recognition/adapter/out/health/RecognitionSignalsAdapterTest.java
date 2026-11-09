package dev.axonia.recognition.adapter.out.health;

import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

class RecognitionSignalsAdapterTest extends CoreJdbcTestSupport {

    private static final Instant FROM = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant INSIDE = Instant.parse("2026-04-15T18:00:00Z");
    private static final Instant BEFORE = Instant.parse("2026-02-01T18:00:00Z");

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private RecognitionSignalsAdapter sut;

    @BeforeEach
    void setUp() {

        sut = new RecognitionSignalsAdapter(jdbc, () -> "default");
    }

    @Test
    void lovesGiven_aggregateCountInWindow() {

        // given two in-window loves and one before the window in community 7,
        // plus an in-window love in community 8
        var rcA = recognizedContribution(7L, 100L);
        love(rcA, "bob", INSIDE);
        love(rcA, "carol", INSIDE.plusSeconds(60));
        love(rcA, "dana", BEFORE);
        love(recognizedContribution(8L, 200L), "erin", INSIDE);

        // when
        var actual = sut.lovesGiven(7L, FROM, TO);

        // then
        assertThat(actual).isEqualTo(2);
    }

    private long recognizedContribution(long communityId, long contributionId) {

        return jdbc.queryForObject("""
                INSERT INTO recognized_contribution (organization_id, contribution_type, contribution_id, community_id,
                        title, occurred_at, created_at, updated_at, version)
                VALUES ('default', 'SLOT_PRESENTED', :contributionId, :communityId, 'Talk',
                        now(), now(), now(), 0)
                RETURNING id
                """, new MapSqlParameterSource().addValue("communityId", communityId).addValue("contributionId",
                contributionId), Long.class);
    }

    private void love(long recognizedContributionId, String loverUserId, Instant createdAt) {

        jdbc.update("""
                INSERT INTO contribution_love (organization_id, recognized_contribution_id, lover_user_id,
                        created_at, updated_at, version)
                VALUES ('default', :rcId, :loverUserId, :createdAt, now(), 0)
                """, new MapSqlParameterSource().addValue("rcId", recognizedContributionId)
                .addValue("loverUserId", loverUserId).addValue("createdAt", Timestamp.from(createdAt)));
    }
}
