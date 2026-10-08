package dev.axonia.config;

import org.springframework.data.jdbc.core.dialect.JdbcPostgresDialect;
import org.springframework.data.relational.core.dialect.IdGeneration;
import org.springframework.data.relational.core.sql.SqlIdentifier;

/**
 * Postgres, with an insert that generates its key asking the driver for that key column alone.
 *
 * <p>
 * The stock dialect names no key column, so pgjdbc appends {@code RETURNING *} to the insert. Once pgjdbc has prepared
 * that insert on the server (from its fifth use on a connection), a column added to or dropped from the table by
 * another connection changes its result type, and the next use inside a transaction fails with "cached plan must not
 * change result type". {@code RETURNING "id"} has the same result type whatever else the table holds.
 */
class KeyReturningPostgresDialect extends JdbcPostgresDialect {

    private final IdGeneration idGeneration = new KeyColumnIdGeneration(super.getIdGeneration());

    @Override
    public IdGeneration getIdGeneration() {
        return idGeneration;
    }

    private record KeyColumnIdGeneration(IdGeneration postgres) implements IdGeneration {

        @Override
        public boolean driverRequiresKeyColumnNames() {
            return true;
        }

        @Override
        public String createSequenceQuery(SqlIdentifier sequenceName) {
            return postgres.createSequenceQuery(sequenceName);
        }
    }
}
