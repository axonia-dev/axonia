package dev.axonia.mentoring.adapter.out.persistence;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import dev.axonia.DedicatedPostgresContainer;
import dev.axonia.MigrationChain;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V136 runs against topics seeded in the three old states: ONGOING and LONG_TERM both become IN_PROGRESS, and each
 * column is renumbered from 0 in its old order.
 */
class TopicBoardMigrationTest {

    private static DedicatedPostgresContainer pg;
    private static JdbcTemplate jdbc;

    private static long pairing;
    private static long otherPairing;

    @BeforeAll
    static void migrateAroundOldStates() {

        pg = new DedicatedPostgresContainer();
        pg.start();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword()));
        MigrationChain.SELF_HOSTED.upTo(pg, "135").migrate();
        pairing = pairing("max");
        otherPairing = pairing("noah");
        oldTopic(pairing, "ongoing", "ONGOING", 0);
        oldTopic(pairing, "long term", "LONG_TERM", 1);
        oldTopic(pairing, "done", "DONE", 2);
        oldTopic(pairing, "later ongoing", "ONGOING", 7);
        oldTopic(pairing, "earlier long term", "LONG_TERM", 4);
        oldTopic(otherPairing, "elsewhere", "DONE", 5);
        MigrationChain.SELF_HOSTED.on(pg).migrate();
    }

    @AfterAll
    static void stop() {

        pg.stop();
    }

    @Test
    void ongoingAndLongTermBecomeInProgress() {

        // when
        var actual = jdbc.queryForMap("SELECT string_agg(label || '=' || state, ', ' ORDER BY id) AS states "
                + "FROM mentoring_topic WHERE pairing_id = ?", pairing);

        // then
        assertThat(actual).containsEntry("states", "ongoing=IN_PROGRESS, long term=IN_PROGRESS, done=DONE, "
                + "later ongoing=IN_PROGRESS, earlier long term=IN_PROGRESS");
    }

    @Test
    void positionsAreDensePerColumnInTheirOldOrder() {

        // when
        var actual = slotsOf(pairing);

        // then
        assertThat(actual).containsExactly(slot("ongoing", "IN_PROGRESS", 0), slot("long term", "IN_PROGRESS", 1),
                slot("earlier long term", "IN_PROGRESS", 2), slot("later ongoing", "IN_PROGRESS", 3),
                slot("done", "DONE", 0));
        assertThat(slotsOf(otherPairing)).containsExactly(slot("elsewhere", "DONE", 0));
    }

    private static Map<String, Object> slot(String label, String state, int position) {

        return Map.of("label", label, "state", state, "position", position);
    }

    private static long pairing(String mentee) {

        return jdbc.queryForObject(
                "INSERT INTO mentoring_pairing (organization_id, community_id, mentor_user_id, "
                        + "mentee_user_id, started_at) VALUES ('default', 1, 'mia', ?, now()) RETURNING id",
                Long.class, mentee);
    }

    private static void oldTopic(long pairingId, String label, String state, int position) {

        jdbc.update("INSERT INTO mentoring_topic (organization_id, pairing_id, label, state, \"position\", created_at) "
                + "VALUES ('default', ?, ?, ?, ?, now())", pairingId, label, state, position);
    }

    private static List<Map<String, Object>> slotsOf(long pairingId) {

        return jdbc.queryForList("SELECT label, state, \"position\" FROM mentoring_topic WHERE pairing_id = ? "
                + "ORDER BY state DESC, \"position\"", pairingId);
    }
}
