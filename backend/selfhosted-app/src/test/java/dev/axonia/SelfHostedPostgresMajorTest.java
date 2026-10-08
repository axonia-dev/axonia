package dev.axonia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.axonia.selfhosted.SelfHostedDatabase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Self-hosted tests run on the Postgres major an installation from this repository runs: the one compose ships and
 * the one the chart's cluster test installs. A schema change that only one major accepts fails here, not at an
 * operator's upgrade. The one exception proves the installation's chain on the oldest major the release supports too.
 */
class SelfHostedPostgresMajorTest {

    private static final Pattern POSTGRES_IMAGE = Pattern.compile("(?m)^\\s*image:\\s*[\"']?(postgres:[^\\s\"']+)");

    private static final Pattern CONTAINER = Pattern.compile("new\\s+DedicatedPostgresContainer\\(\\s*([^)]*?)\\s*\\)");

    private static final Pattern CORE_DATABASE = Pattern.compile("\\b(RlsProbeDatabase|RoleConnectedTestSupport)\\b");

    private static final Set<String> INSTALLATION_IMAGE = Set.of("IMAGE", "SelfHostedDatabase.IMAGE");

    private static final String OLDEST_SUPPORTED_RUN = "SelfHostedMigrationTest.java starts "
            + "DedicatedPostgresContainer.OLDEST_SUPPORTED";

    @ParameterizedTest
    @ValueSource(strings = {"deploy/compose.yaml", "deploy/helm/test/fixtures.yaml"})
    void theDeployRunsTheMajorTheSelfHostedTestsRunOn(String file) {

        var images = POSTGRES_IMAGE.matcher(read(RepositoryRoot.file(file))).results().map(match -> match.group(1))
                .toList();

        assertThat(images).as("Postgres images in " + file).isNotEmpty().containsOnly(SelfHostedDatabase.IMAGE);
    }

    @Test
    void noSelfHostedTestStartsAnotherMajorBesideTheOldestSupportedRun() {

        try (Stream<Path> sources = Files.walk(Path.of("src/test/java"))) {
            var otherMajors = sources.filter(source -> source.toString().endsWith(".java"))
                    .filter(source -> !source.endsWith("SelfHostedPostgresMajorTest.java"))
                    .flatMap(SelfHostedPostgresMajorTest::otherMajorsStartedBy).toList();

            assertThat(otherMajors).as("Self-hosted tests starting a container without SelfHostedDatabase.IMAGE")
                    .containsExactly(OLDEST_SUPPORTED_RUN);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Stream<String> otherMajorsStartedBy(Path source) {

        var text = read(source);
        var name = source.getFileName().toString();
        var containers = CONTAINER.matcher(text).results().map(match -> match.group(1))
                .filter(image -> !INSTALLATION_IMAGE.contains(image))
                .map(image -> image.isEmpty() ? "the default major" : image);
        var coreDatabases = CORE_DATABASE.matcher(text).results().map(match -> match.group(1));
        return Stream.concat(containers, coreDatabases).map(started -> name + " starts " + started);
    }

    private static String read(Path file) {

        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
