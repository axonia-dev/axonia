package dev.axonia;

import java.io.IOException;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the load-bearing {@code TC_DAEMON=true} on the test datasource URL. Without it the jdbc:tc driver stops the
 * shared Postgres container the moment its open-connection count hits zero; combined with {@code minimum-idle=0}, an
 * idle window then tears the container down and the next cached @DataJdbcTest context reconnects to an empty schema,
 * failing every INSERT with BadSqlGrammar. This tripwire fails fast and locally if the flag is ever dropped, instead of
 * surfacing as a flaky, order-dependent CI failure.
 */
class JdbcTcDaemonModeTest {

    @Test
    void sharedContainerUrlRunsInDaemonMode() throws IOException {
        var properties = new Properties();
        try (var in = new ClassPathResource("axonia-core-test.properties").getInputStream()) {
            properties.load(in);
        }

        assertThat(properties.getProperty("spring.datasource.url"))
                .as("jdbc:tc container must stay alive across zero-connection windows").contains("TC_DAEMON=true");
    }
}
