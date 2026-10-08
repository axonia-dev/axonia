package dev.axonia.selfhosted.internal;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import dev.axonia.DedicatedPostgresContainer;
import dev.axonia.SelfHostedMigrationChain;
import dev.axonia.selfhosted.SelfHostedDatabase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The installation's record on a database of its own, migrated by the installation's chain, read and written as the
 * application's runtime role, so the grants and the absence of row-level security are what is tested.
 */
class SelfHostedSettingsTest {

    private static final DedicatedPostgresContainer PG = new DedicatedPostgresContainer(SelfHostedDatabase.IMAGE);

    private static final Instant FOUNDED_AT = Instant.parse("2026-10-02T10:15:30.123Z");

    private static DriverManagerDataSource application;

    private SelfHostedSettings sut;

    @BeforeAll
    static void migrate() throws SQLException {

        PG.start();
        SelfHostedMigrationChain.INSTALLATION.on(PG).migrate();
        application = new DriverManagerDataSource(PG.getJdbcUrl(), "axonia_app", "axonia_app");
        asOwner("INSERT INTO organization (id, name, kind, created_at, updated_at)"
                + " VALUES ('acme', 'Acme', 'ENTERPRISE', now(), now())");
    }

    @AfterAll
    static void stop() {

        PG.stop();
    }

    @BeforeEach
    void setUp() {

        sut = new SelfHostedSettings(new NamedParameterJdbcTemplate(application));
    }

    @AfterEach
    void forget() throws SQLException {

        asOwner("DELETE FROM selfhosted_settings");
    }

    @Test
    void anInstallationThatFoundedNothingHasNoRecord() {

        assertThat(sut.find()).isEmpty();
    }

    @Test
    void readsBackWhatItRecordedToTheMillisecond() {

        // given
        var founded = new SelfHostedSettings.Founded("acme", "oid", FOUNDED_AT);

        // when
        sut.record(founded);

        // then
        assertThat(sut.find()).contains(founded);
    }

    @Test
    void recordsOneInstallationOnly() {

        // given
        sut.record(new SelfHostedSettings.Founded("acme", "sub", FOUNDED_AT));

        // when / then
        assertThatThrownBy(() -> sut.record(new SelfHostedSettings.Founded("acme", "oid", FOUNDED_AT)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void theRuntimeRoleCannotChangeOrForgetTheRecord() throws SQLException {

        // given
        sut.record(new SelfHostedSettings.Founded("acme", "sub", FOUNDED_AT));

        try (var connection = application.getConnection(); var statement = connection.createStatement()) {
            // when / then
            assertThatThrownBy(() -> statement.execute("UPDATE selfhosted_settings SET subject_claim = 'oid'"))
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> statement.execute("DELETE FROM selfhosted_settings"))
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    void theRuntimeRoleMayReadAndRecordOnlyEvenWhereTheBaselinesDefaultPrivilegesDoNotReach() throws SQLException {

        // given
        var database = new DedicatedPostgresContainer(SelfHostedDatabase.IMAGE);
        database.start();
        try {
            SelfHostedMigrationChain.INSTALLATION.upTo(database, "119").migrate();
            asOwner(database, "ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM axonia_app");

            // when
            SelfHostedMigrationChain.INSTALLATION.on(database).migrate();

            // then
            assertThat(privilegesOfTheRuntimeRole(database)).containsExactly("SELECT", "INSERT");
        } finally {
            database.stop();
        }
    }

    @Test
    void theRecordIsReadWhateverOrganizationTheConnectionIsScopedTo() throws SQLException {

        // given
        sut.record(new SelfHostedSettings.Founded("acme", "sub", FOUNDED_AT));

        try (var connection = application.getConnection(); var statement = connection.createStatement()) {
            statement.execute("SET app.current_org = 'globex'");

            // when
            try (var rows = statement.executeQuery("SELECT organization_id FROM selfhosted_settings")) {

                // then
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("acme");
            }
        }
    }

    @Test
    void theTenantDiscoveryRoleCannotReadTheRecord() throws SQLException {

        try (var connection = DriverManager.getConnection(PG.getJdbcUrl(), "axonia_communication_read",
                "communication_read"); var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeQuery("SELECT * FROM selfhosted_settings"))
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    void theFoundingLockIsHeldUntilTheTransactionEnds() {

        // given
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(application));

        // when / then
        transactions.executeWithoutResult(status -> {
            sut.serializeFounding();
            assertThat(anotherConnectionTakesTheFoundingLock()).isFalse();
        });
        assertThat(anotherConnectionTakesTheFoundingLock()).isTrue();
    }

    private static boolean anotherConnectionTakesTheFoundingLock() {

        try (var connection = DriverManager.getConnection(PG.getJdbcUrl(), "axonia_app", "axonia_app");
                var statement = connection.createStatement();
                var rows = statement.executeQuery(
                        "SELECT pg_try_advisory_lock(hashtextextended('axonia.selfhosted.founding', 0))")) {
            rows.next();
            return rows.getBoolean(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> privilegesOfTheRuntimeRole(DedicatedPostgresContainer database) throws SQLException {

        try (var connection = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(),
                database.getPassword());
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT privilege FROM unnest(ARRAY['SELECT', 'INSERT', 'UPDATE',"
                        + " 'DELETE', 'TRUNCATE', 'REFERENCES', 'TRIGGER']) WITH ORDINALITY AS p(privilege, position)"
                        + " WHERE has_table_privilege('axonia_app', 'selfhosted_settings', privilege)"
                        + " ORDER BY position")) {
            var privileges = new ArrayList<String>();
            while (rows.next()) {
                privileges.add(rows.getString(1));
            }
            return privileges;
        }
    }

    private static void asOwner(String sql) throws SQLException {

        asOwner(PG, sql);
    }

    private static void asOwner(DedicatedPostgresContainer database, String sql) throws SQLException {

        try (var connection = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(),
                database.getPassword()); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
