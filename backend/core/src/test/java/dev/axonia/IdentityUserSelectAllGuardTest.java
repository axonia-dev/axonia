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
 * No production statement selects every column of {@code identity_user}.
 *
 * <p>
 * A migration drops columns of {@code identity_user} while the release before it is still serving, and a
 * server-prepared {@code SELECT *} of that release fails with {@code cached plan must not change result type} on its
 * first use after the drop, once per pooled connection, as a 500 on sign-in and member search. A read of
 * {@code identity_user} names its columns through {@code UserEntity.COLUMNS}, which {@code UserEntityColumnsTest} keeps
 * equal to the mapping.
 *
 * <p>
 * <strong>This is a text scan, not a proof.</strong> It matches {@code SELECT *} or {@code SELECT alias.*} directly
 * followed by {@code FROM identity_user}, in {@code .java} files under {@code src/main}. The same statement built by
 * concatenation, or reaching {@code identity_user} through a join, passes here.
 *
 * <p>
 * Each build unit scans its own {@code src/main}: this class core's, {@code CloudIdentityUserSelectAllGuardTest}
 * cloud's.
 */
class IdentityUserSelectAllGuardTest {

    private static final Path MAIN = Path.of("src", "main");

    private static final Pattern SELECT_ALL = Pattern
            .compile("SELECT\\s+(\\w+\\.)?\\*\\s+FROM\\s+(public\\.)?identity_user\\b", Pattern.CASE_INSENSITIVE);

    /**
     * The four statements that selected every column before {@code UserEntity.COLUMNS}, verbatim, then the aliased,
     * schema-qualified and lower-case shape and two near misses, so the scanner proves itself against real positives.
     */
    private static final String THE_STATEMENTS_AS_THEY_SHIPPED = """
            @Query("SELECT * FROM identity_user WHERE organization_id = :organizationId AND lower(email) = lower(:email) LIMIT 1")
            @Query(\"""
                    SELECT * FROM identity_user
            @Query(\"""
                    SELECT * FROM identity_user
            var sql = new StringBuilder("SELECT * FROM identity_user WHERE organization_id = :organizationId");
            select u.* from public.identity_user u
            SELECT * FROM identity_user_role
            SELECT * FROM member
            """;

    @Test
    void noProductionStatementSelectsEveryColumnOfIdentityUser() throws IOException {

        // then
        assertThat(offenders()).as("""
                statements selecting every column of identity_user. Select UserEntity.COLUMNS instead: a column \
                dropped or added under a release still serving must not reach its entity""").isEmpty();
    }

    @Test
    void theScannerFindsEveryShapeOfTheStatementItExistsToPrevent() {

        // when
        var actual = matchesIn(THE_STATEMENTS_AS_THEY_SHIPPED);

        // then
        assertThat(actual).containsExactly("SELECT * FROM identity_user", "SELECT * FROM identity_user",
                "SELECT * FROM identity_user", "SELECT * FROM identity_user", "select u.* from public.identity_user");
    }

    @Test
    void theScannerReadsTheSourceTreeItClaimsTo() throws IOException {

        // when
        var actual = scannedFiles();

        // then
        assertThat(actual).isNotEmpty();
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

        return SELECT_ALL.matcher(source).results().map(result -> result.group()).toList();
    }

    private List<Path> scannedFiles() throws IOException {

        try (Stream<Path> tree = Files.walk(MAIN)) {
            return tree.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted().toList();
        }
    }
}
