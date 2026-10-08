package dev.axonia;

import java.sql.DriverManager;
import java.sql.SQLException;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Shared container and per-role datasource setup for every test that connects as a real Postgres role rather than as
 * the Testcontainers owner. The owner bypasses both RLS and column grants by table ownership, so a test seeded and read
 * back through it would pass against a migration that granted nothing at all.
 *
 * <p>
 * Flyway migrates as the container superuser - creates schema, roles, and RLS policies - and each pool below connects
 * as its own least-privilege role, exactly as {@link RlsStackIsolationTest} does.
 *
 * <p>
 * The container is started eagerly in a static initializer, deliberately NOT through {@code @Container} /
 * {@code @Testcontainers}: that JUnit5 extension stops a static-field container after the FIRST concrete subclass's
 * tests finish, which then leaves every other subclass sharing this base unable to reconnect. One container for every
 * test class that extends this base, for the lifetime of the JVM.
 *
 * <p>
 * It names no application: open subclasses carry {@link CoreSpringBootTest} and Cloud ones a stock
 * {@code @SpringBootTest}, since a configuration inherited from {@link CoreSpringBootTest} cannot be reset.
 */
@ActiveProfiles("DEV")
public abstract class RoleConnectedTestSupport {

    protected static final String COMMUNICATION_READ = "axonia_communication_read";

    protected static final DedicatedPostgresContainer PG = new DedicatedPostgresContainer();

    static {
        PG.start();
    }

    @DynamicPropertySource
    static void dataSourceProps(DynamicPropertyRegistry registry) {
        // Flyway migrates as the superuser - creates schema, axonia_app role, and RLS policies.
        registry.add("spring.flyway.url", PG::getJdbcUrl);
        registry.add("spring.flyway.user", PG::getUsername);
        registry.add("spring.flyway.password", PG::getPassword);

        // App datasource connects as the least-privilege role, subject to RLS.
        // Override the jdbc:tc scheme and TC driver set in application-DEV.properties.
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "axonia_app");
        registry.add("spring.datasource.password", () -> "axonia_app");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // The tenant-discovery pool connects as axonia_communication_read against this same dedicated container.
        // Left on the shared jdbc:tc connection it would own the tables and bypass both RLS and column privileges.
        registry.add("axonia.tenant-discovery.datasource.url", PG::getJdbcUrl);
        registry.add("axonia.tenant-discovery.datasource.username", () -> "axonia_communication_read");
        registry.add("axonia.tenant-discovery.datasource.password", () -> "communication_read");
        registry.add("axonia.tenant-discovery.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    protected static long countAs(String role, String sql) throws SQLException {
        return countAs(role, passwordFor(role), sql);
    }

    protected static long countAs(String role, String password, String sql) throws SQLException {
        try (var connection = DriverManager.getConnection(PG.getJdbcUrl(), role, password);
                var statement = connection.prepareStatement(sql);
                var rows = statement.executeQuery()) {
            rows.next();
            return rows.getLong(1);
        }
    }

    /** Runs one statement as the container owner, which bypasses RLS: for seeding and cleaning, never for proof. */
    protected static void asOwner(String sql) throws SQLException {
        try (var connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    /** A single count read as the container owner, so what the app role cannot see is counted too. */
    protected static long countAsOwner(String sql) throws SQLException {
        try (var connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static String passwordFor(String role) {
        if (COMMUNICATION_READ.equals(role)) {
            return "communication_read";
        }
        throw new IllegalArgumentException("No known password for role " + role);
    }
}
