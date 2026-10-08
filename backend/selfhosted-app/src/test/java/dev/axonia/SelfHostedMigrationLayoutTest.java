package dev.axonia;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The migrations only the Self-hosted edition runs continue the one version sequence above the baseline. */
class SelfHostedMigrationLayoutTest {

    @Test
    void everySelfHostedMigrationIsNumberedAboveTheBaselineAndNoneIsUsedTwice() {

        // given
        var baseline = MigrationFiles.versionsIn("db/selfhosted-baseline").getFirst();
        var own = MigrationFiles.versionsIn("db/selfhosted");

        // then
        assertThat(own).isNotEmpty().allSatisfy(version -> assertThat(version).isGreaterThan(baseline));
        assertThat(Stream.concat(MigrationFiles.versionsIn("db/migration").stream(), own.stream()).toList())
                .as("versions used in both db/migration and db/selfhosted").doesNotHaveDuplicates();
    }
}
