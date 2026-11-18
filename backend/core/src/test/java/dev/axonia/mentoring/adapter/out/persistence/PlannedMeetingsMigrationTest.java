package dev.axonia.mentoring.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import dev.axonia.DedicatedPostgresContainer;
import dev.axonia.MigrationChain;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V142 runs against pairings that hold a next meeting: each becomes one planned meeting, a pairing without one plans
 * none. V143 then drops the pairing's next meeting.
 */
class PlannedMeetingsMigrationTest {

    private static DedicatedPostgresContainer pg;
    private static JdbcTemplate jdbc;

    private static long withNextMeeting;
    private static long withoutNextMeeting;

    @BeforeAll
    static void migrateAroundOldNextMeetings() {

        pg = new DedicatedPostgresContainer();
        pg.start();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword()));
        MigrationChain.SELF_HOSTED.upTo(pg, "141").migrate();
        withNextMeeting = pairing("max");
        withoutNextMeeting = pairing("lea");
        jdbc.update("UPDATE mentoring_pairing SET next_meeting_on = '2026-11-28', next_meeting_set_by = 'max', "
                + "next_meeting_set_at = '2026-11-16T04:00:00Z' WHERE id = ?", withNextMeeting);
        MigrationChain.SELF_HOSTED.on(pg).migrate();
    }

    @AfterAll
    static void stop() {

        pg.stop();
    }

    @Test
    void theNextMeetingBecomesOnePlannedMeeting() {

        // when
        var actual = jdbc.queryForList("SELECT organization_id, meeting_on, planned_by, planned_at "
                + "FROM mentoring_meeting WHERE pairing_id = ?", withNextMeeting);

        // then
        assertThat(actual).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("organization_id", "default").containsEntry("planned_by", "max");
            assertThat(((java.sql.Date) row.get("meeting_on")).toLocalDate()).isEqualTo(LocalDate.parse("2026-11-28"));
            assertThat(((java.sql.Timestamp) row.get("planned_at")).toInstant())
                    .isEqualTo(Instant.parse("2026-11-16T04:00:00Z"));
        });
    }

    @Test
    void aPairingWithoutANextMeetingPlansNone() {

        // when
        var actual = jdbc.queryForList("SELECT id FROM mentoring_meeting WHERE pairing_id = ?", withoutNextMeeting);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void thePairingNoLongerHoldsANextMeeting() {

        // when
        var actual = jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = "
                + "'mentoring_pairing' AND column_name LIKE 'next_meeting%'", String.class);

        // then
        assertThat(actual).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname = " + "'mentoring_pairing_next_meeting_check'",
                Long.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE indexname = " + "'idx_mentoring_pairing_next_meeting'",
                Long.class)).isZero();
    }

    private static long pairing(String mentee) {

        return jdbc.queryForObject("INSERT INTO mentoring_pairing (organization_id, community_id, mentor_user_id, "
                + "mentee_user_id, started_at) VALUES ('default', 1, 'mia', ?, '2026-11-01T09:00:00Z') RETURNING id",
                Long.class, mentee);
    }
}
