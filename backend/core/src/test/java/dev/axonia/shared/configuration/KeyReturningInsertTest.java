package dev.axonia.shared.configuration;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import dev.axonia.CoreJdbcTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

/**
 * A warm connection of the running build meeting a migration: pgjdbc prepares an insert on the server from its fifth
 * use on one connection, another connection then changes the table's columns, and the next use of that insert inside a
 * transaction must still succeed. Outside a transaction pgjdbc would re-prepare and retry on its own, so the last
 * insert runs inside one, as every application write does.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class KeyReturningInsertTest extends CoreJdbcTestSupport {

    private static final int PGJDBC_PREPARE_THRESHOLD = 5;

    @Autowired
    private JdbcAggregateOperations aggregates;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void createTheTable() {

        jdbc.execute("CREATE TABLE insert_key_probe (id bigserial PRIMARY KEY, label text NOT NULL, retired text)");
    }

    @AfterEach
    void dropTheTable() {

        jdbc.execute("DROP TABLE IF EXISTS insert_key_probe");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADD COLUMN added integer", "DROP COLUMN retired"})
    void anInsertPreparedOnTheServerSurvivesAColumnChangeFromAnotherConnection(String change) throws SQLException {

        var dataSource = Objects.requireNonNull(jdbc.getDataSource());
        try (var warm = dataSource.getConnection()) {
            TransactionSynchronizationManager.bindResource(dataSource, new ConnectionHolder(warm));
            try {

                // given
                warm.setAutoCommit(true);
                for (var use = 0; use <= PGJDBC_PREPARE_THRESHOLD; use++) {
                    aggregates.insert(new Probe(null, "before"));
                }
                var preparedOnTheWarmConnection = jdbc.queryForList(
                        "SELECT statement FROM pg_prepared_statements WHERE statement LIKE 'INSERT INTO \"insert_key_probe\"%'",
                        String.class);
                var warmBackend = backendOf(warm);
                var otherBackend = changeTheTableFromAnotherConnection(dataSource, change);

                // when
                warm.setAutoCommit(false);
                var actual = aggregates.insert(new Probe(null, "after"));
                warm.commit();

                // then
                assertThat(otherBackend).isNotEqualTo(warmBackend);
                assertThat(preparedOnTheWarmConnection).singleElement(STRING).endsWith("RETURNING \"id\"");
                assertThat(actual.id()).isEqualTo(
                        jdbc.queryForObject("SELECT id FROM insert_key_probe WHERE label = 'after'", Long.class));
            } finally {
                TransactionSynchronizationManager.unbindResource(dataSource);
            }
        }
    }

    private static int changeTheTableFromAnotherConnection(DataSource dataSource, String change) throws SQLException {

        try (var other = dataSource.getConnection(); var statement = other.createStatement()) {
            other.setAutoCommit(true);
            statement.execute("ALTER TABLE insert_key_probe " + change);
            return backendOf(other);
        }
    }

    private static int backendOf(Connection connection) throws SQLException {

        try (var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT pg_backend_pid()")) {
            result.next();
            return result.getInt(1);
        }
    }

    @Table("insert_key_probe")
    record Probe(@Id Long id, String label) {
    }
}
