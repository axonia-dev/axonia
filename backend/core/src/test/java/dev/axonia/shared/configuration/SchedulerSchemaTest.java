package dev.axonia.shared.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import dev.axonia.CoreSpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quartz's job store, as the migrated database holds it: its own schema, readable and writable by the application role,
 * and outside row-level security because it holds no tenant data.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
class SchedulerSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void quartzTablesLiveInTheSchedulerSchema() {

        var tables = jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'scheduler' ORDER BY tablename", String.class);

        assertThat(tables).containsExactly("qrtz_blob_triggers", "qrtz_calendars", "qrtz_cron_triggers",
                "qrtz_fired_triggers", "qrtz_job_details", "qrtz_locks", "qrtz_paused_trigger_grps",
                "qrtz_scheduler_state", "qrtz_simple_triggers", "qrtz_simprop_triggers", "qrtz_triggers");
    }

    @Test
    void theApplicationRoleCanReadAndWriteThemButNotAlterThem() {

        var privileges = jdbc.queryForList("""
                SELECT DISTINCT privilege_type FROM information_schema.role_table_grants
                 WHERE table_schema = 'scheduler' AND grantee = 'axonia_app' ORDER BY privilege_type
                """, String.class);

        assertThat(privileges).containsExactly("DELETE", "INSERT", "SELECT", "UPDATE");
    }

    @Test
    void noSchedulerTableCarriesRowLevelSecurity() {

        var withRls = jdbc.queryForList("""
                SELECT c.relname FROM pg_class c
                 WHERE c.relnamespace = 'scheduler'::regnamespace AND c.relrowsecurity
                """, String.class);

        assertThat(withRls).isEmpty();
    }

    @Test
    void theLockRowsOfTheApplicationSchedulerExistBeforeAnyInstanceStarts() {

        var locks = jdbc.queryForList(
                "SELECT lock_name FROM scheduler.qrtz_locks WHERE sched_name = 'axonia' ORDER BY lock_name",
                String.class);

        assertThat(locks).containsExactly("STATE_ACCESS", "TRIGGER_ACCESS");
    }
}
