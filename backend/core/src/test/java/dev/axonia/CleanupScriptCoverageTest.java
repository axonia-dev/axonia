package dev.axonia;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code /sql/cleanup.sql} empties every table of the open schema, each named once, so a committing test never leaves a
 * row behind for the next class, whichever table it wrote. Only {@link CleanupScript#FLYWAY_HISTORY} is kept.
 */
class CleanupScriptCoverageTest extends CoreJdbcTestSupport {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Test
    void theCleanupNamesEveryTableOfTheOpenSchemaOnce() {

        // when
        var named = CleanupScript.tablesNamedBy("/sql/cleanup.sql");

        // then
        assertThat(named)
                .as("tables /sql/cleanup.sql names, against the open schema's: name a new table in it, "
                        + "once, and drop a table that is gone")
                .isNotEmpty().containsExactlyInAnyOrderElementsOf(CleanupScript.liveTables(jdbc));
    }
}
