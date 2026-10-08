package dev.axonia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The image an installation runs: built from the open reactor alone, it runs the Self-hosted application's jar as a
 * numeric user, and keeps uploaded images in a directory that user owns, where compose and the chart mount a volume.
 */
class SelfHostedImageBuildContextTest {

    private static final String DOCKERFILE = "backend/selfhosted-app/Dockerfile";

    private static final String TEXT = read(DOCKERFILE);

    /** The stage the image runs: everything after the last {@code FROM}. */
    private static final String RUNTIME = TEXT.substring(Pattern.compile("(?m)^FROM\\s").matcher(TEXT).results()
            .map(MatchResult::start).reduce((first, second) -> second).orElseThrow());

    /** Paths under {@code backend/}, the build context, that only the Cloud's tree holds. */
    private static final List<Path> CLOUD_PATHS = List.of(Path.of("cloud"), Path.of("cloud/pom.xml"),
            Path.of("cloud/src/main/java/dev/axonia/AxoniaApplication.java"));

    @Test
    void everyPathThePackagedBuildReadsIsCopiedIntoTheImage() throws Exception {

        assertThat(DockerfileBuildContext.violations(DOCKERFILE)).as(
                "%s runs `package -DskipTests` on what it copies, so these reach for files the image does not have.",
                DOCKERFILE).isEmpty();
    }

    @Test
    void runsTheSelfHostedApplicationsJar() throws Exception {

        assertThat(DockerfileBuildContext.jarsTheBuildWrites(DOCKERFILE))
                .containsExactly("selfhosted-app/target/axonia-selfhosted-1.0-SNAPSHOT.jar");
    }

    @Test
    void copiesNothingOfTheCloud() {

        assertThat(TEXT).doesNotContain("cloud/");
    }

    /** What reaches the build is the context its dockerignore lets in, whatever the Dockerfile copies. */
    @Test
    void itsContextLetsInNothingOfTheCloud() {

        var rules = read(DOCKERFILE + ".dockerignore").lines().map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
        var reincluded = rules.stream().filter(rule -> rule.startsWith("!"))
                .map(rule -> rule.substring(1).replaceAll("^/+|/+$", "")).toList();

        assertThat(rules).as("the allowlist first excludes everything").first().isEqualTo("*");
        assertThat(reincluded).isNotEmpty().allSatisfy(pattern -> assertThat(CLOUD_PATHS)
                .noneMatch(FileSystems.getDefault().getPathMatcher("glob:" + pattern)::matches));
    }

    @Test
    void runsAsANumericUserThatIsNotRoot() {

        assertThat(user()).matches("[1-9][0-9]*:[1-9][0-9]*");
    }

    @Test
    void theImageItRunsWorksInApp() {

        assertThat(RUNTIME).containsPattern("(?m)^WORKDIR /app$");
    }

    @Test
    void keepsImagesInADirectoryItsUserOwns() {

        var media = single("^ENV MEDIA_LOCAL_PATH=(\\S+)$");

        assertThat(media).isEqualTo("/var/lib/axonia/media");
        assertThat(RUNTIME).as("the stage the image runs").contains("mkdir -p " + media)
                .contains("chown " + user() + " " + media);
    }

    private static String user() {

        return single("^USER (\\S+)$");
    }

    private static String single(String regex) {

        var matches = Pattern.compile(regex, Pattern.MULTILINE).matcher(TEXT).results().toList();
        assertThat(matches).as(regex).hasSize(1);
        return matches.getFirst().group(1);
    }

    private static String read(String path) {

        try {
            return Files.readString(RepositoryRoot.file(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
