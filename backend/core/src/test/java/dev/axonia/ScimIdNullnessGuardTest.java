package dev.axonia;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A structural guard against one recurring, silent defect: reading a {@code scim_id}'s nullness as if it meant
 * something.
 *
 * <p>
 * It never did mean what it was read to mean, and since V41 it cannot mean anything at all:
 * {@code identity_user.scim_id
 * } is {@code NOT NULL}, so every user carries one whatever created them. Code that still asks whether it is absent
 * therefore takes a branch that can no longer be reached, or - worse, and this is what shipped twice - stands in for a
 * question it was never a synonym for. It stood in for "was this person pushed by SCIM", which is now
 * {@code identity_user.arrival}; and it stood in for "can this person appear in {@code directory_group_member}", which
 * is now simply whether a membership row exists. Both readings denied a real member every role bound to their directory
 * group.
 *
 * <p>
 * Grep-and-review missed it once already: the branch that removed one occurrence introduced another, in the same
 * review. This is the check that does not get tired.
 *
 * <p>
 * <strong>What it deliberately does not cover.</strong> It scans {@code .java} and {@code .sql} under {@code src/main},
 * and exempts the Flyway migrations in every folder under {@code db}, generated baseline and development seed included,
 * which must talk about the pre-{@code NOT NULL} world in order to migrate out of it. It matches the written shapes
 * above and no others: a nullness test spelled {@code Objects.isNull(...)}, or hidden behind a variable, passes here.
 * It is a net with a known mesh, not a proof.
 *
 * <p>
 * Each build unit scans its own {@code src/main}: this class core's, {@code CloudScimIdNullnessGuardTest} cloud's.
 */
class ScimIdNullnessGuardTest {

    private static final Path MAIN = Path.of("src", "main");
    private static final List<Path> FLYWAY_MIGRATIONS = List.of(Path.of("src", "main", "resources", "db"),
            Path.of("src", "main", "java", "db"));
    private static final List<String> SCANNED_SUFFIXES = List.of(".java", ".sql");

    private static final List<Pattern> NULLNESS_TESTS = List.of(
            Pattern.compile("\\w*scim_id\\s+is\\s+(?:not\\s+)?null", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\w*[Ss]cimId\\(\\)\\s*[!=]=\\s*null|null\\s*[!=]=\\s*\\w*[Ss]cimId\\(\\)"));

    /**
     * The guards exactly as they stood at {@code 07f2c0ab}, before this rule existed, kept verbatim so the scanner has
     * real positives to prove itself against rather than only the absence of one.
     */
    private static final String THE_GUARDS_AS_THEY_SHIPPED = """
            if (userOpt.isEmpty() || userOpt.get().getScimId() == null) {
            if (header.scimId() == null)
            static final String ARRIVAL_EXPRESSION = "CASE WHEN u.scim_id IS NOT NULL THEN 'SYNCED' ELSE 'FIRST_SIGN_IN' END";
            OR (rb.subject_type = 'GROUP' AND u.scim_id IS NOT NULL AND rb.subject_id IN (
            """;

    @Test
    void noProductionCodeAsksWhetherAScimIdIsAbsent() throws IOException {

        assertThat(offenders()).as("""
                A scim_id is never absent: identity_user.scim_id is NOT NULL since V41, and directory membership is \
                keyed on it. Asking whether one is null is either dead code or, as it was twice before, a stand-in \
                for a different question. Read identity_user.arrival for how the person arrived, and the presence of \
                a directory_group_member row for whether they are in a group.""").isEmpty();
    }

    /** Calibration: the scanner finds every shape the defect actually took, not some of them. */
    @Test
    void theScannerFindsEveryShapeOfTheDefectItExistsToPrevent() {

        assertThat(matchesIn(THE_GUARDS_AS_THEY_SHIPPED)).containsExactly("scim_id IS NOT NULL", "scim_id IS NOT NULL",
                "getScimId() == null", "scimId() == null");
    }

    @Test
    void theScannerReadsTheSourceTreeItClaimsTo() throws IOException {

        assertThat(scannedFiles()).isNotEmpty().noneMatch(ScimIdNullnessGuardTest::isAMigration);
    }

    private static boolean isAMigration(Path file) {

        return FLYWAY_MIGRATIONS.stream().anyMatch(file::startsWith);
    }

    private List<String> offenders() throws IOException {

        var found = new ArrayList<String>();
        for (var file : scannedFiles()) {
            for (var match : matchesIn(Files.readString(file))) {
                found.add(file + ": " + match);
            }
        }
        return found;
    }

    private static List<String> matchesIn(String source) {

        return NULLNESS_TESTS.stream().flatMap(pattern -> pattern.matcher(source).results())
                .map(result -> result.group()).toList();
    }

    private List<Path> scannedFiles() throws IOException {

        try (Stream<Path> tree = Files.walk(MAIN)) {
            return tree.filter(Files::isRegularFile).filter(path -> !isAMigration(path))
                    .filter(path -> SCANNED_SUFFIXES.stream().anyMatch(path.getFileName().toString()::endsWith))
                    .sorted().toList();
        }
    }
}
