package dev.axonia.communication.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.thymeleaf.exceptions.TemplateInputException;

import dev.axonia.communication.configuration.MailTemplateConfig;
import dev.axonia.roundup.configuration.RoundupProperties;
import dev.axonia.roundup.domain.RoundupFacts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailRendererTest {

    /** The roundup's caps as bound with nothing configured, which their {@code @Max} ceilings hold them to. */
    private static final RoundupProperties ROUNDUP_CAPS = new Binder(
            new MapConfigurationPropertySource(Map.of("axonia.roundup.settle-lag", "2d")))
            .bind("axonia.roundup", Bindable.of(RoundupProperties.class)).get();
    private static final int ROUNDUP_COMMUNITY_CAP = ROUNDUP_CAPS.communityCap();
    private static final int ROUNDUP_SESSIONS_PER_COMMUNITY_CAP = ROUNDUP_CAPS.sessionsPerCommunityCap();
    private static final int ROUNDUP_TOPIC_CAP = ROUNDUP_CAPS.topicCap();
    private static final int ROUNDUP_TALK_CAP = RoundupFacts.TALK_CAP;

    private MailRenderer renderer;

    @BeforeEach
    void setUp() {

        renderer = new MailRenderer(new MailTemplateConfig().mailTemplateEngine());
    }

    @Test
    void aWrapUpCarriesBothAnHtmlAndAPlainTextPart() {

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks",
                        List.of(Map.of("title", "Signals in React", "presenter", "Ada")), "followUpNotes",
                        "Recording went up late.", "sessionUrl", "https://axonia.example/s/42"));

        assertThat(rendered.subject()).isEqualTo("Frontend Dojo: what happened");
        assertThat(rendered.html()).contains("Signals in React").contains("Ada")
                .doesNotContain("No talks were recorded for this session.");
        assertThat(rendered.text()).contains("Signals in React")
                .doesNotContain("No talks were recorded for this session.").doesNotContain("<");
    }

    @Test
    void followUpProseIsEscapedNeverInterpreted() {

        var rendered = renderer.render("session-wrap-up", Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(),
                "followUpNotes", "<script>alert(1)</script>", "sessionUrl", "https://axonia.example/s/42"));

        assertThat(rendered.html()).contains("&lt;script&gt;").doesNotContain("<script>");
    }

    @Test
    void aSubjectWithAmpersandsQuotesAndAngleBracketsIsNotHtmlEscaped() {

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Q&A: \"Signals\" <live>", "talks", List.of()));

        assertThat(rendered.subject()).isEqualTo("Q&A: \"Signals\" <live>: what happened");
    }

    @Test
    void aResourceWithAnUnsafeSchemeIsNeverAnHref() {

        var talk = Map.<String, Object>of("title", "Signals in React", "presenter", "Ada", "resources",
                List.of(Map.of("url", "javascript:alert(1)")));

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(talk)));

        assertThat(rendered.html()).doesNotContain("href=\"javascript:alert(1)\"").contains("javascript:alert(1)");
    }

    @Test
    void anUnsubscribeLinkAppearsOnlyWhenTheFactIsPresent() {

        var withLink = renderer.render("session-wrap-up", Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(),
                "unsubscribePageUrl", "https://axonia.example/unsubscribe?token=abc"));
        var withoutLink = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of()));

        assertThat(withLink.html()).contains("Unsubscribe").contains("https://axonia.example/unsubscribe?token=abc");
        assertThat(withLink.text()).contains("Unsubscribe");
        assertThat(withoutLink.html()).doesNotContain("Unsubscribe");
        assertThat(withoutLink.text()).doesNotContain("Unsubscribe");
    }

    @Test
    void wrapUpCarriesTheDateTheSessionWasHeld() {

        var rendered = renderer.render("session-wrap-up", Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(),
                "scheduledAt", Instant.parse("2026-08-15T18:30:00Z")));

        assertThat(rendered.html()).contains("15 August 2026");
        assertThat(rendered.text()).contains("15 August 2026");
    }

    @Test
    void anUpcomingNoticeCarriesTheAgendaAndInvitesAContributionToAnOpenSlot() {

        var rendered = renderer.render("session-upcoming",
                Map.of("sessionTitle", "Frontend Dojo", "seriesTitle", "The Frontend Dojo series", "scheduledAt",
                        Instant.parse("2026-08-15T18:30:00Z"), "sessionUrl", "https://axonia.example/s/42", "talks",
                        List.of(Map.of("title", "Signals in React", "presenter", "Ada"),
                                Map.of("title", "Lightning round", "presenter", ""))));

        assertThat(rendered.subject()).isEqualTo("Frontend Dojo: happening soon");
        assertThat(rendered.html()).contains("Signals in React").contains("Ada").contains("15 August 2026")
                .contains("https://axonia.example/s/42").contains("The Frontend Dojo series");
        assertThat(rendered.text()).contains("Signals in React").contains("With Ada").contains("15 August 2026")
                .doesNotContain("<");
    }

    private static Map<String, Object> aTalk() {

        return Map.of("title", "Colamaiar", "presenter", "Alice Anderson", "presenters",
                List.of(Map.of("name", "Alice Anderson", "initials", "AA")), "subjects",
                List.of("state management", "signals"), "durationMinutes", 20);
    }

    /**
     * The agenda used to render a title and a presenter line and nothing else, so four talks read as four identical
     * blocks. Every value asserted here was already on the fact map or one field away from it.
     */
    @Test
    void anAgendaTalkSaysWhatItIsAboutHowLongItRunsAndWhoIsGivingIt() {

        var rendered = renderer.render("session-upcoming",
                Map.of("sessionTitle", "Weekly FG", "talks", List.of(aTalk()), "sender", aCommunity()));

        assertThat(rendered.html()).contains("Colamaiar").contains("state management").contains("signals")
                .contains("20 min").contains(">AA<").contains("Alice Anderson").contains("Alice Anderson · 20 min");
        assertThat(rendered.text()).contains("state management").contains("20 min");
    }

    /**
     * The .txt side carries its own, separate null-duration guard on both the "With presenter" arm and the "Awaiting a
     * presenter" arm - nothing else in the suite pins either of them, since PreviewMatchesSendTest never looks at .txt
     * output. A broken guard would leak a stray " - ", a bare "min", or the literal word "null" into a member's inbox
     * rather than degrading silently the way the html side already does.
     */
    @Test
    void anAgendaTalkWithNoDurationDoesNotRenderADanglingSeparator() {

        var talk = new HashMap<>(aTalk());
        talk.put("durationMinutes", null);

        var rendered = renderer.render("session-upcoming",
                Map.of("sessionTitle", "Weekly FG", "talks", List.of(talk), "sender", aCommunity()));

        assertThat(rendered.html()).doesNotContain("min").doesNotContain("&middot; <");
        assertThat(rendered.text()).contains("With Alice Anderson").doesNotContain("min").doesNotContain("null");
    }

    @Test
    void anOpenSlotWithNoDurationDoesNotRenderADanglingSeparatorEither() {

        var talk = mapWithNullPresenter("Lightning round");
        talk.put("durationMinutes", null);

        var rendered = renderer.render("session-upcoming",
                Map.of("sessionTitle", "Weekly FG", "talks", List.of(talk), "sender", aCommunity()));

        assertThat(rendered.html()).contains("Awaiting a presenter").doesNotContain("min");
        assertThat(rendered.text()).contains("Awaiting a presenter").doesNotContain("min").doesNotContain("null");
    }

    /**
     * The open-slot rendering's standalone duration line - the one branch reachable only when a slot is BOTH open and
     * already carries a duration - is pinned here. Map.of rather than a null-carrying presenter entry: omitting the key
     * altogether reads as absent through #strings.isEmpty the same way an explicit null would, without fighting
     * Map.of's own rejection of null values.
     */
    @Test
    void anOpenSlotWithADurationStatesHowLongItWillRun() {

        var talk = Map.<String, Object>of("title", "Lightning round", "durationMinutes", 30);

        var rendered = renderer.render("session-upcoming",
                Map.of("sessionTitle", "Weekly FG", "talks", List.of(talk), "sender", aCommunity()));

        assertThat(rendered.html()).contains("Awaiting a presenter").contains("30 min");
        assertThat(rendered.text()).contains("Awaiting a presenter").contains("30 min");
    }

    /**
     * The old copy, "Open. Propose a topic and put your name on it.", asked for the wrong act - an open slot already
     * carries a topic, and taking one is volunteering, not proposing. The block now states the fact calmly; a later
     * task adds the button that does the asking.
     */
    @Test
    void anOpenSlotWithNoPresenterStatesItIsAwaitingAPresenter() {

        var rendered = renderer.render("session-upcoming",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(mapWithNullPresenter("Lightning round"))));

        assertThat(rendered.html()).contains("Awaiting a presenter");
        assertThat(rendered.text()).contains("Awaiting a presenter").doesNotContain("<");
    }

    private static Map<String, Object> mapWithNullPresenter(String title) {

        var talk = new HashMap<String, Object>();
        talk.put("title", title);
        talk.put("presenter", null);
        return talk;
    }

    private static Map<String, Object> anOpenTalk() {

        return Map.of("title", "Angular 25+", "presenters", List.of(), "subjects", List.of(), "durationMinutes", 15);
    }

    @Test
    void anOpenSlotAsksCalmlyAndLetsTheButtonDoTheAsking() {

        var rendered = renderer.render("session-upcoming", Map.of("sessionTitle", "Weekly FG", "talks",
                List.of(anOpenTalk()), "openSlotCount", 1, "sender", aCommunity()));

        assertThat(rendered.html()).contains("Awaiting a presenter").contains("#fdeef4")
                .doesNotContain("Propose a topic and put your name on it");
        assertThat(rendered.text()).contains("Awaiting a presenter");
    }

    /**
     * One call to action, chosen from the agenda's own content. Still one button, still brand pink, still the same URL
     * - only the label moves, so the one-call-to-action rule survives intact. The .txt side must carry the exact same
     * three-way branch: nothing else guards it against silently staying the flat "View the session page" constant,
     * since {@code PreviewMatchesSendTest} never looks at .txt output at all.
     */
    @Test
    void theButtonAsksForWhateverTheAgendaIsActuallyMissing() {

        var empty = renderer.render("session-upcoming", Map.of("sessionTitle", "Weekly FG", "talks", List.of(),
                "sessionUrl", "https://axonia.example/s/42", "sender", aCommunity()));
        var open = renderer.render("session-upcoming",
                Map.of("sessionTitle", "Weekly FG", "talks", List.of(anOpenTalk()), "openSlotCount", 1, "sessionUrl",
                        "https://axonia.example/s/42", "sender", aCommunity()));
        var full = renderer.render("session-upcoming", Map.of("sessionTitle", "Weekly FG", "talks", List.of(aTalk()),
                "openSlotCount", 0, "sessionUrl", "https://axonia.example/s/42", "sender", aCommunity()));

        assertThat(empty.html()).contains("Put a talk on the agenda");
        assertThat(open.html()).contains("Take an open slot");
        assertThat(full.html()).contains("View the session page");

        assertThat(empty.text()).contains("Put a talk on the agenda: https://axonia.example/s/42");
        assertThat(open.text()).contains("Take an open slot: https://axonia.example/s/42");
        assertThat(full.text()).contains("View the session page: https://axonia.example/s/42");
    }

    /**
     * Both arms, in both halves. A {@code contains} assertion on its own cannot catch an over-render, and an
     * over-render is exactly what a condition written beside a {@code th:replace} produces - the inclusion fires at
     * precedence 1 and the condition at 3, so the row renders for every session and one carrying no room offers a link
     * to nothing. The negative arm is what pins the guard; the positive one only pins the copy.
     */
    @Test
    void theRoomIsLinkedOnlyForASessionThatHasOne() {

        var withRoom = renderer.render("session-upcoming", Map.of("sessionTitle", "Weekly FG", "talks", List.of(),
                "sender", aCommunity(), "joinUrl", "https://meet.example/dojo"));
        var withoutRoom = renderer.render("session-upcoming",
                Map.of("sessionTitle", "Weekly FG", "talks", List.of(), "sender", aCommunity()));

        assertThat(withRoom.html()).contains("Meets online.").contains("Join the session")
                .contains("href=\"https://meet.example/dojo\"");
        assertThat(withRoom.text()).contains("Meets online. Join the session: https://meet.example/dojo");
        assertThat(withoutRoom.html()).doesNotContain("Join the session").doesNotContain("Meets online");
        assertThat(withoutRoom.text()).doesNotContain("Join the session").doesNotContain("Meets online")
                .doesNotContain("null");
    }

    /**
     * The two parts of a place that are not a link, in both halves. Nothing compares the {@code .html} to the
     * {@code .txt} - {@code RenderedMessage} has no text field for {@code PreviewMatchesSendTest} to compare - so an
     * explicit assertion on each is the only thing keeping the two from drifting apart.
     *
     * <p>
     * Each arm is asserted absent as well as present, for the reason above: a guard written beside its
     * {@code th:replace} would render a location line reading a literal {@code null} for every session that has none,
     * and no {@code contains} assertion would ever notice.
     */
    @Test
    void theLocationAndJoiningDetailsAreRenderedOnlyWhenTheSessionCarriesThem() {

        var withPlace = renderer.render("session-upcoming",
                Map.of("sessionTitle", "Weekly FG", "talks", List.of(), "sender", aCommunity(), "location",
                        "Room 4B, Gasperich", "joinUrl", "https://meet.example/dojo", "joiningDetails",
                        "Dial +352 27 00 00\nID 123 456 789"));
        var withoutPlace = renderer.render("session-upcoming",
                Map.of("sessionTitle", "Weekly FG", "talks", List.of(), "sender", aCommunity()));

        assertThat(withPlace.html()).contains("Where").contains("Room 4B, Gasperich").contains("ID 123 456 789")
                .contains("white-space:pre-wrap");
        assertThat(withPlace.text()).contains("Where:").contains("Room 4B, Gasperich")
                .contains("Dial +352 27 00 00\nID 123 456 789");
        assertThat(withoutPlace.html()).doesNotContain("Room 4B").doesNotContain(">Where<");
        assertThat(withoutPlace.text()).doesNotContain("Where:").doesNotContain("null");
    }

    /**
     * The parts are independent keys, so a session that names only where to stand renders that line and nothing around
     * it - no empty join link, no blank details paragraph, and above all no literal {@code null}.
     */
    @Test
    void aLocationOnItsOwnRendersWithoutAJoinLinkBesideIt() {

        var rendered = renderer.render("session-upcoming", Map.of("sessionTitle", "Weekly FG", "talks", List.of(),
                "sender", aCommunity(), "location", "Room 4B, Gasperich"));

        assertThat(rendered.html()).contains("Room 4B, Gasperich").doesNotContain("Join the session")
                .doesNotContain("null");
        assertThat(rendered.text()).contains("Room 4B, Gasperich").doesNotContain("Join the session")
                .doesNotContain("null");
    }

    @Test
    void anEmptyAgendaInvitesInsteadOfShrugging() {

        var rendered = renderer.render("session-upcoming",
                Map.of("sessionTitle", "Weekly FG", "talks", List.of(), "sender", aCommunity()));

        assertThat(rendered.html()).contains("Nothing on the agenda yet.")
                .contains("Put something on it and Backend Guild will hear it.")
                .doesNotContain("The agenda is still being put together.");
        assertThat(rendered.text())
                .contains("Nothing on the agenda yet. This one is wide open. "
                        + "Put something on it and Backend Guild will hear it.")
                .doesNotContain("The agenda is still being put together.");
    }

    /**
     * Reachable across a rollout window rather than merely hypothetical: facts are frozen into the outbox at enqueue,
     * so every row enqueued before the sender fact existed carries no {@code sender} key at all, and an organizer
     * previewing one of those rows takes exactly this path. Both halves now interpolate the name, so both need their
     * fallback wording pinned.
     *
     * <p>
     * The {@code community}-to-{@code sender} rename created a second population of exactly this shape: a row frozen
     * under the old key renders with no sender at all rather than failing. Degrading is the deliberate choice over a
     * rehydration shim, and it is this path - already pinned, already prose - that says what degrading looks like.
     */
    @Test
    void anEmptyAgendaWithNoCommunityFallsBackToYourCommunity() {

        var rendered = renderer.render("session-upcoming", Map.of("sessionTitle", "Weekly FG", "talks", List.of()));

        assertThat(rendered.html()).contains("Put something on it and your community will hear it.");
        assertThat(rendered.text()).contains("Nothing on the agenda yet. This one is wide open. "
                + "Put something on it and your community will hear it.").doesNotContain("null");
    }

    /**
     * A slot's title is nullable, so a titleless agenda item is a real row shape. The HTML has always said "Untitled
     * talk"; the .txt twins rendered a blank line under the {@code ----} rule, which reads as a rendering fault rather
     * than as a talk nobody has named yet. All four renderings now agree.
     */
    @ParameterizedTest
    @ValueSource(strings = {"session-upcoming", "session-wrap-up"})
    void aTitlelessTalkIsNamedTheSameWayInBothHalves(String template) {

        var talk = new HashMap<String, Object>();
        talk.put("title", null);
        talk.put("presenter", "Ada");

        var rendered = renderer.render(template,
                Map.of("sessionTitle", "Weekly FG", "talks", List.of(talk), "sender", aCommunity()));

        assertThat(rendered.html()).contains("Untitled talk");
        assertThat(rendered.text()).contains("Untitled talk").doesNotContain("null");
    }

    /**
     * The merged community/series line used to emit its separator unconditionally, so a fact map with a series but no
     * community opened with a dangling " - ". Not unreachable: facts are frozen at enqueue, so any row enqueued before
     * the community fact shipped has no {@code sender} key, and every organizer preview of such a row renders this.
     */
    @ParameterizedTest
    @ValueSource(strings = {"session-upcoming", "session-wrap-up", "session-awaiting-close"})
    void aSeriesWithNoCommunityDoesNotOpenWithADanglingSeparator(String template) {

        var withBoth = renderer.render(template, Map.of("sessionTitle", "Weekly FG", "talks", List.of(), "seriesTitle",
                "Deep dives", "sender", aCommunity()));
        var seriesOnly = renderer.render(template,
                Map.of("sessionTitle", "Weekly FG", "talks", List.of(), "seriesTitle", "Deep dives"));

        assertThat(withBoth.text()).contains("Backend Guild - Deep dives");
        assertThat(seriesOnly.text()).contains("Deep dives").doesNotContain(" - Deep dives");
    }

    /**
     * A link, never a second button: the shell is explicit that a message carries ONE call to action, and two competing
     * pink pills is how that rule dies.
     *
     * <p>
     * {@code sessionUrl} is deliberately included: without it the primary button never renders at all, and a raw count
     * of {@code background-color:#de3b78} would then pass vacuously - it would be counting only {@code aTalk()}'s
     * presenter avatar (index 0 in the palette, also {@code #de3b78}), never a button, proving nothing about "one call
     * to action". Asserting {@code containsOnlyOnce} against a substring unique to {@code primaryButton}'s own style
     * attribute (never shared with the avatar's differently-ordered, comma-free style string) keeps the assertion
     * honest even with both a real button and a real avatar on the page.
     */
    @Test
    void theBacklogInvitationIsALinkAndNotASecondButton() {

        var rendered = renderer.render("session-upcoming",
                Map.of("sessionTitle", "Weekly FG", "talks", List.of(aTalk()), "openSlotCount", 0, "sessionUrl",
                        "https://axonia.example/s/42", "backlogUrl",
                        "https://axonia.example/communities/backend-guild/backlog", "sender", aCommunity()));

        assertThat(rendered.html()).contains("https://axonia.example/communities/backend-guild/backlog")
                .contains("Add it to the backlog");
        assertThat(rendered.html()).containsOnlyOnce(
                "display:inline-block; background-color:#de3b78; color:#ffffff; text-decoration:none;");
        assertThat(rendered.text()).contains("Something you want to hear about? Add it to the backlog: "
                + "https://axonia.example/communities/backend-guild/backlog");
    }

    @Test
    void anAwaitingCloseNudgeAsksWhetherTheSessionHappened() {

        var rendered = renderer.render("session-awaiting-close", Map.of("sessionTitle", "Backend Dojo", "scheduledAt",
                Instant.parse("2026-07-20T09:00:00Z"), "sessionUrl", "https://axonia.example/s/7"));

        assertThat(rendered.subject()).isEqualTo("Did Backend Dojo happen?");
        assertThat(rendered.html()).contains("Did it happen?").contains("20 July 2026")
                .contains("https://axonia.example/s/7");
        assertThat(rendered.text()).contains("Did it happen?").contains("20 July 2026").doesNotContain("<");
    }

    @Test
    void aMissingSessionTitleDoesNotRenderTheWordNull() {

        var rendered = renderer.render("session-wrap-up", Map.of("talks", List.of()));

        assertThat(rendered.subject()).doesNotContain("null").contains("This session");
        assertThat(rendered.html()).doesNotContain("null");
        assertThat(rendered.text()).doesNotContain("null");
    }

    @ParameterizedTest
    @ValueSource(strings = {"session-upcoming", "session-wrap-up", "session-awaiting-close"})
    void aMinimalFactMapStillRendersWithoutError(String template) {

        assertThatCode(() -> {
            var rendered = renderer.render(template, Map.of("sessionTitle", "Frontend Dojo"));
            assertThat(rendered.subject()).contains("Frontend Dojo");
            assertThat(rendered.text()).doesNotContain("<").doesNotContain("\n\n\n");
        }).doesNotThrowAnyException();
    }

    @Test
    void aMissingTemplateThrowsRatherThanFailingSilently() {

        assertThatThrownBy(() -> renderer.render("no-such-template", Map.of()))
                .isInstanceOf(TemplateInputException.class);
    }

    @Test
    void thePreheaderNamesWhoSpokeAndDiffersFromTheSubject() {

        var talk = Map.<String, Object>of("title", "Signals in React", "presenter", "Ada", "subjects",
                List.of("React"));

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(talk)));

        var preheaderStart = rendered.html().indexOf("mso-hide:all;");
        var preheader = rendered.html().substring(preheaderStart, preheaderStart + 200);

        assertThat(rendered.subject()).isEqualTo("Frontend Dojo: what happened");
        assertThat(preheader).contains("Ada on React").doesNotContain("Frontend Dojo: what happened");
    }

    @Test
    void noActionPointsMeansNoHeadingForThem() {

        var rendered = renderer.render("session-wrap-up", Map.of("sessionTitle", "Frontend Dojo", "talks", List.of()));

        assertThat(rendered.html()).doesNotContain(">Action points</p>");
        assertThat(rendered.text()).doesNotContain("ACTION POINTS");
    }

    @Test
    void noCheckedItemsMeansNoHeadingForThem() {

        var rendered = renderer.render("session-wrap-up", Map.of("sessionTitle", "Frontend Dojo", "talks", List.of()));

        assertThat(rendered.html()).doesNotContain(">Checked</p>");
        assertThat(rendered.text()).doesNotContain("CHECKED");
    }

    @Test
    void aCheckedItemRendersItsAssigneeAndItsOutcomeLabel() {

        var checkedItem = checkedItemFact("Renew the certificate", "Bob", "Carried", "CARRIED");

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(), "checkedItems", List.of(checkedItem)));

        assertThat(rendered.html()).contains(">Checked</p>").contains("Renew the certificate").contains("Bob")
                .contains("Carried");
        assertThat(rendered.text()).contains("CHECKED\n").contains("Renew the certificate - Bob (Carried)");
    }

    @Test
    void checkedItemsWithEveryOutcomeRenderThreeDistinctBadgeColors() {

        var done = checkedItemFact("Renew the certificate", "Bob", "Done", "DONE");
        var carried = checkedItemFact("Write the retro", "Ada", "Carried", "CARRIED");
        var dropped = checkedItemFact("Sunset the beta flag", "Ada", "Dropped", "DROPPED");

        var rendered = renderer.render("session-wrap-up", Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(),
                "checkedItems", List.of(done, carried, dropped)));

        // The badge's own colour, not the paragraph's static #786a7e base text colour every row already carries.
        assertThat(rendered.html()).contains("background-color:#f6f0ec; color:#1f9d63;")
                .contains("background-color:#f6f0ec; color:#c32c66;")
                .contains("background-color:#f6f0ec; color:#786a7e;");
    }

    @Test
    void aDoneActionPointIsDistinguishableFromAnOpenOne() {

        var done = actionPointFact("Write the runbook", "Bob", LocalDate.of(2026, 8, 8), true);
        var open = actionPointFact("Try the staging config", null, null, false);

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(), "actionPoints", List.of(done, open)));

        assertThat(rendered.html()).contains(">Action points</p>").contains("text-decoration:line-through")
                .contains("Write the runbook").contains("Due 8 Aug").contains("Try the staging config")
                .contains("Unassigned");
        assertThat(rendered.text()).contains("ACTION POINTS\n").contains("Write the runbook - Bob, due 8 Aug (done)")
                .contains("Try the staging config - Unassigned").doesNotContain("[x]").doesNotContain("[ ]")
                .doesNotContain("Try the staging config - Unassigned (done)");
    }

    @Test
    void aDoneActionPointRendersADonePillAndAnOpenOneDoesNot() {

        var done = actionPointFact("Write the runbook", "Bob", null, true);
        var open = actionPointFact("Try the staging config", null, null, false);

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(), "actionPoints", List.of(done, open)));

        var openRowStart = rendered.html().indexOf("Try the staging config");
        var doneRowHtml = rendered.html().substring(0, openRowStart);
        var openRowHtml = rendered.html().substring(openRowStart);

        assertThat(doneRowHtml).contains(">Done<").contains("color:#1f9d63");
        assertThat(openRowHtml).doesNotContain(">Done<");
    }

    @Test
    void aDroppedActionPointRendersADroppedPillAndStaysStruckThroughLikeADoneOne() {

        var dropped = actionPointFact("Retire the old dashboard", "Bob", null, false, true);
        var open = actionPointFact("Try the staging config", null, null, false);

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(), "actionPoints", List.of(dropped, open)));

        var openRowStart = rendered.html().indexOf("Try the staging config");
        var droppedRowHtml = rendered.html().substring(0, openRowStart);
        var openRowHtml = rendered.html().substring(openRowStart);

        assertThat(droppedRowHtml).contains(">Dropped<").contains("text-decoration:line-through")
                .doesNotContain(">Done<");
        assertThat(openRowHtml).doesNotContain(">Dropped<").doesNotContain("text-decoration:line-through");
        assertThat(rendered.text()).contains("Retire the old dashboard - Bob (dropped)")
                .doesNotContain("Retire the old dashboard - Bob (done)");
    }

    @Test
    void actionPointRowNoLongerRendersACheckboxAffordance() {

        var done = actionPointFact("Write the runbook", "Bob", null, true);
        var open = actionPointFact("Try the staging config", null, null, false);

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(), "actionPoints", List.of(done, open)));

        assertThat(rendered.html()).doesNotContain("border:2px solid #ede4e9");
    }

    @Test
    void aSessionResourceWithNoTitleFallsBackToItsUrl() {

        var rendered = renderer.render("session-wrap-up", Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(),
                "resources", List.of(Map.of("url", "https://axonia.example/notes"))));

        assertThat(rendered.html()).contains("From the session").contains(">https://axonia.example/notes<");
        assertThat(rendered.text()).contains("FROM THE SESSION\n").contains("https://axonia.example/notes");
    }

    @Test
    void anUntitledSessionResourceIsLabelledByItsHost() {

        var resource = Map.of("url", "https://axonia.example/notes", "label", "axonia.example");
        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(), "resources", List.of(resource)));

        assertThat(rendered.html()).contains(">axonia.example</a>").doesNotContain(">https://axonia.example/notes<");
        assertThat(rendered.text()).contains("FROM THE SESSION\n  axonia.example: https://axonia.example/notes");
    }

    @Test
    void aSessionResourceLabelledByItsUrlIsWrittenOnce() {

        var resource = Map.of("url", "not a url", "label", "not a url");
        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(), "resources", List.of(resource)));

        assertThat(rendered.text()).contains("FROM THE SESSION\n  not a url").doesNotContain("not a url: ");
    }

    @Test
    void thePlainTextStatesTheDurationTheBandShows() {

        assertThat(renderer.render("session-wrap-up", aWrapUp()).text()).contains("\nDuration: 1h 04\n");
        assertThat(renderer.render("session-wrap-up", Map.of("sessionTitle", "Frontend Dojo")).text())
                .doesNotContain("Duration");
    }

    @Test
    void thePosterKickerNamesTheDayOnTheClockTheHeldLineUses() {

        var facts = aWrapUp();
        facts.remove("recordingUrl");
        facts.put("scheduledAt", Instant.parse("2026-12-01T23:30:00Z"));
        facts.put("readerTimeZone", "America/New_York");
        var untimed = new HashMap<>(facts);
        untimed.put("hasTime", false);

        assertThat(renderer.render("session-wrap-up", facts).html()).contains(">1 December</p>")
                .doesNotContain(">2 December</p>");
        assertThat(renderer.render("session-wrap-up", untimed).html()).contains(">2 December</p>")
                .doesNotContain(">1 December</p>");
    }

    @Test
    void aBlankNoteGivesNoTakeaways() {

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(), "followUpNotes", "  \n "));

        assertThat(rendered.html()).doesNotContain(">Takeaways</p>");
        assertThat(rendered.text()).doesNotContain("TAKEAWAYS");
    }

    @Test
    void aSessionLevelResourceIsUnaffectedEvenWhenItsTitleMatchesATalkTitle() {

        var talk = Map.<String, Object>of("title", "It was a beautiful topic", "presenter", "Ada");

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(talk), "resources",
                        List.of(Map.of("title", "It was a beautiful topic", "url", "https://axonia.example/notes"))));

        assertThat(rendered.html()).contains("From the session").contains(">It was a beautiful topic<");
        assertThat(rendered.text()).contains("FROM THE SESSION\n")
                .contains("It was a beautiful topic: https://axonia.example/notes");
    }

    @Test
    void theCardsInsetLivesOnlyOnTheContainerCellNotOnAnyBlockRow() {

        var talk = Map.<String, Object>of("title", "Signals in React", "presenter", "Ada");
        var actionPoint = actionPointFact("Try the staging config", null, null, false);

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(talk), "actionPoints", List.of(actionPoint),
                        "resources", List.of(Map.of("url", "https://axonia.example/notes")), "followUpNotes",
                        "Recording went up late.", "sessionUrl", "https://axonia.example/s/42", "nextSession",
                        "Backend Dojo on 15 August"));

        assertThat(rendered.html()).containsOnlyOnce("padding:28px;").doesNotContain("28px 28px")
                .doesNotContain("0 28px").doesNotContain("28px 0 0 28px").doesNotContain("28px 28px 0 28px");
    }

    @Test
    void sectionHeadingCarriesItsSpacingAsTopPaddingOnly() {

        var rendered = renderer.render("session-wrap-up", Map.of("sessionTitle", "Frontend Dojo", "talks", List.of()));

        assertThat(rendered.html()).contains("padding:24px 0 0 0;");
    }

    @Test
    void aSessionResourceWithAnUnsafeSchemeRendersAsTextNotALink() {

        var rendered = renderer.render("session-wrap-up", Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(),
                "resources", List.of(Map.of("title", "Internal doc", "url", "javascript:alert(1)"))));

        assertThat(rendered.html()).doesNotContain("href=\"javascript:alert(1)\"").contains("Internal doc")
                .doesNotContain(">javascript:alert(1)<");
    }

    private static Map<String, Object> actionPointFact(String title, String assignee, LocalDate dueDate, boolean done) {

        return actionPointFact(title, assignee, dueDate, done, false);
    }

    private static Map<String, Object> actionPointFact(String title, String assignee, LocalDate dueDate, boolean done,
            boolean dropped) {

        var actionPoint = new HashMap<String, Object>();
        actionPoint.put("title", title);
        actionPoint.put("assignee", assignee);
        actionPoint.put("dueDate", dueDate);
        actionPoint.put("done", done);
        actionPoint.put("dropped", dropped);
        return actionPoint;
    }

    private static Map<String, Object> checkedItemFact(String title, String assignee, String outcome,
            String outcomeKey) {

        var checkedItem = new HashMap<String, Object>();
        checkedItem.put("title", title);
        checkedItem.put("assignee", assignee);
        checkedItem.put("outcome", outcome);
        checkedItem.put("outcomeKey", outcomeKey);
        return checkedItem;
    }

    private static Map<String, Object> aCommunity() {

        return Map.of("name", "Backend Guild", "initials", "BG", "tintBackground", "#fae2eb", "tintForeground",
                "#b83468");
    }

    @Test
    void everyMailLeadsWithTheCommunityItComesFrom() {

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(), "sender", aCommunity()));

        assertThat(rendered.html()).contains("Backend Guild").contains(">BG<").contains("#fae2eb").contains("#b83468");
        assertThat(rendered.text()).contains("Backend Guild");
    }

    /**
     * The wordmark belongs to the host, not to the sender. Nobody subscribed to axonia's mail; they joined a community.
     * So the card opens with the community and the wordmark sits in the footer, below the card, where the unsubscribe
     * link already lives.
     */
    @Test
    void theAxoniaWordmarkSitsBelowTheCardRatherThanAboveTheTitle() {

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(), "sender", aCommunity()));

        // The <head><title> also resolves to "Frontend Dojo: what happened" and always precedes <body>, so the
        // comparison is scoped past </head> to pin the <h1> itself against the footer wordmark, not the title tag.
        var afterHead = rendered.html().substring(rendered.html().indexOf("</head>"));
        assertThat(afterHead.indexOf(">Frontend Dojo</h1>")).isLessThan(afterHead.indexOf(">axonia<"));
    }

    /**
     * The same rendered date line used to serve a session three days away and one three days past. The three .txt
     * templates always drew this distinction; the HTML never did.
     */
    @ParameterizedTest
    @CsvSource({"session-upcoming,Happening", "session-wrap-up,Held", "session-awaiting-close,Was on for"})
    void theDateLineSaysWhatTheDateMeans(String template, String expectedPrefix) {

        var rendered = renderer.render(template, Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(),
                "scheduledAt", Instant.parse("2026-08-06T16:00:00Z"), "sender", aCommunity()));

        assertThat(rendered.html()).contains(expectedPrefix + " Thursday, 6 August 2026, 16:00 in UTC");
    }

    /**
     * The defect the whole timezone thread exists for, pinned against the real templates. A member in Berlin used to be
     * told 16:00 UTC for a session starting at 18:00 where they sit, and had to do the arithmetic the product already
     * had every fact to do for them.
     */
    @ParameterizedTest
    @CsvSource({"session-upcoming,Happening,Happening", "session-wrap-up,Held,Held",
            "session-awaiting-close,Was on for,was scheduled for"})
    void theHourIsStatedOnTheReadersOwnClock(String template, String htmlPrefix, String textPrefix) {

        var rendered = renderer.render(template,
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(), "scheduledAt",
                        Instant.parse("2026-10-01T16:00:00Z"), "hasTime", true, "timeZone", "America/Los_Angeles",
                        "readerTimeZone", "Europe/Berlin", "sender", aCommunity()));

        var expected = " Thursday, 1 October 2026, 18:00 in Berlin (09:00 in Los Angeles)";
        assertThat(rendered.html()).contains(htmlPrefix + expected).doesNotContain("UTC");
        assertThat(rendered.text()).contains(textPrefix + expected).doesNotContain("UTC");
    }

    /** A recipient nobody has measured lands on the session's own clock, which is still not UTC. */
    @Test
    void anUnknownReaderClockLandsOnTheSessionsOwnZone() {

        var rendered = renderer.render("session-upcoming",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(), "scheduledAt",
                        Instant.parse("2026-10-01T16:00:00Z"), "hasTime", true, "timeZone", "America/Los_Angeles",
                        "sender", aCommunity()));

        assertThat(rendered.html()).contains("Happening Thursday, 1 October 2026, 09:00 in Los Angeles");
        assertThat(rendered.text()).contains("Happening Thursday, 1 October 2026, 09:00 in Los Angeles");
    }

    /**
     * The defect this pair exists to pin, and it reached MEMBERS rather than organizers. An occurrence nobody chose an
     * hour for stores midnight in its SERIES' zone, so a {@code Europe/Paris} occurrence anchored to Thursday 13 August
     * is the instant {@code 2026-08-12T22:00Z}. Formatted in UTC like every timed instant, the reminder read "Happening
     * Wednesday, 12 August 2026, 22:00 UTC": an hour nobody chose, beside the wrong DAY, while the session page said
     * "Thu, Aug 13". Both halves are asserted, in both parts, because nothing else compares the html to the txt.
     */
    @ParameterizedTest
    @CsvSource({"session-upcoming,Happening,Happening", "session-wrap-up,Held,Held",
            "session-awaiting-close,Was on for,was scheduled for"})
    void anUntimedOccurrenceNamesItsOwnDayAndNoHour(String template, String htmlPrefix, String textPrefix) {

        var rendered = renderer.render(template,
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(), "scheduledAt",
                        Instant.parse("2026-08-12T22:00:00Z"), "hasTime", false, "timeZone", "Europe/Paris", "sender",
                        aCommunity()));

        assertThat(rendered.html()).contains(htmlPrefix + " Thursday, 13 August 2026").doesNotContain("22:00")
                .doesNotContain("12 August 2026").doesNotContain("Wednesday");
        assertThat(rendered.text()).contains(textPrefix + " Thursday, 13 August 2026").doesNotContain("22:00")
                .doesNotContain("12 August 2026").doesNotContain("Wednesday");
    }

    /**
     * A fact map with no {@code hasTime} at all - a roundup's, or an outbox row stored before the key existed - keeps
     * the timed line it has always had, which is what the {@code != false} comparison in the shell buys.
     */
    @ParameterizedTest
    @CsvSource({"session-upcoming,Happening,Happening", "session-wrap-up,Held,Held",
            "session-awaiting-close,Was on for,was scheduled for"})
    void aFactMapWithNoHasTimeKeepsTheTimedLine(String template, String htmlPrefix, String textPrefix) {

        var rendered = renderer.render(template, Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(),
                "scheduledAt", Instant.parse("2026-08-06T16:00:00Z"), "sender", aCommunity()));

        assertThat(rendered.html()).contains(htmlPrefix + " Thursday, 6 August 2026, 16:00 in UTC");
        assertThat(rendered.text()).contains(textPrefix + " Thursday, 6 August 2026, 16:00 in UTC");
    }

    /** With no zone to read it on, the untimed day is the one the instant falls on in UTC. */
    @Test
    void anUntimedOccurrenceWithNoZoneFactFallsBackToUtc() {

        var rendered = renderer.render("session-upcoming", Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(),
                "scheduledAt", Instant.parse("2026-08-13T00:00:00Z"), "hasTime", false, "sender", aCommunity()));

        assertThat(rendered.html()).contains("Happening Thursday, 13 August 2026").doesNotContain("00:00");
        assertThat(rendered.text()).contains("Happening Thursday, 13 August 2026").doesNotContain("00:00");
    }

    @ParameterizedTest
    @CsvSource({"session-upcoming,Sent to members of Backend Guild.",
            "session-wrap-up,Sent to members of Backend Guild.",
            "session-awaiting-close,Sent to session hosts in Backend Guild."})
    void theFooterSaysWhyThisPersonIsReceivingThis(String template, String expectedReason) {

        var rendered = renderer.render(template,
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(), "sender", aCommunity()));

        assertThat(rendered.html()).contains(expectedReason);
        assertThat(rendered.text()).contains(expectedReason);
    }

    /**
     * A fact map with no community is not reachable in production, but the series line predates the community line and
     * must not disappear with it: before this change the series was the only thing above the title.
     */
    @Test
    void aMailWithNoCommunityStillShowsItsSeries() {

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(), "seriesTitle", "Deep dives"));

        assertThat(rendered.html()).contains("Deep dives");
    }

    /**
     * The unexpected chip is asserted absent, not merely the expected one present: {@code th:replace} outranks
     * {@code th:if} in Thymeleaf's own attribute precedence, so a condition written on the same tag as the inclusion
     * never runs at all and BOTH arms render. A positive-only assertion passes straight through that.
     */
    @ParameterizedTest
    @CsvSource({"ONE_DAY,'A day on, still open','A week on, still open'",
            "SEVEN_DAY,'A week on, still open','A day on, still open'"})
    void theNudgeSaysHowLongItHasBeenWaitingWithoutClaimingARelativeTime(String arm, String expectedChip,
            String otherArmChip) {

        var rendered = renderer.render("session-awaiting-close",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(), "sender", aCommunity(), "nudgeArm", arm));

        assertThat(rendered.html()).contains(expectedChip).doesNotContain(otherArmChip);
        assertThat(rendered.text()).contains(expectedChip).doesNotContain(otherArmChip);
    }

    /**
     * A missing arm drops the arm chip and NOTHING ELSE. The agenda count shares nudgeStrip's row but not its
     * condition: it is read off {@code talks}, which an armless row still carries. Reachable for the same reason the
     * dangling series separator is - facts are frozen at enqueue, and {@code session-awaiting-close.html} predates the
     * {@code nudgeArm} fact, so every outbox row enqueued before that commit renders exactly this state. Both halves
     * are asserted together because the failure mode here is disagreement, not absence: the .txt gates its count on
     * {@code talks} alone and would keep printing "2 talks on the agenda." while the HTML showed nothing.
     */
    @Test
    void aNudgeWithNoArmDropsTheArmChipAndKeepsTheCountInBothHalves() {

        var talks = List.of(Map.of("title", "Signals in React"), Map.of("title", "Structured concurrency"));

        var rendered = renderer.render("session-awaiting-close",
                Map.of("sessionTitle", "Frontend Dojo", "talks", talks, "sender", aCommunity()));

        assertThat(rendered.html()).doesNotContain("still open").containsOnlyOnce("2 talks");
        assertThat(rendered.text()).doesNotContain("still open").containsOnlyOnce("2 talks");
    }

    @Test
    void aNudgeWithNeitherAnArmNorAnAgendaRendersNoChipRowAtAll() {

        var rendered = renderer.render("session-awaiting-close",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(), "sender", aCommunity()));

        // padding:16px 0 0 0 is nudgeStrip's own outer cell and appears nowhere else in the shell: with neither
        // chip to carry, the row must be gone rather than rendered empty as 16px of dead vertical space.
        assertThat(rendered.html()).doesNotContain("still open").doesNotContain("talk</span>")
                .doesNotContain("talks</span>").doesNotContain("padding:16px 0 0 0;");
        assertThat(rendered.text()).doesNotContain("still open").doesNotContain("on the agenda");
    }

    @Test
    void theNudgeNamesTheCommunityThatIsWaitingOnTheWrapUp() {

        var rendered = renderer.render("session-awaiting-close", Map.of("sessionTitle", "Frontend Dojo", "talks",
                List.of(), "sender", aCommunity(), "nudgeArm", "ONE_DAY"));

        assertThat(rendered.html()).contains("Did it happen? Say so, and Backend Guild gets the wrap-up.")
                .doesNotContain("can go to the community")
                .doesNotContain("Did it happen? Say so, and the community gets the wrap-up.");
        // Nothing else guards .html/.txt drift on this template - the .txt copy was rewritten alongside the
        // .html, and PreviewMatchesSendTest only compares the render path, never the two bodies against each
        // other.
        assertThat(rendered.text()).contains(
                "Did it happen? Say so, and Backend Guild gets the wrap-up. Say it did not, and nobody hears about it again.")
                .doesNotContain("can go to the community")
                .doesNotContain("Did it happen? Say so, and the community gets the wrap-up.");
    }

    /**
     * The null-community arm of the same paragraph. Its opposite arm interpolates {@code communityName}, so an
     * inclusion that outruns its own condition renders the literal {@code null} into a member's inbox here.
     */
    @Test
    void aNudgeWithNoCommunityFallsBackToTheCommunityWithoutRenderingNull() {

        var rendered = renderer.render("session-awaiting-close",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(), "nudgeArm", "ONE_DAY"));

        assertThat(rendered.html()).contains("Did it happen? Say so, and the community gets the wrap-up.")
                .doesNotContain("null");
        assertThat(rendered.text()).contains("Did it happen? Say so, and the community gets the wrap-up.")
                .doesNotContain("null");
    }

    @Test
    void theNudgeCountsTheAgendaItAlreadyHasAndOmitsTheChipWhenThereIsNone() {

        var withTalks = renderer.render("session-awaiting-close",
                Map.of("sessionTitle", "Frontend Dojo", "talks",
                        List.of(Map.of("title", "Signals in React"), Map.of("title", "Structured concurrency")),
                        "sender", aCommunity(), "nudgeArm", "ONE_DAY"));
        var withNone = renderer.render("session-awaiting-close", Map.of("sessionTitle", "Frontend Dojo", "talks",
                List.of(), "sender", aCommunity(), "nudgeArm", "ONE_DAY"));

        // containsOnlyOnce, not contains: two arm rows each pull in talkCountCell, so a doubled chip is exactly
        // the symptom of an inclusion that outran its own condition.
        assertThat(withTalks.html()).containsOnlyOnce("2 talks");
        assertThat(withTalks.text()).containsOnlyOnce("2 talks");
        assertThat(withNone.html()).doesNotContain("talks<");
        assertThat(withNone.text()).doesNotContain("talks");
    }

    private static final String WRAP_UP_URL = "https://axonia.example/communities/backend-guild/sessions/11?org=acme";
    private static final String WRAP_UP_RECORDING = "https://video.example/11";
    private static final String WRAP_UP_THUMBNAIL = "https://cdn.axonia.example/communities/7/thumbnails/11.jpg";
    private static final String WRAP_UP_NOTE = "Two incidents, both closed. **RBAC** lands in January.\n\n"
            + "- Quota alerts page the owning team\n- Scope review before 15 December\n\n"
            + "Plan on the [RBAC page](https://wiki.example/rbac).";

    private static Map<String, Object> aWrapUpTalk(String title, String presenter, Map<String, Object> avatar,
            int morePresenterCount) {

        var talk = new HashMap<String, Object>();
        talk.put("title", title);
        talk.put("presenter", presenter);
        talk.put("presenters", presenter == null ? List.of() : List.of(Map.of("name", presenter, "initials", "XX")));
        talk.put("subjects", List.of());
        talk.put("resources", List.of());
        talk.put("description", null);
        talk.put("avatar", avatar);
        talk.put("morePresenterCount", morePresenterCount);
        return talk;
    }

    private static Map<String, Object> aQuotaTalk() {

        var talk = aWrapUpTalk("Database Quotas Alerting", "David Barbarin",
                aPresenter("David Barbarin", "DB", "#de3b78"), 0);
        talk.put("description", "How a full disk on the orders database went unnoticed for six hours.");
        talk.put("subjects", List.of("incident", "observability", "sql-database"));
        talk.put("resources",
                List.of(Map.of("title", "Runbook", "url", "https://slides.example/quota", "label", "Runbook"),
                        Map.of("title", "Database Quotas Alerting", "url", "https://www.grafana.example/d/1", "label",
                                "grafana.example")));
        return talk;
    }

    private static Map<String, Object> aFlakyTalk() {

        var talk = aWrapUpTalk("Flaky Testcontainers on CI", "Sara Keller, Tom Lee",
                aPresenter("Sara Keller", "SK", "#1f9d63"), 1);
        talk.put("subjects", List.of("testing"));
        return talk;
    }

    /** A realistic wrap-up, mirroring the shape {@code events.domain.SessionFacts} produces, written out by hand. */
    private static Map<String, Object> aWrapUp() {

        var facts = new HashMap<String, Object>();
        facts.put("sender", aCommunity());
        facts.put("sessionTitle", "S26E11");
        facts.put("seriesTitle", "Backend Guild Weekly");
        facts.put("scheduledAt", Instant.parse("2026-11-18T09:00:00Z"));
        facts.put("hasTime", true);
        facts.put("timeZone", "Europe/Zurich");
        facts.put("sessionUrl", WRAP_UP_URL);
        facts.put("recordingUrl", WRAP_UP_RECORDING);
        facts.put("durationLabel", "1h 04");
        facts.put("thumbnailUrl", WRAP_UP_THUMBNAIL);
        facts.put("tints", greenTints());
        facts.put("attendeeCount", 23);
        facts.put("talks", List.of(aQuotaTalk(),
                aWrapUpTalk("Authentication RBAC Update", "Gabriel Dinant",
                        aPresenter("Gabriel Dinant", "GD", "#6e3ceb"), 0),
                aWrapUpTalk("P1: Java4Shell CVE", null, null, 0), aFlakyTalk(),
                aWrapUpTalk("Axonia for the guild", "Lena Moser", aPresenter("Lena Moser", "LM", "#de3b78"), 0)));
        facts.put("feedbackClosesAt", Instant.parse("2026-11-25T12:00:00Z"));
        facts.put("followUpNotes", WRAP_UP_NOTE);
        facts.put("actionPoints", List.of(actionPointFact("Scope review for every service", "Gabriel Dinant",
                LocalDate.of(2026, 12, 15), false)));
        facts.put("checkedItems", List.of(checkedItemFact("Renew the certificate", "Bob", "Done", "DONE")));
        facts.put("resources", List.of(Map.of("title", "Session notes", "url", "https://axonia.example/notes/11")));
        facts.put("nextUp",
                Map.of("nextMonth", "DEC", "nextDay", "2", "nextTitle", "S26E12", "nextLabel", "Wednesday 2 December"));
        facts.put("nextSession", "2 December - S26E12");
        return facts;
    }

    private static void assertInOrder(String rendered, String... markers) {

        var from = 0;
        for (var marker : markers) {
            var at = rendered.indexOf(marker, from);
            assertThat(at).as(marker).isGreaterThanOrEqualTo(0);
            from = at + marker.length();
        }
    }

    @Test
    void theWrapUpLeadsWithTheRecordingThenTheVoteThenTheTakeawaysThenTheTalks() {

        var rendered = renderer.render("session-wrap-up", aWrapUp());

        var body = rendered.html().substring(rendered.html().indexOf("</head>"));
        assertInOrder(body, ">Watch the session</a>", ">How was it?</p>", ">Takeaways</p>", ">Talks</p>",
                ">Action points</p>", ">Checked</p>", ">From the session</p>", ">Next up</p>");
    }

    @Test
    void theBandPlaysTheRecordingWhenThereIsOne() {

        var rendered = renderer.render("session-wrap-up", aWrapUp());

        assertThat(rendered.html()).contains("<a href=\"" + WRAP_UP_RECORDING + "\" style=\"display:block;")
                .containsOnlyOnce(PLAY_GLYPH).contains(">Recording</p>").contains(">Watch the session</a>")
                .contains(">1h 04</span>").doesNotContain("Open the session");
        assertThat(rendered.html()).as("the thumbnail paints the band for every client, Outlook included")
                .contains("background=\"" + WRAP_UP_THUMBNAIL + "\"").contains("bgcolor=\"#16382d\"")
                .contains("<v:fill type=\"frame\" src=\"" + WRAP_UP_THUMBNAIL + "\" color=\"#16382d\"/>")
                .contains("style=\"width:512px;height:140px;\"");
        assertThat(rendered.text()).contains("Watch the session: " + WRAP_UP_RECORDING + "\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "javascript:alert(1)"})
    void withoutAPlayableRecordingTheBandOpensTheSession(String recordingUrl) {

        var facts = aWrapUp();
        facts.put("recordingUrl", recordingUrl);

        var rendered = renderer.render("session-wrap-up", facts);

        assertThat(rendered.html()).doesNotContain(PLAY_GLYPH).doesNotContain("Watch the session")
                .doesNotContain("href=\"javascript:").contains("<a href=\"" + WRAP_UP_URL + "\" style=\"display:block;")
                .contains(">18 November</p>").contains(">Open the session</a>");
        assertThat(rendered.text()).doesNotContain("Watch the session").doesNotContain("javascript:")
                .contains("Open the session: " + WRAP_UP_URL);
    }

    @Test
    void thePosterFooterCountsTheTalksAndTheRoomAndDropsEachAtZero() {

        var empty = aWrapUp();
        empty.put("attendeeCount", 0);
        var lone = aWrapUp();
        lone.put("talks", List.of(aQuotaTalk()));
        lone.put("attendeeCount", null);
        var talkless = aWrapUp();
        talkless.put("talks", List.of());

        var full = renderer.render("session-wrap-up", aWrapUp());
        var noRoom = renderer.render("session-wrap-up", empty);
        var oneTalk = renderer.render("session-wrap-up", lone);
        var noTalks = renderer.render("session-wrap-up", talkless);

        assertThat(visibleText(full.html())).contains(" 5 talks · 23 in the room Open session ");
        assertThat(full.text()).contains("\n5 talks, 23 in the room\n");
        assertThat(visibleText(noRoom.html())).contains(" 5 talks Open session ").doesNotContain("in the room");
        assertThat(noRoom.text()).contains("\n5 talks\n").doesNotContain("in the room");
        assertThat(visibleText(oneTalk.html())).contains(" 1 talk Open session ");
        assertThat(oneTalk.text()).contains("\n1 talk\n");
        assertThat(visibleText(noTalks.html())).contains(" 23 in the room Open session ").doesNotContain("0 talks");
        assertThat(noTalks.text()).contains("\n23 in the room\n").doesNotContain("0 talks");
        assertThat(full.html()).contains("<a href=\"" + WRAP_UP_URL + "\" style=\"color:#1f8357;");
    }

    @Test
    void theVoteIsTheOneButtonAndShowsOnlyWhileFeedbackIsOpen() {

        var closed = aWrapUp();
        closed.put("feedbackClosesAt", null);
        var sender = (Map<?, ?>) aWrapUp().get("sender");

        var open = renderer.render("session-wrap-up", aWrapUp());
        var shut = renderer.render("session-wrap-up", closed);

        assertThat(open.html()).contains(">How was it?</p>").contains(">Two taps, anonymous, open until 25 Nov</p>")
                .contains("<a href=\"" + WRAP_UP_URL + "#feedback\"").contains(">Give feedback</a>")
                .containsOnlyOnce("background-color:#de3b78;color:#ffffff;").contains("class=\"wv-btn\"")
                .contains("@media only screen and (max-width:480px) { .wv-text, .wv-btn { display:block !important;");
        for (var height : List.of(6, 9, 12, 15, 18, 21)) {
            assertThat(open.html()).containsOnlyOnce("width:5px;height:" + height + "px;font-size:0;line-height:0;"
                    + "border-radius:1px;background-color:" + sender.get("tintForeground") + ";");
        }
        assertThat(open.text())
                .contains("\n\nHow was it? Two taps, anonymous, open until 25 Nov\n" + WRAP_UP_URL + "#feedback\n");
        assertThat(shut.html()).doesNotContain("How was it?").doesNotContain("Give feedback")
                .doesNotContain("#feedback").doesNotContain("background-color:#de3b78;color:#ffffff;");
        assertThat(shut.text()).doesNotContain("How was it?").doesNotContain("#feedback");
    }

    @Test
    void theTakeawaysAreTheFormattedNote() {

        var rendered = renderer.render("session-wrap-up", aWrapUp());

        assertThat(rendered.html()).contains(">Takeaways</p>")
                .contains("<strong style=\"font-weight:700;\">RBAC</strong>").contains("<li style=\"margin:0 0 4px 0;")
                .contains(
                        "<a href=\"https://wiki.example/rbac\" style=\"color:#c32c66;text-decoration:underline;font-weight:600;\">RBAC page</a>");
        assertThat(rendered.text()).contains("""
                TAKEAWAYS
                Two incidents, both closed. RBAC lands in January.

                - Quota alerts page the owning team
                - Scope review before 15 December

                Plan on the RBAC page (https://wiki.example/rbac).

                TALKS
                """);
    }

    @Test
    void aShortNoteRendersThroughTheNoteRendererInBothTwins() {

        var facts = aWrapUp();
        facts.put("followUpNotes", "**Bold** [x](https://e.com)");

        var rendered = renderer.render("session-wrap-up", facts);

        assertThat(rendered.html()).contains("<strong").contains("href=\"https://e.com\"");
        assertThat(rendered.text()).contains("TAKEAWAYS\nBold x (https://e.com)\n");
    }

    @Test
    void onlyTheRendererSuppliesTheNoteMarkup() {

        var facts = aWrapUp();
        facts.put("followUpNotes", "");
        facts.put("followUpNotesHtml", "<script>alert(1)</script>");
        facts.put("followUpNotesText", "smuggled");

        var rendered = renderer.render("session-wrap-up", facts);

        assertThat(rendered.html()).doesNotContain("<script>").doesNotContain("Takeaways");
        assertThat(rendered.text()).doesNotContain("smuggled");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void withoutANoteThereAreNoTakeaways(String note) {

        var facts = aWrapUp();
        facts.put("followUpNotes", note);

        var rendered = renderer.render("session-wrap-up", facts);

        assertThat(rendered.html()).doesNotContain("Takeaways").doesNotContain("<strong");
        assertThat(rendered.text()).doesNotContain("TAKEAWAYS");
    }

    @Test
    void aTalkRowSaysWhoWhatAndWhereToReadMore() {

        var rendered = renderer.render("session-wrap-up", aWrapUp());

        assertThat(rendered.html()).contains(">Database Quotas Alerting</p>").contains(">David Barbarin</p>")
                .contains(">How a full disk on the orders database went unnoticed for six hours.</p>")
                .contains("color:#207651;\">incident</span>").contains("color:#207651;\">observability</span>")
                .contains("color:#207651;margin-right:12px;\">sql-database</span>")
                .contains("background-color:#ebf6f1;").contains("<a href=\"https://slides.example/quota\"")
                .contains(">↗ Runbook</a>").contains("<a href=\"https://www.grafana.example/d/1\"")
                .contains(">↗ grafana.example</a>").contains(">DB</div>").contains(">SK</div>")
                .contains("color:#786a7e;\">+1</span>").contains(">Sara Keller, Tom Lee</p>");
        var lastRow = rendered.html().substring(rendered.html().indexOf(">Flaky Testcontainers on CI</p>"));
        lastRow = lastRow.substring(lastRow.indexOf("</tr>"), lastRow.indexOf("</table>"));
        assertThat(rendered.html().split("border-bottom:1px dashed #ede4e9;", -1)).as("a rule under four of five rows")
                .hasSize(9);
        assertThat(lastRow).contains("Axonia for the guild").doesNotContain("dashed");
        assertThat(rendered.text()).contains("""
                TALKS
                Database Quotas Alerting (David Barbarin)
                  How a full disk on the orders database went unnoticed for six hours.
                  incident, observability, sql-database
                  Runbook: https://slides.example/quota
                  grafana.example: https://www.grafana.example/d/1

                Authentication RBAC Update (Gabriel Dinant)
                """);
    }

    @Test
    void aTalkNobodyPresentedSaysPresenterUnknownAndDrawsNoAvatar() {

        var rendered = renderer.render("session-wrap-up", aWrapUp());

        var row = rendered.html().substring(rendered.html().indexOf(">P1: Java4Shell CVE</p>") - 600,
                rendered.html().indexOf(">P1: Java4Shell CVE</p>"));
        assertThat(rendered.html()).contains(">Presenter unknown</p>");
        assertThat(row).doesNotContain("border-radius:50%");
        assertThat(rendered.text()).contains("\nP1: Java4Shell CVE (Presenter unknown)\n");
        assertThat(rendered.html() + rendered.text()).doesNotContain("We did not catch");
    }

    @Test
    void aTalkResourceWithNoLabelFallsBackToItsTitleOrItsUrl() {

        var talk = aWrapUpTalk("It was a beautiful topic", "Ada", null, 0);
        talk.put("resources",
                List.of(Map.of("title", " It Was A Beautiful Topic ", "url", "https://axonia.example/slides/1"),
                        Map.of("title", "Slide deck", "url", "https://axonia.example/slides/2"),
                        Map.of("title", "Internal doc", "url", "javascript:alert(1)")));

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(talk)));

        assertThat(rendered.html()).contains(">↗ https://axonia.example/slides/1</a>")
                .doesNotContain("It Was A Beautiful Topic").contains(">↗ Slide deck</a>")
                .doesNotContain("href=\"javascript:").contains(">Internal doc</span>");
        assertThat(rendered.text()).contains("\n  https://axonia.example/slides/1\n")
                .contains("\n  Slide deck: https://axonia.example/slides/2\n");
    }

    @Test
    void nextUpIsTheCalendarChip() {

        var rendered = renderer.render("session-wrap-up", aWrapUp());

        assertThat(rendered.html()).containsOnlyOnce(">Next up</p>").contains(">DEC</td>").contains(">2</td>")
                .contains(">S26E12</p>").contains(">Wednesday 2 December</p>").contains("border:2px dashed #dbefe6;")
                .doesNotContain("2 December - S26E12");
        assertThat(rendered.text()).contains("\n\nNEXT UP\nS26E12, Wednesday 2 December\n")
                .doesNotContain("2 December - S26E12");
    }

    @Test
    void thePlainTextStatesTheSameFactsInTheSameOrder() {

        var rendered = renderer.render("session-wrap-up", aWrapUp());

        assertInOrder(rendered.text(), "Watch the session: " + WRAP_UP_RECORDING, "Open the session: " + WRAP_UP_URL,
                "5 talks, 23 in the room", "How was it? Two taps, anonymous, open until 25 Nov",
                WRAP_UP_URL + "#feedback", "TAKEAWAYS", "TALKS", "Database Quotas Alerting (David Barbarin)",
                "  How a full disk", "  incident, observability, sql-database",
                "  Runbook: https://slides.example/quota", "ACTION POINTS",
                "Scope review for every service - Gabriel Dinant, due 15 Dec", "CHECKED", "FROM THE SESSION",
                "Session notes: https://axonia.example/notes/11", "NEXT UP", "Sent to members of Backend Guild.");
        for (var prefix : List.of("https://video.example/", "https://axonia.example/", "https://slides.example/",
                "https://www.grafana.example/", "https://wiki.example/")) {
            assertThat(linksIn(rendered.html(), prefix)).as(prefix).isNotEmpty()
                    .isEqualTo(linksIn(rendered.text(), prefix));
        }
    }

    /**
     * A wrap-up frozen into the outbox before the poster carries none of its facts: no tints, no thumbnail, no nextUp,
     * no duration, and talks and resources without their new keys. It still renders in the new layout.
     */
    @Test
    void aWrapUpFrozenBeforeThePosterStillRendersInTheNewLayout() {

        var talk = new HashMap<String, Object>();
        talk.put("title", "Signals in React");
        talk.put("presenter", "Ada Lovelace, Bob Martin");
        talk.put("presenters", List.of(Map.of("name", "Ada Lovelace", "initials", "AL"),
                Map.of("name", "Bob Martin", "initials", "BM")));
        talk.put("subjects", List.of("react"));
        talk.put("durationMinutes", 30);
        talk.put("resources", List.of(Map.of("title", "Slides", "url", "https://slides.example/signals")));
        var facts = new HashMap<String, Object>();
        facts.put("sender", aCommunity());
        facts.put("sessionTitle", "Frontend Dojo");
        facts.put("scheduledAt", Instant.parse("2026-08-15T18:30:00Z"));
        facts.put("timeZone", "UTC");
        facts.put("sessionUrl", WRAP_UP_URL);
        facts.put("recordingUrl", WRAP_UP_RECORDING);
        facts.put("attendeeCount", 9);
        facts.put("talks", List.of(talk));
        facts.put("followUpNotes", "Great turnout.");
        facts.put("nextSession", "Saturday 12 September");

        var rendered = renderer.render("session-wrap-up", facts);

        assertThat(rendered.html()).doesNotContain("null").doesNotContain("v:rect").doesNotContain("background=\"")
                .contains("background-image:linear-gradient(120deg,#1b1420 0%,#312835 65%,#786a7e 130%)")
                .contains(">Signals in React</p>").contains(">AL</div>").contains("color:#786a7e;\">+1</span>")
                .contains("color:#5e5264;margin-right:12px;\">react</span>").contains(">↗ Slides</a>")
                .contains(">Great turnout.</p>").contains(">Next up</p>").contains(">Saturday 12 September</p>")
                .doesNotContain(">DEC</td>");
        assertThat(rendered.text()).doesNotContain("null").contains("Signals in React (Ada Lovelace, Bob Martin)")
                .contains("  Slides: https://slides.example/signals").contains("NEXT UP\nSaturday 12 September");
    }

    @Test
    void theWrapUpDropsItsOldHeadingsAndSpeaksWithoutWeOrYou() {

        var rendered = renderer.render("session-wrap-up", aWrapUp());
        var talkless = renderer.render("session-wrap-up", Map.of("sessionTitle", "Frontend Dojo", "talks", List.of()));

        for (var copy : List.of(rendered.html(), rendered.text(), talkless.html(), talkless.text())) {
            assertThat(copy).doesNotContain("What we agreed to do").doesNotContain("What happened")
                    .doesNotContain("What was checked").doesNotContain("From the organizer")
                    .doesNotContain("We did not catch").doesNotContain("Recording available")
                    .doesNotContain("Coming up next");
        }
        for (var copy : List.of(visibleText(rendered.html()), visibleText(talkless.html()), talkless.text())) {
            assertThat(copy.toLowerCase(Locale.ROOT)).doesNotContainPattern("\\b(we|us|our|you|your)\\b");
        }
    }

    /** Gmail clips past about 102 KB: twelve talks with three links each, a long note and ten action points. */
    @Test
    void theBusiestWrapUpStaysUnderGmailsClip() {

        var facts = aWrapUp();
        facts.put("talks", IntStream.range(0, 12).mapToObj(index -> {
            var talk = aQuotaTalk();
            talk.put("title", "Cloud deploy 3.0 and the database default change, talk " + index);
            talk.put("description", "x".repeat(199) + "…");
            talk.put("resources",
                    IntStream.range(0, 3)
                            .mapToObj(link -> Map.<String, Object>of("title", "Postmortem " + link, "url",
                                    "https://confluence.example/wiki/spaces/ENG/pages/123456789" + link, "label",
                                    "Postmortem " + link))
                            .toList());
            return (Map<String, Object>) talk;
        }).toList());
        facts.put("followUpNotes", "- **Point** with a [link](https://wiki.example/page) and words\n".repeat(50));
        facts.put("actionPoints",
                IntStream.range(0, 10).mapToObj(index -> actionPointFact("Scope review for every service " + index,
                        "Gabriel Dinant", LocalDate.of(2026, 12, 15), false)).toList());

        var rendered = renderer.render("session-wrap-up", facts);

        assertThat(((String) facts.get("followUpNotes")).length()).isGreaterThanOrEqualTo(3000);
        assertThat(rendered.html().getBytes(StandardCharsets.UTF_8).length).isLessThan(90_000);
    }

    private static final String RECORDING = "https://video.example/41";
    private static final String DOJO_RECORDING = "https://video.example/50";
    private static final String HEXAGONS_URL = "https://axonia.example/communities/backend-guild/sessions/41?org=acme";
    private static final String NAMELESS_URL = "https://axonia.example/communities/backend-guild/sessions/42?org=acme";
    private static final String TOKENS_URL = "https://axonia.example/communities/frontend-dojo/sessions/50?org=acme";
    private static final String THUMBNAIL = "https://cdn.axonia.example/communities/7/roundup/0b5e.jpg";
    private static final String PLAY_GLYPH = "&#9654;";

    private static Map<String, Object> anOrganization() {

        return Map.of("name", "Acme Corp", "initials", "AC", "tintBackground", "#f6f0ec", "tintForeground", "#211a26");
    }

    private static Map<String, Object> aPresenter(String name, String initials, String tint) {

        return Map.of("name", name, "initials", initials, "tint", tint);
    }

    /** {@code GuildTints.of("#1f9d63")}, written out: this class knows templates and fact keys only. */
    private static Map<String, Object> greenTints() {

        return Map.of("accent", "#1f9d63", "blockBackground", "#eff8f4", "blockBorder", "#dbefe6", "bandDark",
                "#16382d", "chipBackground", "#ebf6f1", "chipText", "#207651", "linkText", "#1f8357");
    }

    /** {@code GuildTints.of(null)}: the neutral grey a community without an accent is dressed in. */
    private static Map<String, Object> neutralTints() {

        return Map.of("accent", "#786a7e", "blockBackground", "#f6f5f6", "blockBorder", "#e9e7ea", "bandDark",
                "#312835", "chipBackground", "#f3f2f3", "chipText", "#5e5264", "linkText", "#675a6c");
    }

    private static Map<String, Object> aTalk(String title, Map<String, Object> presenter, int morePresenterCount,
            String tag) {

        var talk = new HashMap<String, Object>();
        talk.put("title", title);
        talk.put("presenter", presenter);
        talk.put("morePresenterCount", morePresenterCount);
        talk.put("tag", tag);
        return talk;
    }

    /** The busy poster: a recording, a thumbnail, a co-presented talk, people going and things kept. */
    private static Map<String, Object> aHexagonsPoster() {

        var poster = new HashMap<String, Object>();
        poster.put("dateLabel", "13 May");
        poster.put("title", "Hexagons in anger");
        poster.put("durationLabel", "1h 12");
        poster.put("recordingUrl", RECORDING);
        poster.put("sessionUrl", HEXAGONS_URL);
        poster.put("thumbnailUrl", THUMBNAIL);
        poster.put("talks",
                List.of(aTalk("Ports and adapters", aPresenter("Alice Dubois", "AD", "#de3b78"), 1, "hexagonal"),
                        aTalk("Testing the seam", aPresenter("Bob Martin", "BM", "#6e3ceb"), 0, null)));
        poster.put("moreTalkCount", 0);
        poster.put("goingCount", 31);
        poster.put("resourceGroups",
                List.of(Map.of("label", "Repo", "count", 3), Map.of("label", "Postmortem", "count", 2)));
        return poster;
    }

    /** The bare poster: a series occurrence nobody named, with no recording, no agenda and nothing kept. */
    private static Map<String, Object> aNamelessPoster() {

        var poster = new HashMap<String, Object>();
        poster.put("dateLabel", "2 June");
        poster.put("title", null);
        poster.put("durationLabel", null);
        poster.put("recordingUrl", null);
        poster.put("sessionUrl", NAMELESS_URL);
        poster.put("thumbnailUrl", null);
        poster.put("talks", List.of());
        poster.put("moreTalkCount", 0);
        poster.put("goingCount", 0);
        poster.put("resourceGroups", List.of());
        return poster;
    }

    private static Map<String, Object> aTokensPoster() {

        var poster = new HashMap<String, Object>();
        poster.put("dateLabel", "9 April");
        poster.put("title", "Design tokens");
        poster.put("durationLabel", "58 min");
        poster.put("recordingUrl", DOJO_RECORDING);
        poster.put("sessionUrl", TOKENS_URL);
        poster.put("thumbnailUrl", null);
        poster.put("talks", List.of(aTalk("Tokens in anger", aPresenter("Dana Rue", "DR", "#6e3ceb"), 0, "design")));
        poster.put("moreTalkCount", 0);
        poster.put("goingCount", 12);
        poster.put("resourceGroups", List.of());
        return poster;
    }

    /** The busy card: a hero thumbnail, three presenters, a session the cap left out and a future of its own. */
    private static Map<String, Object> aBackendGuildCard() {

        var card = new HashMap<String, Object>();
        card.put("name", "Backend Guild");
        card.put("initials", "BG");
        card.put("badgeUrl", null);
        card.put("tints", greenTints());
        card.put("presenters", List.of(aPresenter("Alice Dubois", "AD", "#de3b78"),
                aPresenter("Bob Martin", "BM", "#6e3ceb"), aPresenter("Carol Ng", "CN", "#1f9d63")));
        card.put("morePresenterCount", 0);
        card.put("sessionCountLabel", "3 sessions");
        card.put("sessions", List.of(aHexagonsPoster(), aNamelessPoster()));
        card.put("moreSessionsLabel", "+ S26E07 · 15 July");
        card.put("nextTitle", "Signals in React");
        card.put("nextLabel", "Tuesday 14 July");
        card.put("nextMonth", "JUL");
        card.put("nextDay", "14");
        return card;
    }

    /** The quiet card: no accent, no hero, and nothing coming. */
    private static Map<String, Object> aFrontendDojoCard() {

        var card = new HashMap<String, Object>();
        card.put("name", "Frontend Dojo");
        card.put("initials", "FD");
        card.put("badgeUrl", null);
        card.put("tints", neutralTints());
        card.put("presenters", List.of(aPresenter("Dana Rue", "DR", "#6e3ceb")));
        card.put("morePresenterCount", 0);
        card.put("sessionCountLabel", "1 session");
        card.put("sessions", List.of(aTokensPoster()));
        card.put("moreSessionsLabel", null);
        card.put("nextTitle", null);
        card.put("nextLabel", null);
        card.put("nextMonth", null);
        card.put("nextDay", null);
        return card;
    }

    /**
     * A realistic quarter, mirroring the shape {@code roundup.domain.RoundupFacts} produces. Hand-written rather than
     * built by calling that class: this test class is deliberately the one place that knows only about templates and
     * fact keys.
     */
    private static Map<String, Object> aRoundup() {

        var facts = new HashMap<String, Object>();
        facts.put("sender", anOrganization());
        facts.put("sessionTitle", "Q2 2026");
        facts.put("periodLabel", "April to June 2026");
        facts.put("quarterLabel", "Q2");
        facts.put("yearLabel", "2026");
        facts.put("sessionCount", 12);
        facts.put("talkCount", 34);
        facts.put("presenterCount", 3);
        facts.put("resourceCount", 47);
        facts.put("firstTimeContributorCount", 4);
        facts.put("topics", List.of(Map.of("label", "hexagonal", "weight", 5), Map.of("label", "testing", "weight", 3),
                Map.of("label", "kafka", "weight", 1)));
        facts.put("communities", List.of(aBackendGuildCard(), aFrontendDojoCard()));
        facts.put("moreCommunitiesCount", 3);
        facts.put("openTalkCount", 1);
        facts.put("communitiesUrl", "https://axonia.example/communities?org=acme");
        return facts;
    }

    private static Map<String, Object> aRoundupOf(Map<String, Object> card) {

        var facts = aRoundup();
        facts.put("communities", List.of(card));
        return facts;
    }

    private static Map<String, Object> aCardOf(Map<String, Object> poster) {

        var card = aBackendGuildCard();
        card.put("sessions", List.of(poster));
        return card;
    }

    /** What a reader sees of the HTML: tags dropped, entities a template emits decoded, whitespace collapsed. */
    private static String visibleText(String html) {

        return html.substring(html.indexOf("<body")).replaceAll("(?s)<!--.*?-->", "").replaceAll("<[^>]+>", "")
                .replace("&middot;", "·").replace("&amp;", "&").replace("&#39;", "'").replaceAll("\\s+", " ");
    }

    private static List<String> linksIn(String rendered, String prefix) {

        return Pattern.compile(Pattern.quote(prefix) + "[^\\s\"'<)]*").matcher(rendered.replace("&amp;", "&")).results()
                .map(MatchResult::group).distinct().sorted().toList();
    }

    @Test
    void aRoundupComesFromTheOrganizationAndShowsTheQuarterAsDisplayType() {

        var rendered = renderer.render("organization-roundup", aRoundup());

        assertThat(rendered.subject()).isEqualTo("April to June 2026 across the communities");
        assertThat(rendered.html()).contains("Acme Corp").contains(">AC<")
                .contains(">Q2<span style=\"color:#de3b78;\">.</span>2026</h1>").doesNotContain(">Q2 2026</h1>")
                .doesNotContain("Covering April to June 2026").contains("April to June 2026: 12 sessions");
        assertThat(rendered.text()).contains("Acme Corp").contains("Q2 2026").contains("Covering April to June 2026.")
                .doesNotContain("<");
    }

    @Test
    void theLetterOpensWithOneSentenceOfHighlightedFigures() {

        var rendered = renderer.render("organization-roundup", aRoundup());

        var sentence = "34 talks by 3 people over 12 sessions, and 47 things worth keeping.";
        assertThat(visibleText(rendered.html())).contains(sentence);
        assertThat(rendered.html()).contains("#ffd0e1 62%);\">34 talks</span>")
                .contains("#ffd0e1 62%);\">47 things</span>");
        assertThat(rendered.text()).contains(sentence).doesNotContain("No sessions were held over the quarter.");
    }

    /**
     * A tenant's first roundup is routinely one of each. Every plural arm is asserted absent too, because both arms of
     * a singular/plural branch rendering at once is a failure this codebase has already shipped.
     */
    @Test
    void aQuarterOfOneOfEachReadsAsEnglishRatherThanAsDatabaseOutput() {

        var facts = aRoundup();
        facts.put("sessionCount", 1);
        facts.put("talkCount", 1);
        facts.put("presenterCount", 1);
        facts.put("resourceCount", 1);
        facts.put("firstTimeContributorCount", 1);
        facts.put("moreCommunitiesCount", 1);
        facts.put("openTalkCount", 1);

        var rendered = renderer.render("organization-roundup", facts);

        var sentence = "1 talk by 1 person over 1 session, and 1 thing worth keeping.";
        for (var copy : List.of(visibleText(rendered.html()), rendered.text())) {
            assertThat(copy).contains(sentence).contains("1 first time presenter").contains("1 talk still wanted")
                    .contains("and 1 more community was active.").doesNotContain("first time presenters")
                    .doesNotContain("talks still wanted").doesNotContain("more communities were active");
        }
    }

    @Test
    void aFigureWithNothingToSayDropsItsClauseRatherThanPrintingAZero() {

        var nothingKept = aRoundup();
        nothingKept.put("resourceCount", 0);
        var discussionsOnly = aRoundup();
        discussionsOnly.put("talkCount", 0);
        discussionsOnly.put("presenterCount", 0);

        var kept = renderer.render("organization-roundup", nothingKept);
        var discussed = renderer.render("organization-roundup", discussionsOnly);

        for (var copy : List.of(visibleText(kept.html()), kept.text())) {
            assertThat(copy).contains("34 talks by 3 people over 12 sessions.").doesNotContain("worth keeping");
        }
        for (var copy : List.of(visibleText(discussed.html()), discussed.text())) {
            assertThat(copy).contains("12 sessions, and 47 things worth keeping.").doesNotContain("0 talks")
                    .doesNotContain("0 people");
        }
    }

    /** A quarter the letter can name no session in says so in words, and the first-timer count goes with it. */
    @Test
    void aQuarterThatHeldNothingSaysSoAndDropsTheFirstTimerPill() {

        var facts = aRoundup();
        facts.put("sessionCount", 0);
        facts.put("talkCount", 0);
        facts.put("presenterCount", 0);
        facts.put("resourceCount", 0);
        facts.put("topics", List.of());
        facts.put("communities", List.of());
        facts.put("moreCommunitiesCount", 0);
        facts.put("firstTimeContributorCount", 3);

        var rendered = renderer.render("organization-roundup", facts);

        for (var copy : List.of(visibleText(rendered.html()), rendered.text())) {
            assertThat(copy).contains("No sessions were held over the quarter.").doesNotContain("0 sessions")
                    .doesNotContain("first time presenter").doesNotContain("null");
        }
    }

    @Test
    void firstTimePresentersAreACountInAPillAndNeverNames() {

        var rendered = renderer.render("organization-roundup", aRoundup());

        assertThat(rendered.html()).contains("color:#c32c66;\">4 first time presenters</span>");
        assertThat(rendered.text()).contains("4 first time presenters");
    }

    /** The cloud stays; its "What we talked about" heading goes, in both halves. */
    @Test
    void theTopicCloudSizesEachWordByItsWeightAndCarriesNoHeading() {

        var untagged = aRoundup();
        untagged.put("topics", List.of());

        var rendered = renderer.render("organization-roundup", aRoundup());
        var flat = renderer.render("organization-roundup", untagged);

        assertThat(rendered.html()).contains("font-size:26px; color:#de3b78;\">hexagonal</span>")
                .contains("font-size:18px; color:#211a26;\">testing</span>")
                .contains("font-size:13px; color:#786a7e;\">kafka</span>").doesNotContain("What we talked about")
                .doesNotContain("What happened");
        assertThat(rendered.text()).contains("Topics: hexagonal, testing, kafka")
                .doesNotContain("What we talked about");
        assertThat(flat.html()).doesNotContain("font-size:26px; color:#de3b78;");
        assertThat(flat.text()).doesNotContain("Topics:");
    }

    /** Badges carry involvement in the HTML; the plain text names everybody in full. */
    @Test
    void involvementIsBadgesInTheHtmlAndNamesInFullInThePlainText() {

        var rendered = renderer.render("organization-roundup", aRoundup());

        assertThat(rendered.html()).contains(">AD</div>").contains(">BM</div>").contains(">CN</div>")
                .contains(">DR</div>").doesNotContain("Alice Dubois").doesNotContain("Carol Ng")
                .doesNotContain("Dana Rue");
        assertThat(rendered.text()).contains("Presented by Alice Dubois, Bob Martin, Carol Ng.")
                .contains("Presented by Dana Rue.").doesNotContain("<");
    }

    @Test
    void aCommunityWithMorePresentersThanFitStatesTheRemainder() {

        var card = aBackendGuildCard();
        card.put("morePresenterCount", 2);

        var capped = renderer.render("organization-roundup", aRoundupOf(card));
        var whole = renderer.render("organization-roundup", aRoundup());

        assertThat(capped.html()).contains(">+2</div");
        assertThat(capped.text()).contains("Presented by Alice Dubois, Bob Martin, Carol Ng, and 2 more.");
        assertThat(whole.html()).doesNotContain(">+0</div").doesNotContain(">+2</div");
    }

    @Test
    void aCommunityThatHeldOnlyDiscussionsRendersABlockWithNoAvatarRow() {

        var card = aBackendGuildCard();
        card.put("presenters", List.of());

        var rendered = renderer.render("organization-roundup", aRoundupOf(card));

        assertThat(rendered.html()).contains("Backend Guild").doesNotContain(">CN</div>").doesNotContain("null");
        assertThat(rendered.text()).contains("Backend Guild").doesNotContain("Presented by").doesNotContain("null");
    }

    @Test
    void aCommunityBlockIsDressedInItsOwnTints() {

        var rendered = renderer.render("organization-roundup", aRoundup());

        assertThat(rendered.html()).contains("bgcolor=\"#eff8f4\"").contains("border:1px solid #dbefe6")
                .contains("bgcolor=\"#f6f5f6\"").contains("border:1px solid #e9e7ea")
                .contains("background-color:#ebf6f1;color:#207651;\">hexagonal</span>")
                .contains("color:#675a6c;\">FD</div>").contains("color:#1f8357;\">BG</div>").doesNotContain("color-mix")
                .doesNotContain("<svg");
    }

    @Test
    void aBadgeIsAnImageOnlyWhenTheFactCarriesAFetchableUrl() {

        var key = aBackendGuildCard();
        key.put("badgeUrl", "communities/7/badge.png");
        var url = aBackendGuildCard();
        url.put("badgeUrl", "https://cdn.axonia.example/communities/7/badge.png");

        var monogram = renderer.render("organization-roundup", aRoundupOf(key));
        var image = renderer.render("organization-roundup", aRoundupOf(url));

        assertThat(monogram.html()).contains(">BG</div>").doesNotContain("<img")
                .doesNotContain("communities/7/badge.png");
        assertThat(image.html()).contains("<img src=\"https://cdn.axonia.example/communities/7/badge.png\"")
                .doesNotContain(">BG</div>");
    }

    /** A recorded session's whole band opens its recording, behind a play disc drawn in text, never an image. */
    /**
     * A roundup enqueued before the posters is frozen in the old shape: no tints, no quarterLabel, session rows with
     * talkTitles and resources. Delivery still renders it, so the template must fall back rather than throw.
     */
    @Test
    void aRoundupFrozenBeforeThePostersStillRenders() {

        var presenter = Map.<String, Object>of("name", "Alice Dubois", "initials", "AD", "tint", "#7c5cff");
        var session = new HashMap<String, Object>();
        session.put("dateLabel", "13 May");
        session.put("title", "Hexagons in anger");
        session.put("talkTitles", List.of("Ports and adapters"));
        session.put("resources", List.of(Map.of("title", "Slides", "url", "https://example.test/slides")));
        var card = new HashMap<String, Object>();
        card.put("name", "Backend Guild");
        card.put("initials", "BG");
        card.put("badgeUrl", null);
        card.put("accentColor", "#1f9d63");
        card.put("summaryParts", List.of("2 sessions", "1 resource"));
        card.put("presenters", List.of(presenter));
        card.put("morePresenterCount", 0);
        card.put("sessions", List.of(session));
        card.put("moreSessionCount", 1);
        card.put("nextTitle", "Event sourcing");
        card.put("nextLabel", "Wednesday 14 October");
        card.put("moreUpcomingCount", 0);
        var facts = aRoundup();
        facts.remove("quarterLabel");
        facts.remove("yearLabel");
        facts.put("communities", List.of(card));

        var rendered = renderer.render("organization-roundup", facts);

        assertThat(visibleText(rendered.html())).contains("Q2 2026").contains("Backend Guild")
                .contains("Hexagons in anger").contains("Event sourcing");
        assertThat(rendered.html()).contains("background-color:#f6f5f6;");
        assertThat(rendered.text()).contains("  13 May: Hexagons in anger\n").doesNotContain("Session: null");
    }

    @Test
    void aRecordedSessionsBandPlaysItsRecording() {

        var rendered = renderer.render("organization-roundup", aRoundupOf(aCardOf(aHexagonsPoster())));

        assertThat(rendered.html()).contains("<a href=\"" + RECORDING + "\" style=\"display:block;")
                .containsOnlyOnce(PLAY_GLYPH).contains(">13 May</p>").contains(">1h 12</span>");
        assertThat(rendered.html()).as("desktop Outlook drops the block link, so the disc and title link on their own")
                .contains("<a href=\"" + RECORDING
                        + "\" style=\"color:#ffffff;text-decoration:none;\">Hexagons in anger</a>")
                .contains("<a href=\"" + RECORDING + "\" style=\"color:#211a26;text-decoration:none;\">" + PLAY_GLYPH);
        assertThat(rendered.text()).contains("  13 May: Hexagons in anger (1h 12)\n    Watch: " + RECORDING + "\n");
    }

    /** Without a recording there is nothing to play: no disc, no "Watch:", and the band opens the session instead. */
    @Test
    void aSessionWithoutARecordingHasNoPlayDiscAndItsBandOpensTheSession() {

        var rendered = renderer.render("organization-roundup", aRoundupOf(aCardOf(aNamelessPoster())));

        assertThat(rendered.html()).doesNotContain(PLAY_GLYPH)
                .contains("<a href=\"" + NAMELESS_URL.replace("&", "&amp;") + "\" style=\"display:block;")
                .contains(">2 June</a></p>").doesNotContain("null");
        assertThat(rendered.text()).doesNotContain("Watch:").contains("  2 June\n")
                .contains("    Session: " + NAMELESS_URL + "\n");
    }

    /** Desktop Outlook ignores CSS backgrounds, so the thumbnail is also a VML frame, over the same dark mix. */
    @Test
    void aThumbnailPaintsTheBandForEveryClientIncludingOutlook() {

        var rendered = renderer.render("organization-roundup", aRoundupOf(aCardOf(aHexagonsPoster())));

        assertThat(rendered.html()).contains("background=\"" + THUMBNAIL + "\"").contains("bgcolor=\"#16382d\"")
                .contains("background-image:url(&#39;" + THUMBNAIL + "&#39;)")
                .contains("<!--[if gte mso 9]><v:rect xmlns:v=\"urn:schemas-microsoft-com:vml\"")
                .contains("<v:fill type=\"frame\" src=\"" + THUMBNAIL + "\" color=\"#16382d\"/>")
                .contains("</v:textbox></v:rect><![endif]-->");
    }

    @Test
    void aBandWithoutAThumbnailIsTheAccentGradientAndNoVml() {

        var rendered = renderer.render("organization-roundup", aRoundupOf(aCardOf(aNamelessPoster())));

        assertThat(rendered.html()).contains("bgcolor=\"#16382d\"")
                .contains("background-image:linear-gradient(120deg,#1b1420 0%,#16382d 65%,#1f9d63 130%)")
                .doesNotContain("background=\"").doesNotContain("v:rect");
    }

    @Test
    void aPosterListsItsTalksWithFacesAndTagsAndCountsWhatItKept() {

        var rendered = renderer.render("organization-roundup", aRoundupOf(aCardOf(aHexagonsPoster())));

        var html = visibleText(rendered.html());
        assertThat(html).contains("Ports and adapters").contains("Testing the seam").contains("31 going")
                .contains("Repo 3 · Postmortem 2").contains("Open session");
        assertThat(rendered.html()).contains("color:#786a7e;\">+1</span>")
                .containsOnlyOnce("color:#207651;\">hexagonal</span>").contains("Ports and adapters <span")
                .contains(".rp-tag { float:right !important;");
        assertThat(rendered.text()).contains("""
                    Ports and adapters (Alice Dubois and 1 more)
                    Testing the seam (Bob Martin)
                    31 going
                    Repo 3, Postmortem 2
                    Session: %s
                """.formatted(HEXAGONS_URL));
    }

    @Test
    void aPosterWithMoreTalksThanFitSaysHowManyItLeftOut() {

        var many = aHexagonsPoster();
        many.put("moreTalkCount", 3);
        var one = aHexagonsPoster();
        one.put("moreTalkCount", 1);

        var several = renderer.render("organization-roundup", aRoundupOf(aCardOf(many)));
        var single = renderer.render("organization-roundup", aRoundupOf(aCardOf(one)));

        assertThat(visibleText(several.html())).contains("+ 3 more talks");
        assertThat(several.text()).contains("    + 3 more talks\n");
        assertThat(visibleText(single.html())).contains("+ 1 more talk ").doesNotContain("+ 1 more talks");
        assertThat(single.text()).contains("    + 1 more talk\n");
    }

    /** With nothing kept the take-away strip goes and "Open session" moves up into the signals line. */
    @Test
    void aPosterThatKeptNothingStillLinksToItsSessionOnce() {

        var rendered = renderer.render("organization-roundup", aRoundupOf(aCardOf(aNamelessPoster())));

        assertThat(rendered.html()).containsOnlyOnce(">Open session</a>").doesNotContain(" going")
                .doesNotContain("border-top:1px solid #ede4e9");
    }

    /** The two renderings must agree on every link a reader can follow; nothing in the gate compares them. */
    @Test
    void theHtmlAndThePlainTextCarryTheSameRecordingsAndSessions() {

        var rendered = renderer.render("organization-roundup", aRoundup());

        for (var prefix : List.of("https://video.example/", "https://axonia.example/communities/")) {
            assertThat(linksIn(rendered.html(), prefix)).as(prefix).isNotEmpty()
                    .isEqualTo(linksIn(rendered.text(), prefix));
        }
        assertThat(linksIn(rendered.text(), "https://video.example/")).containsExactly(RECORDING, DOJO_RECORDING);
        assertThat(linksIn(rendered.text(), "https://axonia.example/communities/")).containsExactly(HEXAGONS_URL,
                NAMELESS_URL, TOKENS_URL);
    }

    @Test
    void aCappedCommunityNamesTheFirstSessionItLeftOut() {

        var rendered = renderer.render("organization-roundup", aRoundup());

        assertThat(rendered.html()).containsOnlyOnce(">+ S26E07 · 15 July</td>");
        assertThat(rendered.text()).containsOnlyOnce("  + S26E07 · 15 July\n");
    }

    @Test
    void aCommunitysNextSessionIsACalendarChipOnItsOwnBlock() {

        var nameless = aBackendGuildCard();
        nameless.put("nextTitle", null);

        var rendered = renderer.render("organization-roundup", aRoundup());
        var untitled = renderer.render("organization-roundup", aRoundupOf(nameless));

        assertThat(rendered.html()).containsOnlyOnce(">Next up</p>").contains(">JUL</td>").contains(">14</td>")
                .contains(">Signals in React</p>").contains(">Tuesday 14 July</p>");
        assertThat(rendered.text()).containsOnlyOnce("Next up ")
                .contains("  Next up Signals in React, Tuesday 14 July.");
        assertThat(untitled.html()).contains(">Tuesday 14 July</p>").doesNotContain("Untitled").doesNotContain("null");
        assertThat(untitled.text()).contains("  Next up Tuesday 14 July.").doesNotContain("null");
    }

    @Test
    void aQuarterOfTwoCommunitiesIsTwoBlocksAndSaysHowManyMoreWereActive() {

        var capped = renderer.render("organization-roundup", aRoundup());
        var whole = aRoundup();
        whole.put("moreCommunitiesCount", 0);
        var complete = renderer.render("organization-roundup", whole);

        assertThat(capped.html()).containsOnlyOnce("Backend Guild").containsOnlyOnce("Frontend Dojo")
                .contains("and 3 more communities were active.");
        assertThat(capped.text()).containsOnlyOnce("Backend Guild").containsOnlyOnce("Frontend Dojo")
                .contains("and 3 more communities were active.");
        assertThat(complete.html()).doesNotContain("were active").doesNotContain("was active");
        assertThat(complete.text()).doesNotContain("were active").doesNotContain("was active");
    }

    /** The open talks are the one button in the letter, on a dark closing card; with none open there is no ask. */
    @Test
    void theOpenTalksAreTheOneClosingAskAndDisappearAtZero() {

        var many = aRoundup();
        many.put("openTalkCount", 3);
        var none = aRoundup();
        none.put("openTalkCount", 0);

        var several = renderer.render("organization-roundup", many);
        var closed = renderer.render("organization-roundup", none);

        assertThat(several.html()).contains(">3 talks still wanted</p>")
                .contains(">Open topics waiting for a presenter</p>")
                .containsOnlyOnce("<a href=\"https://axonia.example/communities?org=acme\"").contains(">Pick one</a>")
                .containsOnlyOnce("background-color:#de3b78;color:#ffffff;").doesNotContain("Browse the communities");
        assertThat(several.text())
                .contains("3 talks still wanted. Open topics waiting for a presenter.\n"
                        + "Pick one: https://axonia.example/communities?org=acme")
                .doesNotContain("Browse the communities");
        assertThat(closed.html()).doesNotContain("still wanted").doesNotContain("Pick one");
        assertThat(closed.text()).doesNotContain("still wanted").doesNotContain("Pick one");
    }

    @Test
    void anAskWithoutALinkKeepsItsCountAndDropsItsButton() {

        var facts = aRoundup();
        facts.put("communitiesUrl", "");

        var rendered = renderer.render("organization-roundup", facts);

        assertThat(rendered.html()).contains(">1 talk still wanted</p>").doesNotContain("Pick one");
        assertThat(rendered.text()).contains("1 talk still wanted.").doesNotContain("Pick one");
    }

    /** Copy rules hold for everything the letter itself says: no dashes, no we, no you. */
    @Test
    void theLetterSaysNothingWithADashOrInTheFirstOrSecondPerson() {

        var rendered = renderer.render("organization-roundup", aRoundup());

        for (var copy : List.of(visibleText(rendered.html()), rendered.text(), rendered.subject())) {
            assertThat(copy).doesNotContain("—").doesNotContain("–").doesNotContain(" - ");
            assertThat(copy.toLowerCase(Locale.ROOT)).doesNotContainPattern("\\b(we|us|our|you|your)\\b");
        }
    }

    /**
     * Gmail clips a message past about 102 KB of HTML. The busiest letter the caps allow is every community at its
     * session cap, every poster at its talk cap with a thumbnail, co-presenters, long titles and four kinds kept.
     */
    @Test
    void theBusiestLetterTheCapsAllowStaysUnderGmailsClip() {

        var cards = IntStream.range(0, ROUNDUP_COMMUNITY_CAP).mapToObj(this::aBusiestCard).toList();
        var facts = aRoundup();
        facts.put("communities", cards);
        facts.put("topics",
                IntStream.range(0, ROUNDUP_TOPIC_CAP).mapToObj(
                        index -> Map.<String, Object>of("label", "observability-" + index, "weight", 1 + index % 5))
                        .toList());

        var rendered = renderer.render("organization-roundup", facts);

        assertThat(rendered.html().getBytes(StandardCharsets.UTF_8).length).isLessThan(90 * 1024);
    }

    private Map<String, Object> aBusiestCard(int index) {

        var card = aBackendGuildCard();
        card.put("name", "Platform Engineering Guild " + index);
        card.put("badgeUrl", "https://cdn.axonia.example/communities/" + index + "/badge/7f3a9c1e.png");
        card.put("presenters",
                List.of(aPresenter("Alice Dubois", "AD", "#de3b78"), aPresenter("Bob Martin", "BM", "#6e3ceb"),
                        aPresenter("Carol Ng", "CN", "#1f9d63"), aPresenter("Dana Rue", "DR", "#6e3ceb"),
                        aPresenter("Eve Ola", "EO", "#1f9d63")));
        card.put("morePresenterCount", 9);
        card.put("sessions", IntStream.range(0, ROUNDUP_SESSIONS_PER_COMMUNITY_CAP).mapToObj(session -> {
            var poster = aHexagonsPoster();
            poster.put("title", "S26E0" + session + " · Spring AI from prompt to basket in production");
            poster.put("thumbnailUrl", "https://cdn.axonia.example/communities/" + index + "/roundup/"
                    + "0b5e9c4a-3f1d-4e8a-9b2c-6d7e8f9a0b1c.jpg");
            poster.put("sessionUrl", "https://axonia.example/communities/platform-engineering-guild-" + index
                    + "/sessions/1234" + session + "?org=0b5e9c4a-3f1d-4e8a-9b2c-6d7e8f9a0b1c");
            poster.put("recordingUrl", "https://video.example/watch/0b5e9c4a-3f1d-4e8a-9b2c-6d7e8f9a0b1c");
            poster.put("talks",
                    IntStream.range(0, ROUNDUP_TALK_CAP)
                            .mapToObj(talk -> aTalk("Cloud deploy 3.0 and the database default change, talk " + talk,
                                    aPresenter("Alice Dubois", "AD", "#de3b78"), 1, "cloud-deploy"))
                            .toList());
            poster.put("moreTalkCount", 4);
            poster.put("resourceGroups",
                    List.of(Map.of("label", "Postmortem", "count", 2), Map.of("label", "Repository", "count", 3),
                            Map.of("label", "Article", "count", 1), Map.of("label", "Link", "count", 3)));
            return (Map<String, Object>) poster;
        }).toList());
        return card;
    }

    @Test
    void aRoundupFooterSaysTheReaderIsAMemberOfTheOrganizationItself() {

        var rendered = renderer.render("organization-roundup", aRoundup());

        assertThat(rendered.html()).contains("Sent to members of Acme Corp.");
        assertThat(rendered.text()).contains("Sent to members of Acme Corp.");
    }

    /** The wordmark belongs to the host, not to the sender: the same rule a session message follows. */
    @Test
    void aRoundupLeadsWithTheOrganizationAndLeavesTheWordmarkAtTheFoot() {

        var rendered = renderer.render("organization-roundup", aRoundup());

        var afterHead = rendered.html().substring(rendered.html().indexOf("</head>"));
        assertThat(afterHead.indexOf("2026</h1>")).isPositive().isLessThan(afterHead.indexOf(">axonia<"));
    }

    @Test
    void noFactRendersAsTheWordNull() {

        var rendered = renderer.render("organization-roundup", aRoundup());

        assertThat(rendered.html()).doesNotContain("null");
        assertThat(rendered.text()).doesNotContain("null").doesNotContain("\n\n\n");
    }
}
