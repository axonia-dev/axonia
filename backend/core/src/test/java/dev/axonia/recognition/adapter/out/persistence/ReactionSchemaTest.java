package dev.axonia.recognition.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

class ReactionSchemaTest extends CoreJdbcTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void bothTablesExistWithRowLevelSecurityEnabled() {

        var secured = jdbc.queryForList(
                "SELECT relname FROM pg_class WHERE relrowsecurity = true AND relname IN (?, ?)", String.class,
                "reaction_definition", "contribution_reaction");

        assertThat(secured).containsExactlyInAnyOrder("reaction_definition", "contribution_reaction");
    }

    @Test
    void oneDefinitionsPlacementsAreReachableWithoutReadingEveryPlacement() {

        var indexes = jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE tablename = ?", String.class,
                "contribution_reaction");

        assertThat(indexes).contains("idx_contribution_reaction_definition");
    }

    // Each case runs in its own test method rather than both in one: @DataJdbcTest wraps a test method in a
    // single transaction, and Postgres aborts that transaction on the first constraint violation, which would
    // make a second insert in the same method fail with "transaction is aborted" rather than exercise the
    // constraint it is meant to prove.

    @Test
    void aPlacementWithNeitherArmSetViolatesTheOneArmCheck() {

        assertThat(insertPlacementReturningError(null, null)).contains("ck_contribution_reaction_one_arm");
    }

    @Test
    void aPlacementWithBothArmsSetViolatesTheOneArmCheck() {

        assertThat(insertPlacementReturningError("🧠", 1L)).contains("ck_contribution_reaction_one_arm");
    }

    private String insertPlacementReturningError(String emoji, Long definitionId) {
        try {
            jdbc.update("""
                    INSERT INTO contribution_reaction (organization_id, community_id, contribution_type,
                        contribution_id, reactor_user_id, emoji, definition_id, created_at, updated_at)
                    VALUES ('org-1', 1, 'SLOT_PRESENTED', 1, 'user-1', ?, ?, ?, ?)
                    """, emoji, definitionId, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
            return "no error";
        } catch (Exception e) {
            return e.getMessage();
        }
    }
}
