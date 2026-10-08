package dev.axonia;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@CoreSpringBootTest
@ActiveProfiles("DEV")
@Import({CountingDataSourceRegistrar.class, PerformanceSeed.class})
class CountingDataSourceTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void countsOneConnectionAndOneStatementPerSimpleRead() throws Exception {

        CountingDataSource.reset();
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("SELECT 1")) {
            statement.execute();
        }
        assertThat(CountingDataSource.connections()).isEqualTo(1);
        assertThat(CountingDataSource.statements()).isEqualTo(1);
    }
}
