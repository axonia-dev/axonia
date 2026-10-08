package dev.axonia;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards that every single-column foreign key has an index leading on its column. Postgres does not index a foreign key
 * automatically, and checks every parent DELETE or key UPDATE by sequential-scanning the child table on the FK column
 * alone if no such index exists. A future migration that adds a foreign key without also indexing it fails this test.
 */
class ForeignKeyIndexGuardTest extends CoreJdbcTestSupport {

    private static final String UNINDEXED_FOREIGN_KEYS = """
            WITH fk AS (
              SELECT c.conrelid AS oid, c.conrelid::regclass::text AS tbl,
                     (SELECT a.attname FROM unnest(c.conkey) k
                        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k LIMIT 1) AS col
                FROM pg_constraint c
               WHERE c.contype = 'f' AND array_length(c.conkey, 1) = 1
            )
            SELECT fk.tbl || '.' || fk.col
              FROM fk
             WHERE NOT EXISTS (
                   SELECT 1 FROM pg_index i
                     JOIN pg_attribute a ON a.attrelid = i.indrelid
                                        AND a.attnum = (i.indkey::int2[])[0]
                    WHERE i.indrelid = fk.oid AND a.attname = fk.col)
             ORDER BY 1
            """;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void everySingleColumnForeignKeyHasAnIndexLeadingOnItsColumn() {

        List<String> unindexed = jdbc.queryForList(UNINDEXED_FOREIGN_KEYS, String.class);
        assertThat(unindexed).as("each listed table.column is a single-column foreign key with no index leading on it; "
                + "add CREATE INDEX ON <table> (<column>, ...) in a new Flyway migration so Postgres "
                + "does not sequential-scan the child table on every parent delete or key update").isEmpty();
    }
}
