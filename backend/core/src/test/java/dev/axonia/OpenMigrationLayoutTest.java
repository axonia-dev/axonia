package dev.axonia;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The Self-hosted chain is one baseline and, above it, the migrations both editions run. */
class OpenMigrationLayoutTest {

    @Test
    void theBaselineIsOneFile() {

        assertThat(MigrationFiles.versionsIn("db/selfhosted-baseline")).hasSize(1);
    }

    @Test
    void everySharedMigrationIsNumberedAboveTheBaseline() {

        // given
        var baseline = MigrationFiles.versionsIn("db/selfhosted-baseline").getFirst();

        // then
        assertThat(MigrationFiles.versionsIn("db/migration")).doesNotHaveDuplicates()
                .allSatisfy(version -> assertThat(version).isGreaterThan(baseline));
    }

    @Test
    void theOpenTestsMigrateTheSelfHostedChain() throws IOException {

        // given
        var properties = new Properties();
        try (var in = Files.newInputStream(Path.of("src/test/resources/application.properties"))) {
            properties.load(in);
        }

        // then
        assertThat(properties.getProperty("spring.flyway.locations"))
                .isEqualTo(MigrationChain.SELF_HOSTED.locationsProperty());
    }
}
