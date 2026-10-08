package dev.axonia.communication;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import dev.axonia.CoreSpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What this module's tables promise the database itself, asserted against a migrated schema rather than inferred from
 * the migration file. Follows {@code BillingSchemaTest}'s recipe, and exists for the same reason: every
 * {@code @DataJdbcTest} in this module connects as the shared container's superuser, which bypasses Row-Level Security
 * by table ownership, so a table shipped with the {@code ENABLE ROW LEVEL SECURITY} line or the isolation policy
 * missing would pass the whole suite green and isolate nothing in production. Reading {@code pg_class} and
 * {@code pg_policies} is what turns that from an assumption into a test.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
class CommunicationSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * The record a message leaves behind when its content expires. It carries an {@code organization_id} and outlives
     * every row it was distilled from, so it is exactly as tenant-scoped as the {@code communication_message} it
     * replaces, and must be secured on the same terms.
     */
    @Test
    void theExpiredMessageRecordIsRowLevelSecuredAndIsolatedByOrganization() {

        assertThat(rowSecurityEnabled("communication_expired_message")).isTrue();
        assertThat(policyNames("communication_expired_message")).contains("organization_isolation");
    }

    @Test
    void theMessageAndItsDeliveriesAreRowLevelSecuredAndIsolatedByOrganization() {

        assertThat(rowSecurityEnabled("communication_message")).isTrue();
        assertThat(policyNames("communication_message")).contains("organization_isolation");
        assertThat(rowSecurityEnabled("communication_delivery")).isTrue();
        assertThat(policyNames("communication_delivery")).contains("organization_isolation");
    }

    private boolean rowSecurityEnabled(String table) {

        var results = jdbc
                .queryForList("SELECT c.relrowsecurity FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE c.relname = ? AND n.nspname = 'public'", Boolean.class, table);
        return !results.isEmpty() && results.get(0);
    }

    private List<String> policyNames(String table) {

        return jdbc.queryForList("SELECT policyname FROM pg_policies WHERE tablename = ? AND schemaname = 'public'",
                String.class, table);
    }
}
