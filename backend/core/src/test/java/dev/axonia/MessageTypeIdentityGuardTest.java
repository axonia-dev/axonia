package dev.axonia;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import dev.axonia.communication.api.MessageType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No source compares a {@link MessageType} constant with {@code ==} or {@code !=}.
 *
 * <p>
 * {@link MessageType} was an enum, and {@code ==} was its idiom. It is now a record a module of another edition can
 * declare with {@code MessageType.of}, and every production caller hands over a type parsed from a stored or signed
 * code. The open codes parse back to their constants, so {@code ==} on an open type still works; on a contributed type
 * it is a fresh instance, and the comparison silently suppresses the message, drops the panel entry or fails the chat
 * post.
 *
 * <p>
 * <strong>This is a text scan, not a proof.</strong> It matches {@code ==} or {@code !=} with an operand that names a
 * {@code MessageType} constant: one of {@link MessageType}'s own, or a {@code static final MessageType} field declared
 * in the scanned sources, qualified or statically imported. Two variables of the type compared with {@code ==} pass
 * here.
 *
 * <p>
 * Each build unit scans its own {@code src}: this class core's, {@code CloudMessageTypeIdentityGuardTest} cloud's and
 * {@code SelfHostedMessageTypeIdentityGuardTest} selfhosted-app's.
 */
class MessageTypeIdentityGuardTest {

    private static final Path SOURCES = Path.of("src");

    private static final Pattern DECLARED = Pattern.compile("static\\s+final\\s+MessageType\\s+([A-Z][A-Z0-9_]*)\\b");

    /**
     * Three comparisons as production code would write them, a statically imported one, and one on a constant a module
     * of another edition contributes, declared here as such a module would; then near misses the scanner must leave
     * alone.
     */
    private static final String THE_COMPARISONS = """
            final class ContributedMessages { static final MessageType MEMBER_DIGEST = MessageType.of("member_digest"); }
            if (MessageType.SESSION_UPCOMING == type) {
            return MessageType.SESSION_AWAITING_CLOSE == type;
            if (type != MessageType.ORGANIZATION_ROUNDUP) {
            return SESSION_WRAP_UP==type ? a : b;
            verify(port).enqueue(argThat(m -> ContributedMessages.MEMBER_DIGEST == m.type()));
            if (MessageType.SESSION_UPCOMING.equals(type)) {
            assert MessageType.SESSION_UPCOMING.code() == null;
            if (SESSION_UPCOMING_COUNT == 3) {
            """;

    @Test
    void noSourceComparesAMessageTypeConstantByIdentity() throws IOException {

        // then
        assertThat(offenders()).as("""
                MessageType constants compared with == or !=. Use equals: a contributed type parsed from a stored \
                code is a fresh instance""").isEmpty();
    }

    @Test
    void theScannerFindsEveryShapeOfTheComparisonItExistsToPrevent() {

        // when
        var actual = matchesIn(THE_COMPARISONS, constantNames(List.of(THE_COMPARISONS)));

        // then
        assertThat(actual).containsExactly("MessageType.SESSION_UPCOMING ==", "MessageType.SESSION_AWAITING_CLOSE ==",
                "!= MessageType.ORGANIZATION_ROUNDUP", "SESSION_WRAP_UP==", "ContributedMessages.MEMBER_DIGEST ==");
    }

    @Test
    void theScannerReadsTheSourceTreeItClaimsTo() throws IOException {

        // when
        var actual = scannedFiles();

        // then
        assertThat(actual).isNotEmpty();
    }

    private List<String> offenders() throws IOException {

        var sources = scannedFiles();
        var texts = new ArrayList<String>();
        for (var file : sources) {
            texts.add(Files.readString(file));
        }
        var names = constantNames(texts);
        var found = new ArrayList<String>();
        for (var i = 0; i < sources.size(); i++) {
            for (var match : matchesIn(texts.get(i), names)) {
                found.add(sources.get(i) + ": " + match);
            }
        }
        return found;
    }

    private static Set<String> constantNames(List<String> sources) {

        var names = Arrays.stream(MessageType.class.getDeclaredFields())
                .filter(MessageTypeIdentityGuardTest::isConstant).map(Field::getName)
                .collect(Collectors.toCollection(TreeSet::new));
        sources.forEach(source -> DECLARED.matcher(source).results().forEach(result -> names.add(result.group(1))));
        return names;
    }

    private static boolean isConstant(Field field) {

        return Modifier.isStatic(field.getModifiers()) && field.getType() == MessageType.class;
    }

    private static List<String> matchesIn(String source, Set<String> names) {

        var name = "(?:\\w+\\.)*(?:" + String.join("|", names) + ")\\b(?![.(])";
        var comparison = Pattern.compile("\\b" + name + "\\s*[!=]=|[!=]=\\s*" + name);
        return comparison.matcher(source).results().map(result -> result.group()).toList();
    }

    private List<Path> scannedFiles() throws IOException {

        try (Stream<Path> tree = Files.walk(SOURCES)) {
            return tree.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().endsWith("MessageTypeIdentityGuardTest.java"))
                    .sorted().toList();
        }
    }
}
