package dev.axonia.installation.adapter.out.persistence;

import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.axonia.DedicatedPostgresContainer;
import dev.axonia.SelfHostedMigrationChain;
import dev.axonia.selfhosted.SelfHostedDatabase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V121 and V122 on a database of their own: the one mail server row, what the application's role may do to it and to
 * the history, and the Owner of an installation founded before the two permissions existed.
 */
class InstallationMigrationTest {

    private static final DedicatedPostgresContainer PG = new DedicatedPostgresContainer(SelfHostedDatabase.IMAGE);

    @BeforeAll
    static void migrateToTheVersionBeforeTheBackfill() throws SQLException {

        PG.start();
        SelfHostedMigrationChain.INSTALLATION.upTo(PG, "121").migrate();
        asOwner("""
                INSERT INTO role (id, organization_id, name, created_at, updated_at, version, system, slug)
                VALUES (9001, 'acme', 'Owner', now(), now(), 0, true, 'owner'),
                       (9002, 'acme', 'Organizer', now(), now(), 0, true, 'organizer'),
                       (9003, 'other', 'Owner', now(), now(), 0, true, 'owner');
                INSERT INTO role_permission (role_id, permission)
                VALUES (9001, 'organization:read'), (9003, 'mail_server:read');
                """);
        SelfHostedMigrationChain.INSTALLATION.on(PG).migrate();
    }

    @AfterAll
    static void stop() {

        PG.stop();
    }

    @Test
    void givesEveryOwnerBothPermissionsOnceAndNoOtherRoleAny() throws SQLException {

        assertThat(
                count("SELECT count(*) FROM role_permission WHERE role_id = 9001 AND permission LIKE 'mail_server:%'"))
                .isEqualTo(2);
        assertThat(
                count("SELECT count(*) FROM role_permission WHERE role_id = 9003 AND permission LIKE 'mail_server:%'"))
                .isEqualTo(2);
        assertThat(count("SELECT count(*) FROM role_permission WHERE role_id = 9002")).isZero();
        assertThat(count("SELECT count(*) FROM role_permission WHERE role_id = 9001")).isEqualTo(3);
    }

    @Test
    void createsTheOneMailServerRowEmptyAtRevisionZero() throws SQLException {

        assertThat(count("SELECT count(*) FROM mail_server_settings WHERE revision = 0 AND host IS NULL"
                + " AND password_secret IS NULL AND tested_fingerprint IS NULL")).isOne();
    }

    @Test
    void theApplicationReadsAndUpdatesTheMailServerAndNeverAddsOrRemovesARow() throws SQLException {

        asApplication("UPDATE mail_server_settings SET from_name = 'Acme' WHERE revision = 0");
        assertThat(count("SELECT count(*) FROM mail_server_settings WHERE from_name = 'Acme'")).isOne();
        assertRefused("INSERT INTO mail_server_settings (installation) VALUES (true)");
        assertRefused("DELETE FROM mail_server_settings");
        asOwner("UPDATE mail_server_settings SET from_name = NULL");
    }

    @Test
    void theApplicationAppendsToTheHistoryAndMayClearOnlyItsActor() throws SQLException {

        asApplication("""
                INSERT INTO installation_settings_change (occurred_at, actor_user_id, integration, changes)
                VALUES (now(), 'migration-actor', 'mail', '[]'::jsonb)
                """);
        asApplication("UPDATE installation_settings_change SET actor_user_id = NULL"
                + " WHERE actor_user_id = 'migration-actor'");
        assertRefused("UPDATE installation_settings_change SET changes = '[]'::jsonb");
        assertRefused("DELETE FROM installation_settings_change");
        assertThat(count("SELECT count(*) FROM installation_settings_change WHERE actor_user_id IS NULL")).isOne();
    }

    @Test
    void theHistoryKnowsOnlyTheMailIntegration() {

        assertThatThrownBy(() -> asOwner("""
                INSERT INTO installation_settings_change (occurred_at, actor_user_id, integration, changes)
                VALUES (now(), 'migration-actor', 'chat', '[]'::jsonb)
                """)).isInstanceOf(SQLException.class)
                .hasMessageContaining("installation_settings_change_integration_check");
    }

    private static void assertRefused(String sql) {

        assertThatThrownBy(() -> asApplication(sql)).isInstanceOf(SQLException.class)
                .hasMessageContaining("permission denied");
    }

    private static long count(String sql) throws SQLException {

        try (var connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static void asOwner(String sql) throws SQLException {

        try (var connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void asApplication(String sql) throws SQLException {

        try (var connection = DriverManager.getConnection(PG.getJdbcUrl(), "axonia_app", "axonia_app");
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
