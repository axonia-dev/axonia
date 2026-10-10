package dev.axonia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import dev.axonia.shared.api.error.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Catching a {@link NotFoundException} is never local. The services that throw it are {@code @Transactional}, so inside
 * a transaction they join the caller's one, and Spring marks that shared transaction rollback-only the moment the
 * exception leaves the proxy - before any {@code catch} here runs. The caller then finishes, commits, and the commit
 * throws {@code UnexpectedRollbackException}. That is how one withdrawn artifact turned every expertise search over its
 * tags into a 500 while every unit test stayed green: mocks carry no transaction, so nothing but a real one can see it.
 *
 * <p>
 * So absence must be modelled as a value at the port - an {@code Optional}, or a map that omits what is missing - and
 * never as an exception the caller quietly absorbs. {@code ActivityTitleReader}, {@code ArtifactTitleReader} and
 * {@code CommunityService.identitiesByIds} are what that looks like.
 *
 * <p>
 * <strong>This is a text scan, not a proof.</strong> It sees the two words {@code catch (NotFoundException} and nothing
 * else: it cannot tell whether a given catch sits inside a transaction, and it is blind to the same mistake made with a
 * supertype ({@code catch (RuntimeException}) or with any other unchecked exception a transactional method throws. It
 * exists to stop the known shape from spreading, not to certify that it is gone.
 *
 * <p>
 * Each build unit scans its own {@code src/main/java}: this class core's, {@code CloudNotFoundSwallowGuardTest}
 * cloud's, where no site predates the rule.
 */
class NotFoundSwallowGuardTest {

    /**
     * The sites that predate the rule, each an ordinary read that will 500 the moment its row is deleted. Shrink this
     * list; never grow it. Owed as of 2026-09-18 - every entry needs the same treatment the expertise ones got.
     */
    private static final Set<String> KNOWN_UNFIXED = Set.of(
            "dev/axonia/bookmarks/adapter/out/community/CommunityAccessAdapter.java",
            "dev/axonia/recognition/adapter/out/community/CommunityAccessAdapter.java",
            "dev/axonia/events/adapter/out/communication/ChatDestinationsAdapter.java",
            "dev/axonia/events/adapter/out/communication/MessageSubjectPolicyAdapter.java",
            "dev/axonia/onboarding/adapter/out/community/MembershipStandingAdapter.java");

    private static final Path MAIN = Path.of("src/main/java");

    @Test
    void noNewCodeSwallowsANotFoundExceptionFromATransactionalRead() {

        assertThat(swallowers()).containsExactlyInAnyOrderElementsOf(knownUnfixed());
    }

    protected Set<String> knownUnfixed() {

        return KNOWN_UNFIXED;
    }

    private List<String> swallowers() {

        try (Stream<Path> files = Files.walk(MAIN)) {
            return files.filter(p -> p.toString().endsWith(".java")).filter(NotFoundSwallowGuardTest::catchesNotFound)
                    .map(p -> MAIN.relativize(p).toString()).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean catchesNotFound(Path file) {

        try {
            return Files.readString(file).contains("catch (NotFoundException");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
