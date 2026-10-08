package dev.axonia;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.axonia.selfhosted.SelfHostedDatabase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Self-hosted edition's database from nothing: the baseline, the migrations both editions run, then the Self-hosted
 * edition's own. Flyway is run directly, since the application itself does not start before its adapters exist.
 */
class SelfHostedMigrationTest {

    private static final DedicatedPostgresContainer PG = new DedicatedPostgresContainer(SelfHostedDatabase.IMAGE);

    // What Flyway reads from the first migration; editing the baseline refuses the start of every existing install.
    private static final int BASELINE_CHECKSUM = 1610497026;

    // Every character that ends or nests a literal in the baseline's role block.
    private static final String AN_OPERATORS_PASSWORD = "it's \"quoted\" $$ $tag$ \\ ';-- end";

    private static Flyway flyway;

    private static MigrateResult migration;

    @BeforeAll
    static void migrate() {

        PG.start();
        flyway = SelfHostedMigrationChain.INSTALLATION.on(PG);
        migration = flyway.migrate();
    }

    @AfterAll
    static void stop() {

        PG.stop();
    }

    @Test
    void theSelfHostedChainMigratesAnEmptyDatabase() {

        assertThat(migration.success).isTrue();
        assertThat(flyway.info().applied()[0].getDescription()).isEqualTo("selfhosted baseline");
        assertThat(flyway.info().applied()[0].getVersion().getVersion()).isEqualTo("119");
        assertThat(flyway.info().applied()[0].getChecksum())
                .as("the Self-hosted baseline is a public contract: "
                        + "an existing install refuses to start on any edit of it, a comment or a space included")
                .isEqualTo(BASELINE_CHECKSUM);
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    /**
     * An operator's own database may run any major the release supports: the migrations only this edition runs are
     * proven on the oldest supported major too, not only on the one compose ships.
     */
    @Test
    void theSelfHostedChainMigratesAnEmptyDatabaseOnTheOldestSupportedMajor() {

        // given
        var database = new DedicatedPostgresContainer(DedicatedPostgresContainer.OLDEST_SUPPORTED);
        database.start();
        try {
            var oldest = SelfHostedMigrationChain.INSTALLATION.on(database);

            // when
            var result = oldest.migrate();

            // then
            assertThat(result.success).isTrue();
            assertThat(oldest.info().pending()).isEmpty();
        } finally {
            database.stop();
        }
    }

    @Test
    void theTwoRolesTakeWhateverPasswordAnOperatorChooses() throws SQLException {

        // given
        var database = new DedicatedPostgresContainer(SelfHostedDatabase.IMAGE);
        database.start();
        try {
            // when
            SelfHostedMigrationChain.INSTALLATION.withPlaceholders(
                    Map.of("app-password", AN_OPERATORS_PASSWORD, "communication-read-password", AN_OPERATORS_PASSWORD))
                    .on(database).migrate();

            // then
            try (var app = DriverManager.getConnection(database.getJdbcUrl(), "axonia_app", AN_OPERATORS_PASSWORD);
                    var discovery = DriverManager.getConnection(database.getJdbcUrl(), "axonia_communication_read",
                            AN_OPERATORS_PASSWORD)) {
                assertThat(app.isValid(5)).isTrue();
                assertThat(discovery.isValid(5)).isTrue();
            }
        } finally {
            database.stop();
        }
    }

    @Test
    void theApplicationNamesTheChainThisTestMigrates() throws IOException {

        // given
        var properties = new Properties();
        try (var in = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            properties.load(in);
        }

        // then
        assertThat(properties.getProperty("spring.flyway.locations"))
                .isEqualTo(SelfHostedMigrationChain.INSTALLATION.locationsProperty());
    }

    @Test
    void theChainCreatesTheTwoRolesTheApplicationConnectsAsAndNoOther() throws SQLException {

        // when
        var roles = new ArrayList<String>();
        try (var connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                var statement = connection.createStatement();
                var rows = statement
                        .executeQuery("SELECT rolname FROM pg_roles WHERE rolname LIKE 'axonia\\_%' ORDER BY 1")) {
            while (rows.next()) {
                roles.add(rows.getString(1));
            }
        }

        // then
        assertThat(roles).containsExactly("axonia_app", "axonia_communication_read");
    }

    @Test
    void theRuntimeRoleWritesAndReadsItsOwnOrganization() throws SQLException {

        try (var connection = DriverManager.getConnection(PG.getJdbcUrl(), "axonia_app", "axonia_app");
                var statement = connection.createStatement()) {
            // given
            statement.execute("SET app.current_org = 'selfhosted-probe'");
            statement.execute("INSERT INTO organization (id, name, kind, created_at, updated_at)"
                    + " VALUES ('selfhosted-probe', 'Probe', 'ENTERPRISE', now(), now())");

            // when
            try (var rows = statement.executeQuery("SELECT count(*) FROM organization")) {
                rows.next();

                // then
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void theTenantDiscoveryRoleReadsThreeColumnsOfOneTable() throws SQLException {

        try (var connection = DriverManager.getConnection(PG.getJdbcUrl(), "axonia_communication_read",
                "communication_read"); var statement = connection.createStatement()) {
            // when
            try (var rows = statement.executeQuery("SELECT id, lifecycle_state, kind FROM organization")) {

                // then
                assertThat(rows.getMetaData().getColumnCount()).isEqualTo(3);
            }
            assertThatThrownBy(() -> statement.executeQuery("SELECT name FROM organization"))
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> statement.executeQuery("SELECT count(*) FROM identity_user"))
                    .hasMessageContaining("permission denied");
        }
    }
}
