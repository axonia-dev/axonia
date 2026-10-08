package dev.axonia;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every test of this build unit that starts its own Postgres goes through {@link DedicatedPostgresContainer}.
 * {@code CloudDedicatedPostgresContainerGuardTest} holds cloud's tests to the same rule.
 */
class DedicatedPostgresContainerGuardTest {

    private static final Path TEST_SOURCES = Path.of("src", "test", "java");

    private static final Pattern RAW_CONTAINER = Pattern.compile("new\\s+PostgreSQLContainer\\b");

    @Test
    void noTestStartsAPostgresContainerThatSkipsTheHostConnectionWait() throws IOException {

        assertThat(filesMatching(RAW_CONTAINER)).as(
                "A raw PostgreSQLContainer is ready before the host port forward is: use DedicatedPostgresContainer")
                .isEmpty();
    }

    @Test
    void theScannerReadsTheTestSourceTree() throws IOException {

        assertThat(filesMatching(Pattern.compile("new\\s+DedicatedPostgresContainer\\(")))
                .contains(TEST_SOURCES.resolve(calibrationTarget()));
    }

    protected Path calibrationTarget() {

        return Path.of("dev", "axonia", "RlsProbeDatabase.java");
    }

    private List<Path> filesMatching(Pattern pattern) throws IOException {

        try (Stream<Path> tree = Files.walk(TEST_SOURCES)) {
            return tree.filter(path -> path.toString().endsWith(".java")).filter(path -> {
                try {
                    return pattern.matcher(Files.readString(path)).find();
                } catch (IOException e) {
                    throw new IllegalStateException("could not read " + path, e);
                }
            }).toList();
        }
    }
}
