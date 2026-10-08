package dev.axonia.communication.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A structural guard against one specific, silent Thymeleaf bug: a condition written on the same element as a fragment
 * inclusion never runs. Inclusion is order 1 in Thymeleaf's attribute precedence and conditional evaluation is order 3,
 * and {@code th:replace} replaces the host element and stops processing its remaining attributes the moment it fires -
 * so the element renders unconditionally and every expression in its fragment parameters is evaluated regardless.
 *
 * <p>
 * It shipped through a green gate once already: four rows of the close-out nudge carried a {@code th:if} beside their
 * {@code th:replace}, so every nudge rendered both arm chips, both talk-count chips and both copies of its body
 * paragraph, and the no-community path rendered the literal {@code null}. Nothing caught it because every assertion on
 * those rows was a positive one. Rendering tests can only ever pin the cases someone thought to write; this pins the
 * shape, across every template at once.
 *
 * <p>
 * The two safe placements remain available and are not flagged: a wrapping {@code <th:block th:if=...>} around the
 * including element, or a {@code th:if} on the fragment's own definition (what {@code statStrip}, {@code
 * talkCountCell} and {@code nudgeStrip} do).
 *
 * <p>
 * Each build unit lists exactly the templates of its own directory: this class core's,
 * {@code CloudMailTemplateAttributePrecedenceTest} cloud's.
 */
class MailTemplateAttributePrecedenceTest {

    private static final Path TEMPLATES = Path.of("src", "main", "resources", "templates", "mail");
    /**
     * {@code th:each} is here for exactly the reason {@code th:if} is, and it is the subtler of the two: iteration is
     * precedence 2 and inclusion 1, so a loop written beside an inclusion never runs and the row renders ONCE instead
     * of N times. That is harder to spot in a rendering test than a doubled arm, because the first item is present and
     * every positive assertion on it passes. The safe placement is the same one the conditions have - a wrapping
     * {@code <th:block th:each=...>} around the including element - which is what every roundup fragment uses.
     */
    private static final List<String> OPEN_TEMPLATES = List.of("_shell.html", "session-upcoming.html",
            "session-upcoming.txt", "session-wrap-up.html", "session-wrap-up.txt", "session-awaiting-close.html",
            "session-awaiting-close.txt", "organization-roundup.html", "organization-roundup.txt");

    private static final List<String> CONDITIONS = List.of("th:if", "th:unless", "th:each");
    private static final List<String> INCLUSIONS = List.of("th:replace", "th:insert", "th:include");

    /**
     * The four rows exactly as they shipped in {@code session-awaiting-close.html} at {@code 1da65780}, kept verbatim
     * so this scanner has a real positive to prove itself against rather than only the absence of one.
     */
    private static final String THE_BUG_AS_IT_SHIPPED = """
            <th:block th:fragment="content" th:with="communityName=(${community} != null) ? ${community.name} : null">
            <tr th:if="${nudgeArm} == 'ONE_DAY'" th:replace="~{_shell.html :: nudgeStrip(alert='A day on, still open')}">Chips</tr>
            <tr th:if="${nudgeArm} == 'SEVEN_DAY'" th:replace="~{_shell.html :: nudgeStrip(alert='A week on, still open')}">Chips</tr>
            <tr th:if="${community} != null"
                th:replace="~{_shell.html :: bodyText(text='Did it happen? Say so, and ' + ${communityName} + ' gets the wrap-up.')}">Body</tr>
            <tr th:if="${community} == null"
                th:replace="~{_shell.html :: bodyText(text='Did it happen? Say so, and the community gets the wrap-up.')}">Body</tr>
            <th:block th:if="not ${#strings.isEmpty(sessionUrl)}">
            <tr th:replace="~{_shell.html :: primaryButton(url=${sessionUrl}, label='Close out this session')}">Button</tr>
            </th:block>
            </th:block>
            """;

    @Test
    void noMailTemplatePutsAConditionOnTheSameElementAsAnInclusion() throws IOException {

        assertThat(offenders()).isEmpty();
    }

    /** Calibration: the scanner finds all four of the rows that actually shipped, not three and not zero. */
    @Test
    void theScannerFindsEveryRowOfTheBugItExistsToPrevent() {

        assertThat(elementsOf(THE_BUG_AS_IT_SHIPPED).stream().filter(MailTemplateAttributePrecedenceTest::isOffender))
                .hasSize(4);
    }

