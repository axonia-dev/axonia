package dev.axonia.roundup.internal;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import dev.axonia.roundup.ArtifactActivity.CapturedArtifact;
import dev.axonia.roundup.SessionActivity.HeldSession;
import dev.axonia.roundup.SessionActivity.UpcomingSession;
import dev.axonia.roundup.TopicActivity.TagCount;
import dev.axonia.roundup.internal.QuarterActivity.NamedCommunity;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A structural guard on the seam between {@link RoundupFacts} and the two templates that read it. The roundup's five
 * headline figures - {@code sessionCount}, {@code talkCount}, {@code presenterCount}, {@code resourceCount},
 * {@code openTalkCount} - and its two headings are interpolated RAW, with no {@code != null} arm of their own, because
 * every one of them is a number the assembly always produces. A fact that stopped being produced therefore does not
 * render a shorter message, it renders the literal word "null" into an organization-wide mail.
 *
 * <p>
 * That is not hypothetical: the producer shipped {@code .facts(Map.of())} onto this branch, bounded only by the roundup
 * defaulting to off. A rendering test can only ever pin the facts somebody thought to write down; this reads the fact
 * names out of the templates themselves, so a fact added to a template and forgotten in the assembly fails here rather
 * than in an inbox.
 *
 * <p>
 * <b>Three things it deliberately does not catch, so nobody reads it as more than it is.</b> None of them is live today
 * - every one of the current {@code th:with} bindings was checked against the fact names, and none collides.
 *
 * <ol>
 * <li><b>It does not follow {@code th:replace="~{_shell.html :: page(...)}"} into the shared shell.</b> A fact the
 * shell interpolates raw is unchecked here; {@code sender} is covered only incidentally, because the {@code .txt} half
 * happens to read it directly too. Guarding the shell means guarding it for every message that includes it, which
 * belongs to {@code communication} rather than to this module.</li>
 * <li><b>It guards root names only.</b> {@code ${community.summaryParts}} and {@code ${session.dateLabel}} both reduce
 * to the root {@code communities}; the nested accessors are keys {@link RoundupFacts} constructs by hand inside a card
 * and its session rows, and renaming one there would still reach an inbox. The per-row keys are pinned by
 * {@code RoundupFactsTest}'s own assertions instead.</li>
 * <li><b>Locals are subtracted per file, never across both.</b> That is what stops a {@code th:with} binding in the
 * {@code .txt} from masking a same-named real fact read by the {@code .html} - the reason the two files are scanned
 * independently rather than concatenated.</li>
 * </ol>
 */
class RoundupTemplateFactsTest {

    /**
     * Resolved off the classpath, not by relative path: {@code Path.of("src", "main", ...)} is only correct while the
     * working directory happens to be the module root, which is true under Surefire and not true of every runner.
     */
    private static final String TEMPLATE_ROOT = "templates/mail/";
    private static final List<String> FILES = List.of("organization-roundup.html", "organization-roundup.txt");

    /** Every {@code ${...}} in a Thymeleaf template, standard or inlined. */
    private static final Pattern EXPRESSION = Pattern.compile("\\$\\{([^{}]*)}");

    /** A root variable: an identifier that is neither a property of something else nor a Thymeleaf utility object. */
    private static final Pattern ROOT_VARIABLE = Pattern.compile("(?<![.\\w#])([a-zA-Z_][a-zA-Z0-9_]*)");

    private static final Pattern WITH_ATTRIBUTE = Pattern.compile("th:with=\"([^\"]*)\"", Pattern.DOTALL);
    private static final Pattern WITH_BINDING = Pattern.compile("(?<![.\\w])([a-zA-Z_][a-zA-Z0-9_]*)\\s*=(?!=)");
    /**
     * Both names a {@code th:each} can bind: the item and its optional status variable. The status variable is as much
     * a local as the item is, and a scanner that saw only the first would report {@code presenterStat} as a fact the
     * assembly must produce - a false positive that would block any template joining a list with a separator.
     */
    private static final Pattern EACH_BINDING = Pattern
            .compile("th:each=\"\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?:,\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*)?:");

