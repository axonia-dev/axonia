package dev.axonia.communication.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.thymeleaf.exceptions.TemplateInputException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailRendererTest {

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
    void aRecordingUrlWithAnUnsafeSchemeIsNeverAnHref() {

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(), "recordingUrl", "javascript:alert(1)"));

        assertThat(rendered.html()).doesNotContain("href=\"javascript:alert(1)\"").contains("javascript:alert(1)");
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
    void emptyStringFactsDoNotLeaveDanglingLabels() {

        var facts = new HashMap<String, Object>();
        facts.put("sessionTitle", "Frontend Dojo");
        facts.put("talks", List.of());
        facts.put("seriesTitle", "");
        facts.put("recordingUrl", "");
        facts.put("followUpNotes", "");
        facts.put("nextSession", "");
        facts.put("sessionUrl", "");

        var rendered = renderer.render("session-wrap-up", facts);

        assertThat(rendered.html()).doesNotContain("Recording available").doesNotContain("From the organizer")
                .doesNotContain("Coming up next").doesNotContain("Open the session").doesNotContain("From the session")
                .doesNotContain("margin:0 0 6px 0; font-size:13px; font-weight:600; color:#786a7e;")
                .contains("No talks were recorded for this session.");
        assertThat(rendered.text()).doesNotContain("()").doesNotContain("Recording available")
                .doesNotContain("A note from the organizer").doesNotContain("Coming up next:")
                .doesNotContain("View this session on axonia").doesNotContain("From the session")
                .contains("No talks were recorded for this session.").doesNotContain("<");
    }

    @Test
    void attendeeCountRendersAsAStatStripChip() {

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(), "attendeeCount", 1));

        assertThat(rendered.html()).contains("1 in the room");
        assertThat(rendered.text()).contains("1 in the room");
    }

    @Test
    void zeroAttendeesSuppressesTheLineEntirely() {

        // talks is non-empty so the outer stat-strip guard renders the strip via the talk-count chip: without
        // that, "attendeeCount: 0" alone would never reach the inner attendeeCount guard at all, and this test
        // would prove nothing about it.
        var talk = Map.<String, Object>of("title", "Signals in React", "presenter", "Ada");

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(talk), "attendeeCount", 0));

        assertThat(rendered.html()).doesNotContain("in the room");
        assertThat(rendered.text()).doesNotContain("in the room");
    }

    @Test
    void noRecordingUrlSuppressesTheRecordingChipEvenWhenTalksArePresent() {

        // Same shape of gap as zeroAttendeesSuppressesTheLineEntirely: talks non-empty keeps the outer stat-strip
        // guard true via the talk-count chip, so the absence of a recording chip here is actually exercising the
        // inner recordingUrl guard, not just an already-suppressed strip.
        var talk = Map.<String, Object>of("title", "Signals in React", "presenter", "Ada");

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(talk)));

        assertThat(rendered.html()).doesNotContain("Recording available");
        assertThat(rendered.text()).doesNotContain("Recording available");
    }

    @Test
    void statStripRendersNothingWhenThereIsNothingToShow() {

        var rendered = renderer.render("session-wrap-up", Map.of("sessionTitle", "Frontend Dojo", "talks", List.of()));

        assertThat(rendered.html())
                .doesNotContain("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">");
    }

    @Test
    void statStripOmitsTheTalkChipWhenThereAreNoTalksEvenIfTheStripRenders() {

        // The strip renders on attendeeCount alone here; talks stays empty, so the talk-count chip must not
        // fall back to "0 talks" - that would mean its own inner guard, not the strip's outer one, was deleted.
        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(), "attendeeCount", 5));

        assertThat(rendered.html()).contains("5 in the room").doesNotContain("talk</span>")
                .doesNotContain("talks</span>");
        assertThat(rendered.text()).contains("5 in the room").doesNotContain("0 talks");
    }

    @Test
    void theAvatarRowOnlyRendersWhenPresentersIsResolved() {

        var talk = Map.<String, Object>of("title", "Signals in React", "presenter", "Ada");

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(talk)));

        assertThat(rendered.html()).doesNotContain("<td style=\"font-size:13px; color:#786a7e;\"");
    }

    @Test
    void theNameFallbackParagraphsDoNotRenderAlongsideTheAvatarRow() {

        var talk = Map.<String, Object>of("title", "Signals in React", "presenter", "Ada", "presenters",
                List.of(Map.of("name", "Ada", "initials", "A")));

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(talk)));

        assertThat(rendered.html()).doesNotContain("margin:0 0 8px 0; font-size:13px; font-weight:600; color:#786a7e;");
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
    void aTalkWithNoPresenterReadsAsProseNotDatabaseOutput() {

        var talk = mapWithNullPresenter("Lightning round");

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(talk)));

        assertThat(rendered.html()).contains("We did not catch who presented this one.");
        assertThat(rendered.text()).contains("We did not catch who presented this one.");
    }

    @Test
    void multiplePresentersGetCyclingAvatarColoursIncludingTheSurvivingViolet() {

        var talk = Map.<String, Object>of("title", "Signals in React", "presenter", "Ada, Bob, Carol", "presenters",
                List.of(Map.of("name", "Ada", "initials", "A"), Map.of("name", "Bob", "initials", "B"),
                        Map.of("name", "Carol", "initials", "C")));

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(talk)));

        assertThat(rendered.html()).contains("background-color:#de3b78").contains("background-color:#6e3ceb")
                .contains("background-color:#1f9d63").contains(">A<").contains(">B<").contains(">C<");
    }

    @Test
    void wrapUpSurfacesSubjectsResourcesRecordingAttendanceAndWhatIsNext() {

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks",
                        List.of(Map.of("title", "Signals in React", "presenter", "Ada", "subjects",
                                List.of("React", "State management"), "resources",
                                List.of(Map.of("url", "https://axonia.example/slides/42")))),
                        "recordingUrl", "https://axonia.example/rec/42", "attendeeCount", 12, "nextSession",
                        "Backend Dojo on 15 August", "sessionUrl", "https://axonia.example/s/42"));

        assertThat(rendered.html()).contains(">React<").contains(">State management<")
                .contains("https://axonia.example/slides/42").contains("https://axonia.example/rec/42")
                .contains("12 in the room").contains("Backend Dojo on 15 August");
        assertThat(rendered.text()).contains("React, State management").contains("https://axonia.example/slides/42")
                .contains("https://axonia.example/rec/42").contains("12 in the room")
                .contains("Backend Dojo on 15 August").doesNotContain("<");
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

        assertThat(rendered.html()).doesNotContain("What we agreed to do");
        assertThat(rendered.text()).doesNotContain("What we agreed to do");
    }

    @Test
    void noCheckedItemsMeansNoHeadingForThem() {

        var rendered = renderer.render("session-wrap-up", Map.of("sessionTitle", "Frontend Dojo", "talks", List.of()));

        assertThat(rendered.html()).doesNotContain("What was checked");
        assertThat(rendered.text()).doesNotContain("What was checked");
    }

    @Test
    void aCheckedItemRendersItsAssigneeAndItsOutcomeLabel() {

        var checkedItem = checkedItemFact("Renew the certificate", "Bob", "Carried", "CARRIED");

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(), "checkedItems", List.of(checkedItem)));

        assertThat(rendered.html()).contains("What was checked").contains("Renew the certificate").contains("Bob")
                .contains("Carried");
        assertThat(rendered.text()).contains("What was checked").contains("Renew the certificate - Bob (Carried)");
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

        assertThat(rendered.html()).contains("What we agreed to do").contains("text-decoration:line-through")
                .contains("Write the runbook").contains("Due 8 Aug").contains("Try the staging config")
                .contains("Unassigned");
        assertThat(rendered.text()).contains("What we agreed to do")
                .contains("Write the runbook - Bob, due 8 Aug (done)").contains("Try the staging config - Unassigned")
                .doesNotContain("[x]").doesNotContain("[ ]")
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
        assertThat(rendered.text()).contains("From the session").contains("https://axonia.example/notes");
    }

    @Test
    void aTalkResourceWhoseTitleMatchesTheTalkTitleRendersTheUrlAsTheLabelInsteadOfRepeatingTheTitle() {

        var talk = Map.<String, Object>of("title", "It was a beautiful topic", "presenter", "Ada", "resources",
                List.of(Map.of("title", " It Was A Beautiful Topic ", "url", "https://axonia.example/slides/1"),
                        Map.of("title", "Slide deck", "url", "https://axonia.example/slides/2")));

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(talk)));

        assertThat(rendered.html()).contains(">https://axonia.example/slides/1<")
                .doesNotContain(">It Was A Beautiful Topic<").contains(">Slide deck<");
        assertThat(rendered.text()).contains("https://axonia.example/slides/1")
                .doesNotContain("It Was A Beautiful Topic:").contains("Slide deck: https://axonia.example/slides/2");
    }

    @Test
    void aSessionLevelResourceIsUnaffectedEvenWhenItsTitleMatchesATalkTitle() {

        var talk = Map.<String, Object>of("title", "It was a beautiful topic", "presenter", "Ada");

        var rendered = renderer.render("session-wrap-up",
                Map.of("sessionTitle", "Frontend Dojo", "talks", List.of(talk), "resources",
                        List.of(Map.of("title", "It was a beautiful topic", "url", "https://axonia.example/notes"))));

        assertThat(rendered.html()).contains("From the session").contains(">It was a beautiful topic<");
        assertThat(rendered.text()).contains("From the session")
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
    @CsvSource({"session-upcoming,You are a member of Backend Guild.",
            "session-wrap-up,You are a member of Backend Guild.",
            "session-awaiting-close,You look after sessions in Backend Guild."})
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

    private static Map<String, Object> anOrganization() {

        return Map.of("name", "Acme Corp", "initials", "AC", "tintBackground", "#f6f0ec", "tintForeground", "#211a26");
    }

    private static Map<String, Object> aPresenter(String name, String initials, String tint) {

        return Map.of("name", name, "initials", initials, "tint", tint);
    }

    private static Map<String, Object> aResource(String title, String url) {

        var resource = new HashMap<String, Object>();
        resource.put("title", title);
        resource.put("url", url);
        return resource;
    }

    private static Map<String, Object> aSessionRow(String dateLabel, String title, List<String> talkTitles,
            List<Map<String, Object>> resources) {

        var row = new HashMap<String, Object>();
        row.put("dateLabel", dateLabel);
        row.put("title", title);
        row.put("talkTitles", talkTitles);
        row.put("resources", resources);
        return row;
    }

    /**
     * The busy card: three presenters, two sessions - one of them a series occurrence nobody named and nobody put an
     * agenda on - and a future of its own. Mutable, so a test can cap it or empty it without rebuilding the letter.
     */
    private static Map<String, Object> aBackendGuildCard() {

        var card = new HashMap<String, Object>();
        card.put("name", "Backend Guild");
        card.put("initials", "BG");
        card.put("badgeUrl", null);
        card.put("accentColor", "#1f9d63");
        card.put("summaryParts", List.of("7 sessions", "20 talks", "30 resources"));
        card.put("presenters", List.of(aPresenter("Alice Dubois", "AD", "#de3b78"),
                aPresenter("Bob Martin", "BM", "#6e3ceb"), aPresenter("Carol Ng", "CN", "#1f9d63")));
        card.put("morePresenterCount", 0);
        card.put("sessions",
                List.of(aSessionRow("13 May", "Hexagons in anger", List.of("Ports and adapters", "Testing the seam"),
                        List.of(aResource("Recording", "https://axonia.example/a/1"))),
                        aSessionRow("2 June", null, List.of(), List.of())));
        card.put("moreSessionCount", 0);
        card.put("nextTitle", "Signals in React");
        card.put("nextLabel", "Tuesday 14 July");
        card.put("moreUpcomingCount", 0);
        return card;
    }

    /** The quiet card, and the one with NOTHING coming - so "Next up" must appear exactly once across the two. */
    private static Map<String, Object> aFrontendDojoCard() {

        var card = new HashMap<String, Object>();
        card.put("name", "Frontend Dojo");
        card.put("initials", "FD");
        card.put("badgeUrl", null);
        card.put("accentColor", null);
        card.put("summaryParts", List.of("1 session", "1 talk"));
        card.put("presenters", List.of(aPresenter("Dana Rue", "DR", "#6e3ceb")));
        card.put("morePresenterCount", 0);
        card.put("sessions", List.of(aSessionRow("9 April", "Design tokens", List.of("Tokens in anger"), List.of())));
        card.put("moreSessionCount", 0);
        card.put("nextTitle", null);
        card.put("nextLabel", null);
        card.put("moreUpcomingCount", 0);
        return card;
    }

    /**
     * A realistic quarter, mirroring the shape {@code roundup.internal.RoundupFacts} produces. Hand-written rather than
     * built by calling that class: it is package-private to another module, and this test class is deliberately the one
     * place that knows only about templates and fact keys.
     */
    private static Map<String, Object> aRoundup() {

        var facts = new HashMap<String, Object>();
        facts.put("sender", anOrganization());
        facts.put("sessionTitle", "Q2 2026");
        facts.put("periodLabel", "April to June 2026");
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
        facts.put("communitiesUrl", "https://axonia.example/communities");
        return facts;
    }

    private static Map<String, Object> aRoundupOf(Map<String, Object> card) {

        var facts = aRoundup();
        facts.put("communities", List.of(card));
        return facts;
    }

    @Test
    void aRoundupComesFromTheOrganizationAndSaysWhichQuarterItCovers() {

        var rendered = renderer.render("organization-roundup", aRoundup());

        assertThat(rendered.subject()).isEqualTo("April to June 2026 across your communities");
        assertThat(rendered.html()).contains("Acme Corp").contains(">AC<").contains("#f6f0ec").contains("#211a26")
                .contains(">Q2 2026</h1>").contains("Covering April to June 2026");
        // The headline echoed in the .txt half too, the shape all three session-*.txt files already follow.
        assertThat(rendered.text()).contains("Acme Corp").contains("Q2 2026").contains("Covering April to June 2026.")
                .doesNotContain("<");
    }

    /**
     * The quarter's scale as a band of four figures rather than as the run-on sentence it replaces. The HTML asserts
     * the figures and their labels separately, because the band is the one place a figure and its noun are not in the
     * same string; the .txt has no columns to line up and states the same four as one sentence.
     */
    @Test
    void theScaleOfTheQuarterIsABandOfSessionsTalksPresentersAndResources() {

        var rendered = renderer.render("organization-roundup", aRoundup());

        assertThat(rendered.html()).contains(">12</p>").contains(">Sessions</p>").contains(">34</p>")
                .contains(">Talks</p>").contains(">3</p>").contains(">Presenters</p>").contains(">47</p>")
                .contains(">Resources</p>").doesNotContain("No sessions were held over the quarter.");
        assertThat(rendered.text()).contains("12 sessions, 34 talks, 3 presenters, 47 resources.")
                .doesNotContain("No sessions were held over the quarter.");
    }

    /**
     * A figure inside the band prints its zero, unlike a card's own summary line, which drops a part that is zero. The
     * two are not inconsistent: the band's four columns are the letter's spine and a gap in it reads as a rendering
     * fault, while a summary line is a sentence and "0 resources" is the most discouraging way available of saying a
     * community captured nothing.
     */
    @Test
    void aFigureOfZeroInsideTheBandIsPrintedRatherThanDroppedTheWayASummaryLineDropsIt() {

        var facts = aRoundup();
        facts.put("talkCount", 0);

        var rendered = renderer.render("organization-roundup", facts);

        assertThat(rendered.html()).contains(">0</p>").contains(">Talks</p>");
        assertThat(rendered.text()).contains("12 sessions, 0 talks, 3 presenters, 47 resources.");
    }

    /**
     * A quarter the letter can name no session in prints a sentence instead of a band of zeroes. The band must be
     * asserted ABSENT rather than only the sentence present: a {@code contains} assertion cannot catch an over-render,
     * and this codebase has already shipped a template that rendered both arms of a condition past nine reviews.
     */
    @Test
    void aQuarterThatHeldNothingSaysSoInWordsRatherThanAsABandOfZeroes() {

        var facts = aRoundup();
        facts.put("sessionCount", 0);
        facts.put("talkCount", 0);
        facts.put("presenterCount", 0);
        facts.put("resourceCount", 0);
        facts.put("topics", List.of());
        facts.put("communities", List.of());
        facts.put("moreCommunitiesCount", 0);

        var rendered = renderer.render("organization-roundup", facts);

        assertThat(rendered.html()).contains("No sessions were held over the quarter.").doesNotContain(">Sessions</p>")
                .doesNotContain(">Talks</p>").doesNotContain(">Presenters</p>").doesNotContain(">Resources</p>")
                .doesNotContain("null");
        assertThat(rendered.text()).contains("No sessions were held over the quarter.").doesNotContain("0 sessions")
                .doesNotContain("0 talks").doesNotContain("0 presenters").doesNotContain("0 resources")
                .doesNotContain("null");
    }

    /**
     * The line with the strictest rule, and the one place the two renderings deliberately differ. The HTML shows
     * involvement as initialled badges and names NOBODY - per-talk presenter lines were tried and rejected, since one
     * member presenting five of seven talks made the letter read as a wall of the same name. The .txt has no avatars,
     * so it names everybody in full, which is where the earlier "presenters are named" decision survives intact.
     *
     * <p>
     * Asserted in both directions in both halves. Nothing in the gate compares the two files, so an assertion on
     * {@code rendered.text()} is the only thing standing between these two renderings and silent drift.
     */
    @Test
    void involvementIsBadgesInTheHtmlAndNamesInFullInThePlainText() {

        var rendered = renderer.render("organization-roundup", aRoundup());

        assertThat(rendered.html()).contains(">AD</div>").contains(">BM</div>").contains(">CN</div>")
                .contains(">DR</div>").doesNotContain("Alice Dubois").doesNotContain("Bob Martin")
                .doesNotContain("Carol Ng").doesNotContain("Dana Rue");
        assertThat(rendered.text()).contains("Presented by Alice Dubois, Bob Martin, Carol Ng.")
                .contains("Presented by Dana Rue.").doesNotContain("<");
    }

    /**
     * A community whose sessions had nobody on any slot - a quarter of discussion-format meetings, which is ordinary
     * rather than defensive - hands this card an EMPTY presenter list. The avatar cell is omitted and the rest of the
     * card is unchanged; nothing may render as the literal word "null", in either half.
     */
    @Test
    void aCommunityThatHeldOnlyDiscussionsRendersACardWithNoAvatarRowAtAll() {

        var card = aBackendGuildCard();
        card.put("presenters", List.of());
        card.put("morePresenterCount", 0);

        var rendered = renderer.render("organization-roundup", aRoundupOf(card));

        assertThat(rendered.html()).contains("Backend Guild").contains("Hexagons in anger").doesNotContain("null")
                .doesNotContain(">AD</div>");
        assertThat(rendered.text()).contains("Backend Guild").contains("Hexagons in anger").doesNotContain("null")
                .doesNotContain("Presented by").doesNotContain("<");
    }

    /** A community with more presenters than the avatar row holds states the remainder rather than swallowing it. */
    @Test
    void aCommunityWithMorePresentersThanFitStatesTheRemainderAndSaysNothingWhenEverybodyFit() {

        var card = aBackendGuildCard();
        card.put("morePresenterCount", 2);

        var capped = renderer.render("organization-roundup", aRoundupOf(card));
        var whole = renderer.render("organization-roundup", aRoundup());

        assertThat(capped.html()).contains(">+2</div");
        assertThat(capped.text()).contains("Presented by Alice Dubois, Bob Martin, Carol Ng, and 2 more.");
        assertThat(whole.html()).doesNotContain(">+0</div").doesNotContain("+2");
        assertThat(whole.text()).doesNotContain(" more.").contains("Presented by Alice Dubois, Bob Martin, Carol Ng.");
    }

    /**
     * The whole point of the redesign: the past gets NOUNS. The letter used to print six figures about what happened
     * and give a title only to what had not happened yet, and every one of the strings below is something it could not
     * previously say at all.
     */
    @Test
    void aCommunityCardCarriesItsNameItsSummaryLineAndTheTitlesOfWhatItHeld() {

        var rendered = renderer.render("organization-roundup", aRoundup());

        assertThat(rendered.html()).contains("Backend Guild").contains(">BG</div>")
                .contains("7 sessions \u00b7 20 talks \u00b7 30 resources").contains("13 May")
                .contains("Hexagons in anger").contains("Ports and adapters \u00b7 Testing the seam")
                .contains("https://axonia.example/a/1").contains(">Recording</a>").doesNotContain("null");
        assertThat(rendered.text()).contains("Backend Guild").contains("7 sessions, 20 talks, 30 resources")
                .contains("13 May: Hexagons in anger").contains("Talks: Ports and adapters, Testing the seam")
                .contains("Recording: https://axonia.example/a/1").doesNotContain("\u00b7").doesNotContain("null")
                .doesNotContain("<");
    }

    /**
     * A community's own accent tints its monogram, and a community that never chose one falls back to body ink rather
     * than to a hex the facts had to invent. Frontend Dojo carries a null accent for exactly this.
     */
    @Test
    void aCommunityWithoutAnAccentFallsBackToBodyInkRatherThanToAnInventedHex() {

        var rendered = renderer.render("organization-roundup", aRoundup());

        assertThat(rendered.html()).contains("text-align:center;color:#1f9d63;\">BG</div>")
                .contains("text-align:center;color:#211a26;\">FD</div>").doesNotContain("color:null");
    }

    /**
     * A badge is only ever an image when the fact carries a fetchable http(s) URL, and a monogram otherwise. The
     * resolution itself happens in {@code RoundupFactsAssembler}, which turns the community's storage key into a public
     * URL; this is defence in depth against anything unresolved reaching the template, since an {@code <img
     * src>} built from a bare object key is a broken image in every inbox.
     */
    @Test
    void aBadgeIsAnImageOnlyWhenTheFactCarriesAFetchableUrlAndAMonogramOtherwise() {

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

    /** Two communities are two cards, and the remainder line states what the cap left out, or says nothing. */
    @Test
    void aQuarterOfTwoCommunitiesIsTwoCardsAndSaysHowManyMoreWereActive() {

        var capped = renderer.render("organization-roundup", aRoundup());
        var whole = aRoundup();
        whole.put("moreCommunitiesCount", 0);
        var complete = renderer.render("organization-roundup", whole);

        assertThat(capped.html()).containsOnlyOnce("Backend Guild").containsOnlyOnce("Frontend Dojo")
                .contains("and 3 more communities were active.");
        assertThat(capped.text()).containsOnlyOnce("Backend Guild").containsOnlyOnce("Frontend Dojo")
                .contains("and 3 more communities were active.");
        assertThat(complete.html()).doesNotContain("more communities were active")
                .doesNotContain("more community was active");
        assertThat(complete.text()).doesNotContain("more communities were active")
                .doesNotContain("more community was active");
    }

    /**
     * A card capped by {@code sessionsPerCommunityCap} states its own remainder, in all three arms. The plural arm is
     * asserted absent from the singular rendering, because both arms of a singular/plural branch rendering at once is
     * precisely the failure this codebase has already shipped once.
     */
    @Test
    void aCardCappedByItsSessionCapSaysHowManyItLeftOutAndSaysNothingWhenItLeftOutNone() {

        var many = aBackendGuildCard();
        many.put("moreSessionCount", 2);
        var one = aBackendGuildCard();
        one.put("moreSessionCount", 1);

        var capped = renderer.render("organization-roundup", aRoundupOf(many));
        var singular = renderer.render("organization-roundup", aRoundupOf(one));
        var whole = renderer.render("organization-roundup", aRoundup());

        assertThat(capped.html()).contains("and 2 more sessions were held.");
        assertThat(capped.text()).contains("and 2 more sessions were held.");
        assertThat(singular.html()).contains("and 1 more session was held.").doesNotContain("more sessions were held");
        assertThat(singular.text()).contains("and 1 more session was held.").doesNotContain("more sessions were held");
        assertThat(whole.html()).doesNotContain("more sessions were held").doesNotContain("more session was held");
        assertThat(whole.text()).doesNotContain("more sessions were held").doesNotContain("more session was held");
    }

    /**
     * "What is coming" is no longer a section: each card carries its own future, so a reader meets a community's name
     * once instead of twice three hundred pixels apart. The heading's absence is asserted as firmly as the line's
     * presence, and "Next up" is asserted to appear ONCE across two cards - Frontend Dojo has nothing coming, and a
     * bare {@code contains} would be satisfied by a trailing "Next up null." on its card.
     */
    @Test
    void aCommunitysFutureSitsOnItsOwnCardRatherThanInASectionOfItsOwn() {

        var soon = aBackendGuildCard();
        soon.put("moreUpcomingCount", 2);

        var rendered = renderer.render("organization-roundup", aRoundup());
        var more = renderer.render("organization-roundup", aRoundupOf(soon));

        assertThat(rendered.html()).containsOnlyOnce("Next up ").contains(">Signals in React</span")
                .contains(", Tuesday 14 July.").doesNotContain("What is coming").doesNotContain("more to come")
                .doesNotContain("null");
        assertThat(rendered.text()).containsOnlyOnce("Next up ").contains("Next up Signals in React, Tuesday 14 July.")
                .doesNotContain("What is coming").doesNotContain("more to come").doesNotContain("null");
        assertThat(more.html()).contains(", Tuesday 14 July. 2 more to come.");
        assertThat(more.text()).contains("Next up Signals in React, Tuesday 14 July. 2 more to come.");
    }

    /**
     * Decision handed down from the activity ports: a series occurrence with no titled slot is nameless, and neither
     * fallback the app uses is reachable from the roundup. Both the session row and the "next up" line render it by its
     * date alone, so "Untitled" must be absent as firmly as the date is present.
     */
    @Test
    void aNamelessSessionIsRenderedByItsDateRatherThanByAPlaceholderWord() {

        var nameless = aBackendGuildCard();
        nameless.put("nextTitle", null);

        var rendered = renderer.render("organization-roundup", aRoundupOf(nameless));

        assertThat(rendered.html()).contains("2 June").contains(">Next up Tuesday 14 July.</p>")
                .doesNotContain("Untitled").doesNotContain("null");
        assertThat(rendered.text()).contains("Next up Tuesday 14 July.").doesNotContain("Untitled")
                .doesNotContain("null").doesNotContain("<");
    }

    /**
     * The topic cloud is the "what was this quarter about" glance, sized and coloured by the same three bands the
     * Expertise page's constellation uses. The heaviest tag is asserted in brand pink at 26px and the lightest muted at
     * 13px, so a cloud that lost its scale and rendered flat fails here rather than reading as a design choice.
     */
    @Test
    void theTopicCloudSizesAndColoursEachWordByItsWeightAndDisappearsWithTheTags() {

        var untagged = aRoundup();
        untagged.put("topics", List.of());

        var rendered = renderer.render("organization-roundup", aRoundup());
        var flat = renderer.render("organization-roundup", untagged);

        assertThat(rendered.html()).contains("What we talked about")
                .contains("font-size:26px; color:#de3b78;\">hexagonal</span>")
                .contains("font-size:18px; color:#211a26;\">testing</span>")
                .contains("font-size:13px; color:#786a7e;\">kafka</span>");
        assertThat(rendered.text()).contains("What we talked about: hexagonal, testing, kafka");
        assertThat(flat.html()).doesNotContain("What we talked about").doesNotContain("hexagonal");
        assertThat(flat.text()).doesNotContain("What we talked about").doesNotContain("hexagonal");
    }

    /**
     * The one figure in the letter that asks the reader for something, and the only one that is absent at zero rather
     * than stated as a zero: an open-slot count of none has nothing to ask for. All three arms, in both halves.
     */
    @Test
    void theAskIsPresentOnlyWhereASlotIsStillOpenAndCarriesBothOfItsArms() {

        var many = aRoundup();
        many.put("openTalkCount", 3);
        var none = aRoundup();
        none.put("openTalkCount", 0);

        var one = renderer.render("organization-roundup", aRoundup());
        var several = renderer.render("organization-roundup", many);
        var closed = renderer.render("organization-roundup", none);

        assertThat(one.html()).contains("1 talk still needs a presenter across the coming quarter. It could be yours.")
                .doesNotContain("still need a presenter");
        assertThat(one.text()).contains("1 talk still needs a presenter across the coming quarter. It could be yours.")
                .doesNotContain("still need a presenter");
        assertThat(several.html())
                .contains("3 talks still need a presenter across the coming quarter. One could be yours.")
                .doesNotContain("still needs a presenter");
        assertThat(several.text())
                .contains("3 talks still need a presenter across the coming quarter. One could be yours.")
                .doesNotContain("still needs a presenter");
        assertThat(closed.html()).doesNotContain("needs a presenter").doesNotContain("need a presenter");
        assertThat(closed.text()).doesNotContain("needs a presenter").doesNotContain("need a presenter");
    }

    /**
     * The one quarter where the first-timer figure and the scale line cannot both be stated. It comes from an
     * organization-wide port with no community predicate, so it survives a filter that dropped every session the letter
     * could name. The producer's floor is now a single clause and refuses every such SENT letter, but
     * {@code RoundupPreviewer} deliberately applies no floor, because an administrator deciding whether to switch the
     * roundup on is entitled to see the empty letter it would have produced.
     *
     * <p>
     * "3 people presented for the first time." under "No sessions were held over the quarter." is not a pair a reader
     * can reconcile in a preview either. Asserted in both directions and in both halves: a {@code contains} assertion
     * cannot catch a sentence that renders when it should not.
     */
    @Test
    void theFirstTimerSentenceIsAbsentFromAQuarterTheLetterCanReportNoSessionsFor() {

        var archived = aRoundup();
        archived.put("sessionCount", 0);
        archived.put("talkCount", 0);
        archived.put("presenterCount", 0);
        archived.put("resourceCount", 0);
        archived.put("topics", List.of());
        archived.put("communities", List.of());
        archived.put("moreCommunitiesCount", 0);
        archived.put("firstTimeContributorCount", 3);

        var quiet = renderer.render("organization-roundup", archived);
        var busy = renderer.render("organization-roundup", aRoundup());

        assertThat(quiet.html()).contains("No sessions were held over the quarter.")
                .doesNotContain("presented for the first time");
        assertThat(quiet.text()).contains("No sessions were held over the quarter.")
                .doesNotContain("presented for the first time");
        assertThat(busy.html()).contains(">4 </span><span>people presented for the first time.</span>");
        assertThat(busy.text()).contains("4 people presented for the first time.");
    }

    /**
     * A tenant's first roundup is routinely one session, in one community, by one person. Every plural arm is asserted
     * absent as well, because both arms of a singular/plural branch rendering at once is precisely the failure this
     * codebase has already shipped once. The band's labels inflect too: "1 Talks" under a figure of 1 is the sentence
     * that tells that tenant nobody read this before sending it.
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

        var rendered = renderer.render("organization-roundup", facts);

        assertThat(rendered.html()).contains(">Session</p>").contains(">Talk</p>").contains(">Presenter</p>")
                .contains(">Resource</p>").contains(">1 </span><span>person presented for the first time.</span>")
                .contains("and 1 more community was active.").doesNotContain(">Sessions</p>")
                .doesNotContain(">Talks</p>").doesNotContain(">Presenters</p>").doesNotContain(">Resources</p>")
                .doesNotContain("people presented").doesNotContain("more communities were active");
        assertThat(rendered.text()).contains("1 session, 1 talk, 1 presenter, 1 resource.")
                .contains("1 person presented for the first time.").contains("and 1 more community was active.")
                .doesNotContain("1 sessions").doesNotContain("1 talks").doesNotContain("1 presenters")
                .doesNotContain("1 resources").doesNotContain("people presented")
                .doesNotContain("more communities were active");
    }

    /**
     * A resource is only ever an {@code <a>} for an http(s) url. The roundup styles its own resource line rather than
     * reusing {@code resourceLink}, since a session row is a line in a list at a smaller, bolder scale than a wrap-up's
     * own block - so the safe-scheme rule has to be asserted here too rather than inherited.
     */
    @Test
    void aRoundupResourceWithAnUnsafeSchemeIsNeverAnHref() {

        var card = aBackendGuildCard();
        card.put("sessions", List.of(aSessionRow("13 May", "Hexagons in anger", List.of(),
                List.of(aResource(null, "javascript:alert(1)")))));

        var rendered = renderer.render("organization-roundup", aRoundupOf(card));

        assertThat(rendered.html()).doesNotContain("href=\"javascript:alert(1)\"").contains("javascript:alert(1)")
                .doesNotContain("null");
        // The .txt half applies no scheme filter and needs none: plain text has no href to hijack, so the url is
        // printed verbatim. Asserted rather than left unexamined, since this is the one roundup case whose two halves
        // genuinely differ in behaviour rather than only in wording.
        assertThat(rendered.text()).contains("javascript:alert(1)").doesNotContain("<").doesNotContain("null");
    }

    /**
     * Every section a fact map can legitimately arrive without, asserted absent by its heading rather than by its rows:
     * a heading rendered above nothing is the shape a reader reads as a broken mail, and it is exactly what an
     * inclusion that outran its own condition produces.
     */
    @Test
    void everyOptionalSectionDisappearsWithItsOwnFactRatherThanLeavingAHeadingBehind() {

        var facts = aRoundup();
        facts.put("topics", List.of());
        facts.put("communities", List.of());
        facts.put("moreCommunitiesCount", 0);
        facts.put("openTalkCount", 0);
        facts.put("communitiesUrl", "");

        var rendered = renderer.render("organization-roundup", facts);

        assertThat(rendered.html()).doesNotContain("What we talked about").doesNotContain("What happened")
                .doesNotContain("Browse the communities")
                .doesNotContain("display:inline-block; background-color:#de3b78; color:#ffffff; text-decoration:none;")
                .doesNotContain("null");
        assertThat(rendered.text()).doesNotContain("What we talked about").doesNotContain("What happened")
                .doesNotContain("Browse the communities").doesNotContain("\n\n\n").doesNotContain("null");
    }

    @Test
    void aRoundupCarriesExactlyOneCallToActionAndItIsBrowsingTheCommunities() {

        var rendered = renderer.render("organization-roundup", aRoundup());

        assertThat(rendered.html()).contains("Browse the communities").contains("https://axonia.example/communities")
                .containsOnlyOnce(
                        "display:inline-block; background-color:#de3b78; color:#ffffff; text-decoration:none;");
        assertThat(rendered.text()).contains("Browse the communities: https://axonia.example/communities");
    }

    @Test
    void aRoundupFooterSaysTheReaderIsAMemberOfTheOrganizationItself() {

        var rendered = renderer.render("organization-roundup", aRoundup());

        assertThat(rendered.html()).contains("You are a member of Acme Corp.");
        assertThat(rendered.text()).contains("You are a member of Acme Corp.");
    }

    /** The wordmark belongs to the host, not to the sender - the same rule a session message follows. */
    @Test
    void aRoundupLeadsWithTheOrganizationAndLeavesTheWordmarkAtTheFoot() {

        var rendered = renderer.render("organization-roundup", aRoundup());

        var afterHead = rendered.html().substring(rendered.html().indexOf("</head>"));
        assertThat(afterHead.indexOf(">Q2 2026</h1>")).isLessThan(afterHead.indexOf(">axonia<"));
    }
}
