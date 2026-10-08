package dev.axonia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A test that migrates its own database names its edition's {@link MigrationChain}, and no test reaches Flyway any
 * other way, so the folders an edition runs are written down once. Comments are not code, so they are not scanned. Each
 * build unit scans its own tests.
 */
class MigrationChainGuardTest {

    private static final Path TESTS = Path.of("src/test/java");

    private static final Path CHAIN = Path.of("core/src/test/java/dev/axonia/MigrationChain.java");

    private static final Path CLOUD_CHAIN = Path.of("cloud/src/test/java/dev/axonia/CloudMigrationChain.java");

    private static final Path SELF_HOSTED_CHAIN = Path
            .of("selfhosted-app/src/test/java/dev/axonia/SelfHostedMigrationChain.java");

    // Each reads the migration folders an edition's own properties file names, to pin its chain to it.
    private static final Set<Path> CHAIN_PROPERTY_READERS = Set.of(
            Path.of("core/src/test/java/dev/axonia/OpenMigrationLayoutTest.java"),
            Path.of("cloud/src/test/java/dev/axonia/CloudConfigurationTest.java"),
            Path.of("selfhosted-app/src/test/java/dev/axonia/SelfHostedMigrationTest.java"),
            Path.of("selfhosted-app/src/test/java/dev/axonia/SelfHostedApplicationPropertiesTest.java"));

    private static final List<Forbidden> FORBIDDEN = List.of(
            new Forbidden("Flyway's configure, through the class, an instance or a static import",
                    "(?:\\.|::)\\s*configure\\b|\\bimport\\s+static\\s+org\\.flywaydb\\.", Set.of(CHAIN)),
            new Forbidden("a Flyway or a Flyway configuration built directly",
                    "\\bnew\\s+(?:[\\w.]+\\.)?(?:Flyway|FluentConfiguration|ClassicConfiguration)\\s*[(<]"
                            + "|\\b(?:Flyway|FluentConfiguration|ClassicConfiguration)\\s*::\\s*new\\b",
                    Set.of()),
            new Forbidden("migration folders named outside a chain", "\\.\\s*locations\\s*\\(", Set.of(CHAIN)),
            new Forbidden("a Spring Boot hook that reconfigures Flyway",
                    "\\bFlyway(?:ConfigurationCustomizer|MigrationStrategy|MigrationInitializer)\\b", Set.of()),
            new Forbidden("a test's own Flyway folders or target version, as a property",
                    "(?i)flyway[._-](?:locations|target)\\b", CHAIN_PROPERTY_READERS),
            new Forbidden("a chain built outside MigrationChain and the editions' chain constants",
                    "\\bnew\\s+(?:[\\w.]+\\.)?MigrationChain\\s*\\(|\\bMigrationChain\\s*::\\s*new\\b",
                    Set.of(CHAIN, CLOUD_CHAIN, SELF_HOSTED_CHAIN)));

    @Test
    void noTestReachesFlywayOutsideAMigrationChain() throws IOException {

        try (var files = Files.walk(TESTS)) {
            // when
            var bypasses = files.filter(file -> file.toString().endsWith(".java")).flatMap(file -> {
                var code = JavaSource.withoutComments(read(file));
                var path = fromBackend(file);
                return FORBIDDEN.stream().filter(rule -> !rule.exempt().contains(path))
                        .filter(rule -> rule.pattern().matcher(code).find()).map(rule -> path + ": " + rule.what());
            }).sorted().toList();

            // then
            assertThat(bypasses).as("tests reaching Flyway outside a migration chain: migrate with "
                    + "MigrationChain.SELF_HOSTED in core, SelfHostedMigrationChain.INSTALLATION in selfhosted-app, "
                    + "or the Cloud's chain in cloud").isEmpty();
        }
    }

    private static Path fromBackend(Path file) {

        return Path.of("").toAbsolutePath().getParent().relativize(file.toAbsolutePath());
    }

    private static String read(Path file) {

        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record Forbidden(String what, Pattern pattern, Set<Path> exempt) {

        Forbidden(String what, String regex, Set<Path> exempt) {

            this(what, Pattern.compile(regex), exempt);
        }
    }
}