    private static final Set<String> OPERATORS = Set.of("and", "or", "not", "null", "true", "false");

    /**
     * Minted by the renderer for the one recipient it is rendering for, never by {@link RoundupFacts} - a fact map
     * could not sign it, and see {@code RenderRequest} for why the signing stays inside {@code communication}.
     */
    private static final Set<String> RENDERER_PROVIDED = Set.of("unsubscribePageUrl");

    @Test
    void everyFactTheRoundupTemplatesReadIsOneTheAssemblyProduces() throws IOException {

        assertThat(RoundupFacts.of(aRealisticQuarter()).keySet()).containsAll(factsTheTemplatesRead());
    }

    /**
     * Calibration. A scanner that quietly matched nothing would pass the test above for the wrong reason, so this names
     * the figures the templates really do interpolate without a guard - the exact set a missing fact would have
     * rendered as "null".
     */
    @Test
    void theScannerFindsTheFiguresTheTemplatesInterpolateWithoutAGuard() throws IOException {

        assertThat(factsTheTemplatesRead()).contains("sessionCount", "talkCount", "presenterCount", "resourceCount",
                "openTalkCount", "sessionTitle", "periodLabel");
    }

    private static Set<String> factsTheTemplatesRead() throws IOException {

        var facts = new TreeSet<String>();
        for (var file : FILES) {
            facts.addAll(factsReadBy(new ClassPathResource(TEMPLATE_ROOT + file).getContentAsString(UTF_8)));
        }
        return facts;
    }

    /** One file at a time, so a local bound in one template can never mask a real fact read by the other. */
    private static Set<String> factsReadBy(String template) {

        var referenced = new TreeSet<String>();
        var locals = new TreeSet<>(RENDERER_PROVIDED);
        collect(EXPRESSION.matcher(template), matcher -> rootVariablesOf(matcher.group(1)).forEach(referenced::add));
        collect(WITH_ATTRIBUTE.matcher(template),
                matcher -> collect(WITH_BINDING.matcher(matcher.group(1)), binding -> locals.add(binding.group(1))));
        collect(EACH_BINDING.matcher(template), matcher -> IntStream.rangeClosed(1, matcher.groupCount())
                .mapToObj(matcher::group).filter(Objects::nonNull).forEach(locals::add));
        referenced.removeAll(locals);
        return referenced;
    }

    private static List<String> rootVariablesOf(String expression) {

        var matcher = ROOT_VARIABLE.matcher(expression.replaceAll("'[^']*'", "''"));
        return matcher.results().map(result -> result.group(1)).filter(name -> !OPERATORS.contains(name)).toList();
    }

    private static void collect(Matcher matcher, Consumer<Matcher> onMatch) {

        while (matcher.find()) {
            onMatch.accept(matcher);
        }
    }

    private static QuarterActivity aRealisticQuarter() {

        return QuarterActivity.builder().organizationName("Acme Corp").quarter(new Quarter(2026, 2))
                .communities(List.of(new NamedCommunity(7L, "Backend Guild", null, null)))
                .held(List.of(HeldSession.builder().sessionId(1L).communityId(7L).title("Hexagons in anger")
                        .heldAt(Instant.parse("2026-05-12T17:00:00Z")).talkCount(2).talkTitles(List.of())
                        .presenterNames(List.of("Alice Dubois")).build()))
                .upcoming(List.of(new UpcomingSession(7L, "Signals in React", Instant.parse("2026-07-14T17:00:00Z"))))
                .firstTimePresenterCount(3)
                .captured(IntStream.range(0, 47)
                        .mapToObj(i -> new CapturedArtifact(1L, "Artifact " + i, "https://example.test/" + i,
                                "Recording"))
                        .toList())
                .topics(List.of(new TagCount("hexagonal", 4), new TagCount("testing", 1))).openTalkCount(2)
                .communityCap(8).sessionsPerCommunityCap(4).topicCap(12)
                .communitiesUrl("https://axonia.example/communities").build();
    }
}