    /**
     * The trap that makes a naive {@code <[a-zA-Z][^<>]*?>} scanner useless, and the reason {@link #elementsOf} tracks
     * quotes rather than stopping at the first {@code >}: a condition routinely CONTAINS a {@code >} comparison, and
     * {@code statStrip}'s real one does. A naive scanner truncates such a tag at the {@code >} inside its own
     * condition, never sees the inclusion that follows, and so goes blind on exactly the elements most likely to carry
     * a condition. Do not "simplify" the scanner back into that.
     */
    @Test
    void aConditionContainingAGreaterThanDoesNotHideTheInclusionBehindIt() {

        var withInclusion = """
                <td th:if="${attendeeCount} != null and ${attendeeCount} > 0" th:replace="~{::talkCountCell}">talks</td>
                """;
        var conditionOnly = """
                <td th:if="${attendeeCount} != null and ${attendeeCount} > 0" style="padding:0;">chip</td>
                """;

        assertThat(elementsOf(withInclusion).stream().filter(MailTemplateAttributePrecedenceTest::isOffender))
                .hasSize(1);
        assertThat(elementsOf(conditionOnly).stream().filter(MailTemplateAttributePrecedenceTest::isOffender))
                .isEmpty();
    }

    /**
     * The list is the directory: a listed template is actually reached, so a green result is coverage rather than a
     * list of absent names, and a template left off the list is not left unscanned.
     */
    @Test
    void theScanCoversEveryTemplate() throws IOException {

        assertThat(templates()).containsExactlyInAnyOrderElementsOf(templatesInTheDirectory());
    }

    protected List<String> templates() {

        return OPEN_TEMPLATES;
    }

    private static List<String> templatesInTheDirectory() throws IOException {

        try (var files = Files.list(TEMPLATES)) {
            return files.map(path -> path.getFileName().toString()).toList();
        }
    }

    private List<String> offenders() throws IOException {

        var found = new ArrayList<String>();
        for (var template : templates()) {
            elementsOf(Files.readString(TEMPLATES.resolve(template))).stream()
                    .filter(MailTemplateAttributePrecedenceTest::isOffender).map(element -> template + ": " + element)
                    .forEach(found::add);
        }
        return found;
    }

    private static boolean isOffender(String element) {

        return CONDITIONS.stream().anyMatch(element::contains) && INCLUSIONS.stream().anyMatch(element::contains);
    }

    /**
     * Every markup tag and every TEXT-mode {@code [# ... ]} block in one source, comments excluded so this test's own
     * prose about {@code th:if} beside {@code th:replace} - and the shell's - is never mistaken for markup. The closing
     * delimiter is found outside quoted attribute values only, which is what survives both a {@code >} inside a
     * condition and a {@code ]} inside an indexed expression such as {@code ${talk.subjects[0]}}.
     */
    private static List<String> elementsOf(String source) {

        var elements = new ArrayList<String>();
        var index = 0;
        while (index < source.length()) {
            var start = nextElementStart(source, index);
            if (start < 0) {
                break;
            }
            if (source.startsWith("<!--", start)) {
                var commentEnd = source.indexOf("-->", start);
                index = commentEnd < 0 ? source.length() : commentEnd + "-->".length();
                continue;
            }
            var end = closingIndex(source, start, source.startsWith("[#", start) ? ']' : '>');
            elements.add(source.substring(start, end));
            index = end + 1;
        }
        return elements;
    }

    private static int nextElementStart(String source, int from) {

        var tag = source.indexOf('<', from);
        var block = source.indexOf("[#", from);
        if (tag < 0 || block < 0) {
            return Math.max(tag, block);
        }
        return Math.min(tag, block);
    }

    private static int closingIndex(String source, int from, char closing) {

        var quote = 0;
        for (var index = from; index < source.length(); index++) {
            var character = source.charAt(index);
            if (quote == 0 && (character == '"' || character == '\'')) {
                quote = character;
            } else if (quote == character) {
                quote = 0;
            } else if (quote == 0 && character == closing) {
                return index;
            }
        }
        return source.length();
    }
}
