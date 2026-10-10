package dev.axonia;

import java.sql.SQLException;

import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A test's own Postgres, ready only once this JVM connects: under colima the host port forward can lag the readiness
 * log line. The shared {@code jdbc:tc} container already connects through the retrying {@link #createConnection}.
 */
public final class DedicatedPostgresContainer extends PostgreSQLContainer {

    /** The major core's and the hosted edition's tests run on: the oldest one an edition runs in production. */
    public static final String OLDEST_SUPPORTED = "postgres:18";

    private static final int HOST_CONNECTION_TIMEOUT_SECONDS = 30;

    /**
     * Bounds every wait on the server. A colima port forward can accept the connection and then never answer, and with
     * no timeout the driver would block on that read for good, hanging the whole fork. The socket bound sits far above
     * the slowest statement a test runs on purpose.
     */
    static final String LOGIN_TIMEOUT_SECONDS = "30";

    static final String SOCKET_TIMEOUT_SECONDS = "300";

    public DedicatedPostgresContainer() {
        this(OLDEST_SUPPORTED);
    }

    public DedicatedPostgresContainer(String image) {
        super(image);
        withConnectTimeoutSeconds(HOST_CONNECTION_TIMEOUT_SECONDS);
        withUrlParam("loginTimeout", LOGIN_TIMEOUT_SECONDS);
        withUrlParam("socketTimeout", SOCKET_TIMEOUT_SECONDS);
    }

    @Override
    protected void waitUntilContainerStarted() {
        super.waitUntilContainerStarted();
        try {
            createConnection("").close();
        } catch (SQLException e) {
            throw new IllegalStateException("Postgres logged ready but %s refused host connections for %ds"
                    .formatted(getJdbcUrl(), HOST_CONNECTION_TIMEOUT_SECONDS), e);
        }
    }
}
