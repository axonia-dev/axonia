package dev.axonia;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import dev.axonia.shared.configuration.PersistenceConfig;
import dev.axonia.shared.configuration.TimeConfig;

@DataJdbcTest
@ActiveProfiles("DEV")
@Import({PersistenceConfig.class, TimeConfig.class})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class JdbcTestSupport {

    /** The live columns of one table, for tests that assert what a table does or does not carry. */
    protected List<String> columnsOf(NamedParameterJdbcTemplate jdbc, String table) {

        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = :table",
                Map.of("table", table), String.class);
    }
}
