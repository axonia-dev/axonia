package dev.axonia.mentoring.adapter.out.persistence;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import dev.axonia.DedicatedPostgresContainer;
import dev.axonia.MigrationChain;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V144 runs against pairings and proposals made under the promise of privacy: each pairing asks both people, with
 * neither answered, and accepting an earlier proposal pairs asking too.
 */
class ShowingMigrationTest {

    private static DedicatedPostgresContainer pg;
    private static JdbcTemplate jdbc;

    private static long pairing;
    private static long proposal;

    @BeforeAll
    static void migrateAroundEarlierPairings() {

        pg = new DedicatedPostgresContainer();
        pg.start();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword()));
        MigrationChain.SELF_HOSTED.upTo(pg, "143").migrate();
        pairing = jdbc.queryForObject("INSERT INTO mentoring_pairing (organization_id, community_id, mentor_user_id, "
                + "mentee_user_id, started_at) VALUES ('default', 1, 'mia', 'max', '2026-11-01T09:00:00Z') RETURNING id",
                Long.class);
        proposal = jdbc.queryForObject("INSERT INTO mentoring_proposal (organization_id, community_id, "
                + "mentor_user_id, mentee_user_id, proposed_by, topics, message, status, created_at) VALUES "
                + "('default', 1, 'mia', 'lea', 'MENTEE', ARRAY['Kafka'], 'Hello', 'OPEN', '2026-11-10T09:00:00Z') "
                + "RETURNING id", Long.class);
        MigrationChain.SELF_HOSTED.on(pg).migrate();
    }

    @AfterAll
    static void stop() {

        pg.stop();
    }

    @Test
    void anEarlierPairingAsksBothPeople() {

        // when
        var actual = jdbc.queryForMap(
                "SELECT visibility, mentor_said_yes, mentee_said_yes FROM mentoring_pairing WHERE id = ?", pairing);

        // then
        assertThat(actual).containsEntry("visibility", "ASKING").containsEntry("mentor_said_yes", false)
                .containsEntry("mentee_said_yes", false);
    }

    @Test
    void anEarlierProposalPairsAsking() {

        // when
        var actual = jdbc.queryForObject("SELECT pairs_shown FROM mentoring_proposal WHERE id = ?", Boolean.class,
                proposal);

        // then
        assertThat(actual).isFalse();
    }

    @Test
    void aNewRowStatesItsShowing() {

        // when
        var actual = jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name IN "
                + "('mentoring_pairing', 'mentoring_proposal') AND column_name IN ('visibility', 'pairs_shown') "
                + "AND column_default IS NOT NULL", String.class);

        // then
        assertThat(actual).isEmpty();
    }
}
