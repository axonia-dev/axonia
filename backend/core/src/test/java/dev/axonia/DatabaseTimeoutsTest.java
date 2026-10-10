package dev.axonia;

import java.io.IOException;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.JdbcDatabaseContainer;

import static org.assertj.core.api.Assertions.assertThat;

/** Every test Postgres connection bounds its waits, so a port forward that never answers fails a test, not the fork. */
class DatabaseTimeoutsTest {

    private static final String BOUNDS = "loginTimeout=" + DedicatedPostgresContainer.LOGIN_TIMEOUT_SECONDS
            + "&socketTimeout=" + DedicatedPostgresContainer.SOCKET_TIMEOUT_SECONDS;

    @Test
    void aDedicatedContainersUrlCarriesBothBounds() throws ReflectiveOperationException {

        var parameters = JdbcDatabaseContainer.class.getDeclaredMethod("constructUrlParameters", String.class,
                String.class);
        parameters.setAccessible(true);
        try (var container = new DedicatedPostgresContainer()) {
            assertThat((String) parameters.invoke(container, "?", "&")).contains("loginTimeout=30")
                    .contains("socketTimeout=300");
        }
    }

    @Test
    void theSharedContainersUrlsCarryTheSameBounds() throws IOException {

        var properties = new Properties();
        try (var in = getClass().getResourceAsStream("/axonia-core-test.properties")) {
            properties.load(in);
        }

        assertThat(properties.getProperty("spring.datasource.url")).endsWith(BOUNDS);
        assertThat(properties.getProperty("axonia.tenant-discovery.datasource.url"))
                .as("the jdbc:tc driver keys its containers by URL, so the two pools must name the same one")
                .isEqualTo(properties.getProperty("spring.datasource.url"));
    }
}
