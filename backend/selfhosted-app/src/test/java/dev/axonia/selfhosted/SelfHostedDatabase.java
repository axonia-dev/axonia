package dev.axonia.selfhosted;

import java.sql.DriverManager;
import java.sql.SQLException;

import dev.axonia.DedicatedPostgresContainer;

/**
 * The Self-hosted tests' one Postgres, started once per test JVM and never stopped by a test. The application migrates
 * it as the container owner and connects as its own two roles, so row-level security and column grants hold here as in
 * an installation. The owner helpers bypass both: for seeding, cleaning and counting what a role cannot see, never as
 * proof.
 */
public final class SelfHostedDatabase {

    /** The Postgres major an installation runs; {@code SelfHostedPostgresMajorTest} holds the deploy files to it. */
    public static final String IMAGE = "postgres:18";

    public static final String APP_ROLE = "axonia_app";

    public static final String APP_PASSWORD = "axonia_app";

    public static final String DISCOVERY_ROLE = "axonia_communication_read";

    public static final String DISCOVERY_PASSWORD = "communication_read";

    private static final DedicatedPostgresContainer PG = new DedicatedPostgresContainer(IMAGE);

    static {
        PG.start();
    }

    private SelfHostedDatabase() {
    }

    public static DedicatedPostgresContainer shared() {

        return PG;
    }

    /** Runs one statement as the owner. */
    public static void asOwner(String sql) throws SQLException {

        try (var connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    /** A single count read as the owner, so rows the application's role cannot see are counted too. */
    public static long countAsOwner(String sql) throws SQLException {

        try (var connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
