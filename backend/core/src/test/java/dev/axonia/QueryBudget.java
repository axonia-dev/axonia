package dev.axonia;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts what a block of application code costs the database, in the two units that matter: connections and
 * statements. A connection checkout costs up to four RLS round trips inside {@code OrganizationAwareDataSource} (a
 * {@code set_config} and a matching {@code RESET} for each of organization and subject, only when present) that this
 * counter does not see - which is exactly why connections are counted separately from statements: a checkout is
 * expensive in a way no statement count can reveal. A statement is a network round trip of its own.
 */
public final class QueryBudget {

    private QueryBudget() {
    }

    public static void assertWithin(int maxConnections, int maxStatements, Runnable block) {

        CountingDataSource.reset();
        block.run();
        var connections = CountingDataSource.connections();
        var statements = CountingDataSource.statements();
        assertThat(connections).as("connection checkouts (each costs up to 4 unseen RLS round trips)")
                .isLessThanOrEqualTo(maxConnections);
        assertThat(statements).as("statements prepared").isLessThanOrEqualTo(maxStatements);
    }
}
