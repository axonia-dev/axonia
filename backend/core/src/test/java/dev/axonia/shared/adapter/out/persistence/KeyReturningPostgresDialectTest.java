package dev.axonia.shared.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.data.jdbc.core.dialect.JdbcPostgresDialect;
import org.springframework.data.relational.core.sql.SqlIdentifier;

import static org.assertj.core.api.Assertions.assertThat;

class KeyReturningPostgresDialectTest {

    private final KeyReturningPostgresDialect sut = new KeyReturningPostgresDialect();

    @Test
    void getIdGeneration_namesTheKeyColumnToTheDriver() {

        // when
        var actual = sut.getIdGeneration();

        // then
        assertThat(actual.driverRequiresKeyColumnNames()).isTrue();
        assertThat(actual.getKeyColumnName(SqlIdentifier.quoted("id"))).isEqualTo("id");
    }

    @Test
    void getIdGeneration_keepsThePostgresSequenceQuery() {

        // given
        var sequence = SqlIdentifier.quoted("probe_seq");

        // when
        var actual = sut.getIdGeneration().createSequenceQuery(sequence);

        // then
        assertThat(actual).isEqualTo(JdbcPostgresDialect.INSTANCE.getIdGeneration().createSequenceQuery(sequence))
                .isEqualTo("SELECT nextval('\"probe_seq\"')");
    }
}
