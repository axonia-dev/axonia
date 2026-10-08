package dev.axonia.events.internal;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import dev.axonia.RepositoryRoot;
import dev.axonia.community.CommunityIdentity;
import dev.axonia.events.ActionItemOutcome;
import dev.axonia.events.CheckpointOutcome;
import dev.axonia.events.MeetingPlace;
import dev.axonia.events.ResourceLink;
import dev.axonia.events.Section;
import dev.axonia.events.Slot;
import dev.axonia.events.mother.SessionMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionFactsTest {

    private static final String SESSION_URL = "https://axonia.example/communities/frontend-guild/sessions/7";
    private static final Set<String> FACT_KEYS = Set.of("sessionTitle", "seriesTitle", "scheduledAt", "hasTime",
            "timeZone", "sessionUrl", "talks", "resources", "actionPoints", "checkedItems", "followUpNotes",
            "recordingUrl", "attendeeCount", "nextSession", "sender", "nudgeArm", "openSlotCount", "backlogUrl");
    private static final Set<String> TALK_KEYS = Set.of("title", "presenter", "presenters", "subjects",
            "durationMinutes", "resources");

    /**
     * Read from the repository rather than the classpath, the same way {@code KindHueTest} reads {@code kind-hue.ts}: a
     * frontend source file is not a backend test resource, so the path is resolved from the repository root. Note the
     * real path is {@code features/events/domain/}, not a {@code lib/} path.
     */
    private static final Path SESSION_TS = RepositoryRoot.file("frontend/src/features/events/domain/session.ts");

    private TemplateEngine mailTemplateEngine;

    @BeforeEach
    void setUpTemplateEngine() {

        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/mail/");
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mailTemplateEngine = new TemplateEngine();
        mailTemplateEngine.setTemplateResolver(resolver);
    }

    private static Section.SectionBuilder aSection() {

        return Section.builder().id(21L).sessionId(7L).position(0).sectionTypeId(1L).sectionType("Round table");
    }

    private static Slot.SlotBuilder aSlot() {

        return Slot.builder().id(11L).sectionId(21L).position(0);
    }

    private static SessionFactsContext.SessionFactsContextBuilder aContext() {

        return SessionFactsContext.builder().sessionUrl(SESSION_URL);
    }

    private String render(String template, Map<String, Object> facts) {

        var context = new Context();
        context.setVariables(facts);
        return mailTemplateEngine.process(template, context);
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> onlyTalk(Map<String, Object> facts) {

        return (Map<String, ?>) ((List<?>) facts.get("talks")).get(0);
    }

    @Test
    void aWrapUpCarriesTheTalksTheirPresentersAndTheirResources() {

        var facts = SessionFacts.of(
                SessionMother.aHeldSession()
                        .sections(List.of(aSection().slots(List.of(aSlot().title("Signals in React")
                                .presenterUserIds(List.of("user-ada")).tags(List.of("react", "signals")).build()))
                                .build()))
                        .notes("Recording went up late.").recordingUrl("https://example.com/rec").build(),
                aContext().displayNamesByUserId(Map.of("user-ada", "Ada"))
                        .resourcesBySlotId(
                                Map.of(11L, List.of(new ResourceLink("Slides", "https://example.com/deck.pdf"))))
                        .build());

        assertThat(facts).containsEntry("followUpNotes", "Recording went up late.").containsEntry("recordingUrl",
                "https://example.com/rec");
        assertThat((List<?>) facts.get("talks")).singleElement()
                .satisfies(t -> assertThat(t).hasFieldOrPropertyWithValue("title", "Signals in React")
                        .hasFieldOrPropertyWithValue("presenter", "Ada")
                        .hasFieldOrPropertyWithValue("subjects", List.of("react", "signals"))
                        .hasFieldOrPropertyWithValue("resources",
                                List.of(Map.of("title", "Slides", "url", "https://example.com/deck.pdf"))));
    }

    @Test
    void aSessionWithNoFollowUpStillProducesFacts() {

        var facts = SessionFacts.of(SessionMother.aHeldSession().notes(null).recordingUrl(null).build(),
                aContext().build());

        assertThat(facts).containsEntry("followUpNotes", null).containsEntry("recordingUrl", null);
        assertThat((List<?>) facts.get("talks")).isNotNull();
    }

    @Test
    void anEmptyStringNotesAndRecordingUrlAreAbsentNotBlank() {

        // Production shape, not a hypothetical: SessionService writes .notes("") and .recordingUrl(""), never
        // null, so a facts map that only handled null would still leak a blank paragraph into the mail.
        var facts = SessionFacts.of(SessionMother.aHeldSession().notes("").recordingUrl("").build(),
                aContext().build());

        assertThat(facts).containsEntry("followUpNotes", null).containsEntry("recordingUrl", null);
    }

    @Test
    void aWhitespaceOnlyNoteIsAbsentNotBlank() {

        // SessionService never trims (SessionService.java:206 stores the note untrimmed): " " is not a value
        // either, or the organizer's note card renders with nothing in it.
        var facts = SessionFacts.of(SessionMother.aHeldSession().notes("   ").recordingUrl("   ").build(),
                aContext().build());

        assertThat(facts).containsEntry("followUpNotes", null).containsEntry("recordingUrl", null);
    }

    @Test
    void aRoundTableSectionWithNoSlotsIsNotAnError() {

        var facts = SessionFacts.of(
                SessionMother.aHeldSession().sections(List.of(aSection().slots(List.of()).build())).build(),
                aContext().build());

        assertThat((List<?>) facts.get("talks")).isEmpty();
    }

    @Test
    void aPresenterIdWithNoResolvedNameIsAbsentNotTheRawId() {

        // The embarrassing failure mode this guards against: a member reading "With user-ada" in their inbox
        // because nobody resolved the id to a name.
        var facts = SessionFacts.of(SessionMother.aHeldSession().sections(List.of(aSection()
                .slots(List.of(aSlot().title("Unresolved talk").presenterUserIds(List.of("user-ghost")).build()))
                .build())).build(), aContext().build());

        @SuppressWarnings("unchecked")
        var talk = (Map<String, Object>) ((List<?>) facts.get("talks")).get(0);
        assertThat(talk).containsEntry("presenter", null);
    }

    @Test
    void aPresenterWithAnUnresolvedIdIsDroppedFromPresentersToo() {

        var facts = SessionFacts.of(SessionMother.aHeldSession().sections(List.of(aSection()
                .slots(List.of(aSlot().title("Unresolved talk").presenterUserIds(List.of("user-ghost")).build()))
                .build())).build(), aContext().build());

        assertThat((List<?>) onlyTalk(facts).get("presenters")).isEmpty();
    }

    @Test
    void multiplePresentersOnOneSlotJoinIntoOneResolvedString() {

        var facts = SessionFacts.of(
                SessionMother.aHeldSession()
                        .sections(List.of(aSection().slots(List.of(
                                aSlot().title("Pair talk").presenterUserIds(List.of("user-ada", "user-bob")).build()))
                                .build()))
                        .build(),
                aContext().displayNamesByUserId(Map.of("user-ada", "Ada", "user-bob", "Bob")).build());

        assertThat((List<?>) facts.get("talks")).singleElement()
                .satisfies(t -> assertThat(t).hasFieldOrPropertyWithValue("presenter", "Ada, Bob"));
    }

    @Test
    void presentersCarryNameAndInitialsBesidePresenter() {

        var facts = SessionFacts.of(
                SessionMother.aHeldSession()
                        .sections(List.of(aSection().slots(List.of(
                                aSlot().title("Pair talk").presenterUserIds(List.of("user-ada", "user-bob")).build()))
                                .build()))
                        .build(),
                aContext().displayNamesByUserId(Map.of("user-ada", "Ada Lovelace", "user-bob", "Bob")).build());

        @SuppressWarnings("unchecked")
        var presenters = (List<Map<String, String>>) onlyTalk(facts).get("presenters");

        assertThat(presenters).containsExactly(Map.of("name", "Ada Lovelace", "initials", "AL"),
                Map.of("name", "Bob", "initials", "B"));
    }

    @Test
    void aBlankDisplayNameIsDroppedFromBothPresenterFactsTheSameAsAnUnresolvedId() {

        // identity_user.display_name is NOT NULL with no check against empty (V1__baseline.sql:433), so a blank
        // name is a real row shape, not a hypothetical. presenter and presenters must agree it is absent, or a
        // member sees "We did not catch who presented this one." next to one empty coloured circle.
        var facts = SessionFacts.of(SessionMother.aHeldSession().sections(List.of(aSection()
                .slots(List.of(aSlot().title("Blank name talk").presenterUserIds(List.of("user-blank")).build()))
                .build())).build(), aContext().displayNamesByUserId(Map.of("user-blank", "  ")).build());

        assertThat((List<?>) onlyTalk(facts).get("presenters")).isEmpty();
        assertThat(onlyTalk(facts)).containsEntry("presenter", null);
    }

    @Test
    void initialsNeverSplitASurrogatePair() {

        var supplementaryLetter = "𝕏"; // U+1D54F MATHEMATICAL DOUBLE-STRUCK CAPITAL X

        var facts = SessionFacts.of(
                SessionMother.aHeldSession()
                        .sections(List.of(aSection()
                                .slots(List.of(aSlot().title("Solo talk").presenterUserIds(List.of("user-x")).build()))
                                .build()))
                        .build(),
                aContext().displayNamesByUserId(Map.of("user-x", supplementaryLetter)).build());

        @SuppressWarnings("unchecked")
        var presenters = (List<Map<String, String>>) onlyTalk(facts).get("presenters");

        assertThat(presenters).singleElement()
                .satisfies(p -> assertThat(p.get("initials")).isEqualTo(supplementaryLetter));
    }

    @Test
    void initialsCapAtTwoCodePointsNotTwoChars() {

        // "𝕏" (U+1D54F) is a single letter that is two UTF-16 chars wide. A char-length cap would stop
        // after this one supplementary-plane letter; the cap must count code points, so a second word still
        // contributes its own initial.
        var supplementaryLetter = "𝕏";

        var facts = SessionFacts.of(
                SessionMother.aHeldSession()
                        .sections(List.of(aSection()
                                .slots(List.of(aSlot().title("Solo talk").presenterUserIds(List.of("user-x")).build()))
                                .build()))
                        .build(),
                aContext().displayNamesByUserId(Map.of("user-x", supplementaryLetter + " Smith")).build());

        @SuppressWarnings("unchecked")
        var presenters = (List<Map<String, String>>) onlyTalk(facts).get("presenters");

        assertThat(presenters).singleElement()
                .satisfies(p -> assertThat(p.get("initials")).isEqualTo(supplementaryLetter + "S"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aTalkCarriesItsDurationAndOmitsItWhenTheSlotHasNone() {

        var withDuration = SessionFacts.of(SessionMother.aHeldSession()
                .sections(List
                        .of(aSection().slots(List.of(aSlot().title("Colamaiar").durationMinutes(20).build())).build()))
                .build(), aContext().build());
        var withNone = SessionFacts.of(SessionMother.aHeldSession()
                .sections(List.of(
                        aSection().slots(List.of(aSlot().title("Colamaiar").durationMinutes(null).build())).build()))
                .build(), aContext().build());

        assertThat((Map<String, Object>) onlyTalk(withDuration)).containsEntry("durationMinutes", 20);
        assertThat((Map<String, Object>) onlyTalk(withNone)).containsEntry("durationMinutes", null);
    }

    @Test
    void talkResourcesDefaultToAnEmptyListWhenNoneAreAttached() {

        // A slot the context's resource map has no entry for genuinely has nothing attached - a real state, not
        // an error, and never null (the template iterates it unconditionally once talk.resources is non-empty).
        var facts = SessionFacts.of(
                SessionMother.aHeldSession()
                        .sections(List.of(aSection().slots(List.of(aSlot().title("Talk").build())).build())).build(),
                aContext().build());

        assertThat((List<?>) facts.get("talks")).singleElement()
                .satisfies(t -> assertThat(t).hasFieldOrPropertyWithValue("resources", List.of()));
    }

    @Test
    void talkResourcesComeFromTheContextsResourceMapKeyedBySlotId() {

        var facts = SessionFacts.of(
                SessionMother.aHeldSession()
                        .sections(List.of(aSection().slots(List.of(aSlot().id(11L).title("Talk one").build(),
                                aSlot().id(12L).title("Talk two").position(1).build())).build()))
                        .build(),
                aContext()
                        .resourcesBySlotId(
                                Map.of(11L, List.of(new ResourceLink("Slides", "https://example.com/deck.pdf")), 12L,
                                        List.of(new ResourceLink("Repo", "https://example.com/repo"),
                                                new ResourceLink("Handout", "https://example.com/handout.pdf"))))
                        .build());

        var talks = (List<?>) facts.get("talks");
        assertThat(talks).hasSize(2);
        assertThat((Map<?, ?>) talks.get(0)).hasFieldOrPropertyWithValue("resources",
                List.of(Map.of("title", "Slides", "url", "https://example.com/deck.pdf")));
        assertThat((Map<?, ?>) talks.get(1)).hasFieldOrPropertyWithValue("resources",
                List.of(Map.of("title", "Repo", "url", "https://example.com/repo"),
                        Map.of("title", "Handout", "url", "https://example.com/handout.pdf")));
    }

    @Test
    void aResourceWithABlankTitleIsAbsentNotAnEmptyLabel() {

        // Same blankToNull contract every other title-shaped fact already gets - an ArtifactEntity with a real
        // title is what the adapter always produces, but the fact map should not assume that forever.
        var facts = SessionFacts.of(
                SessionMother.aHeldSession()
                        .sections(List.of(aSection().slots(List.of(aSlot().title("Talk").build())).build())).build(),
                aContext()
                        .resourcesBySlotId(Map.of(11L, List.of(new ResourceLink("  ", "https://example.com/deck.pdf"))))
                        .build());

        assertThat((List<?>) facts.get("talks")).singleElement()
                .satisfies(t -> assertThat(t).hasFieldOrPropertyWithValue("resources",
                        List.of(mapOfTitleAndUrl(null, "https://example.com/deck.pdf"))));
    }

    private static Map<String, Object> mapOfTitleAndUrl(String title, String url) {

        var map = new LinkedHashMap<String, Object>();
        map.put("title", title);
        map.put("url", url);
        return map;
    }

    @Test
    void seriesTitleAndNextSessionAreAbsentWhenTheContextHasNone() {

        // Neither the series title nor "what's next" is something a one-off session (or a series' final
        // occurrence) has - a context that supplies neither is a real state, not an error.
        var facts = SessionFacts.of(SessionMother.aHeldSession().build(), aContext().build());

        assertThat(facts).containsEntry("seriesTitle", null).containsEntry("nextSession", null);
    }

    @Test
    void seriesTitleAndNextSessionPassThroughWhenTheContextSuppliesThem() {

        var facts = SessionFacts.of(SessionMother.aHeldSession().build(),
                aContext().seriesTitle("Frontend Dojo").nextSession("Friday, 14 August, 18:00 UTC").build());

        assertThat(facts).containsEntry("seriesTitle", "Frontend Dojo").containsEntry("nextSession",
                "Friday, 14 August, 18:00 UTC");
    }

    @Test
    void sessionUrlPassesThroughFromTheContextUnchanged() {

        // SessionFacts does not build this URL itself - it has no route knowledge and no port to the community's
        // slug. The caller assembles the real, working link and hands it in verbatim.
        var facts = SessionFacts.of(SessionMother.aHeldSession().build(), aContext().build());

        assertThat(facts).containsEntry("sessionUrl", SESSION_URL);
    }

    @Test
    void aBlankSessionUrlIsRejectedAtConstruction() {

        // Unlike seriesTitle/nextSession/resources, sessionUrl has no genuine absent state: every message this
        // context feeds wants its call to action. A silent null here would gut three messages' primary link with
        // no exception and no failing test - reject it instead, the first time anyone forgets to set it.
        assertThatThrownBy(() -> SessionFactsContext.builder().sessionUrl("").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMissingSessionUrlIsRejectedAtConstruction() {

        assertThatThrownBy(() -> SessionFactsContext.builder().build()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sessionTitleScheduledAtAndAttendeeCountPassThroughUnformatted() {

        // No titled slot: SessionMother's default section carries one ("Intro talk"), which would otherwise win
        // over the session's own title under the app's naming rule and defeat the very pass-through this test
        // means to check.
        var scheduledAt = Instant.parse("2026-08-01T18:00:00Z");
        var facts = SessionFacts.of(SessionMother.aHeldSession().title("Signals in React").sections(List.of())
                .scheduledAt(scheduledAt).attendeeCount(12L).build(), aContext().build());

        assertThat(facts).containsEntry("sessionTitle", "Signals in React").containsEntry("scheduledAt", scheduledAt)
                .containsEntry("attendeeCount", 12L);
    }

    /**
     * The two facts that stop the date line from inventing an hour. {@code scheduledAt} alone cannot say whether
     * anybody chose one, because an untimed occurrence stores midnight in its SERIES' zone - an ordinary instant. The
     * zone travels with it because that midnight is what has to be read back on the same clock it was composed on.
     */
    @Test
    void hasTimeAndTheSeriesZoneTravelBesideTheInstant() {

        var facts = SessionFacts.of(SessionMother.any().hasTime(false).seriesTimeZone("Europe/Paris")
                .scheduledAt(Instant.parse("2026-08-12T22:00:00Z")).build(), aContext().build());

        assertThat(facts).containsEntry("hasTime", false).containsEntry("timeZone", "Europe/Paris");
    }

    /**
     * A one-off records the zone it was typed in, and that is the only clock anybody chose for it, so it outranks
     * anything a reader might otherwise fall back to. The two are stated together here rather than left to whichever
     * arm a fixture happens to exercise: a session carries at most one of them in practice.
     */
    @Test
    void aSessionsOwnZoneOutranksTheSeriesItWouldOtherwiseRead() {

        var facts = SessionFacts.of(SessionMother.any().timeZone("Asia/Tokyo").seriesTimeZone("Europe/Paris").build(),
                aContext().build());

        assertThat(facts).containsEntry("timeZone", "Asia/Tokyo");
    }

    /** A one-off created before the column existed has neither zone. UTC stands in, which is what it already did. */
    @Test
    void aSessionWithNoZoneOfAnyKindReportsUtcRatherThanNothing() {

        var facts = SessionFacts.of(SessionMother.any().seriesId(null).timeZone(null).seriesTimeZone(null).build(),
                aContext().build());

        assertThat(facts).containsEntry("hasTime", true).containsEntry("timeZone", "UTC");
    }

    @Test
    void aBlankSessionTitleIsAbsentNotAnEmptyHeading() {

        // No titled slot: see sessionTitleScheduledAtAndAttendeeCountPassThroughUnformatted for why the default
        // section must be cleared here too.
        var facts = SessionFacts.of(SessionMother.aHeldSession().title("").sections(List.of()).build(),
                aContext().build());

        assertThat(facts).containsEntry("sessionTitle", null);
    }

    /**
     * The app names an untitled session by its first titled slot (session.ts:99-112). Reading session.title alone meant
     * the same session was "Colamaiar" on its page and "This session" in the mail whose button links to that page - and
     * the mail's subject line inherited the wrong name too.
     */
    @Test
    void anUntitledSessionIsNamedByItsFirstTitledSlotTheWayTheAppNamesIt() {

        var facts = SessionFacts.of(SessionMother.aHeldSession().title(null)
                .sections(List.of(aSection()
                        .slots(List.of(aSlot().title(null).build(), aSlot().id(12L).title("Colamaiar").build()))
                        .build()))
                .build(), aContext().build());

        assertThat(facts).containsEntry("sessionTitle", "Colamaiar");
    }

    @Test
    void aTitledSlotWinsOverTheSessionsOwnTitleExactlyAsItDoesInTheApp() {

        var facts = SessionFacts.of(SessionMother.aHeldSession().title("Weekly FG")
                .sections(List.of(aSection().slots(List.of(aSlot().title("Colamaiar").build())).build())).build(),
                aContext().build());

        assertThat(facts).containsEntry("sessionTitle", "Colamaiar");
    }

    @Test
    void aSessionWithNoTitledSlotFallsBackToItsOwnTitle() {

        var facts = SessionFacts.of(
                SessionMother.aHeldSession().title("Weekly FG")
                        .sections(List.of(aSection().slots(List.of(aSlot().title("   ").build())).build())).build(),
                aContext().build());

        assertThat(facts).containsEntry("sessionTitle", "Weekly FG");
    }

    /**
     * The naming rule is pinned against the app's own, in the same spirit as
     * {@code KindHueTest#theEightHuesMatchTheFrontendsOwnPaletteInOrder}. The cases above pin what this port DOES; this
     * pins what it is a port OF, so deleting or reordering a step in {@code session.ts} - swapping the two lookups, or
     * dropping the blank-title guard that makes a whitespace-only slot title fall through - fails here rather than
     * leaving a mail headed differently from the page its own button links to.
     *
     * <p>
     * Only the two ported steps are asserted. The app's final {@code return "Session"} is deliberately NOT ported (see
     * {@link SessionFacts}), so it is pinned as present-in-the-app-and-absent-here rather than as a shared rule.
     */
    @Test
    void theNamingRuleMatchesTheFrontendsOwnInOrder() throws Exception {

        // Bounded at both ends. An open-ended slice runs on through distinctPresenters and everything after it, so
        // the ordering assertion would still hold even if a step had been moved out of sessionTitle entirely and
        // merely happened to reappear further down the file - it would bite by accident rather than by design.
        var source = Files.readString(SESSION_TS);
        var body = source.substring(source.indexOf("export function sessionTitle("));
        var rule = body.substring(0, body.indexOf("\n}\n") + "\n}".length());
        assertThat(rule).doesNotContain("export function distinctPresenters(");
        var steps = List.of("for (const section of session.sections)", "section.slots.find(",
                "slot.title !== null && slot.title.trim() !== \"\"", "return titled.title;",
                "session.title !== null && session.title.trim() !== \"\"", "return session.title;",
                "return \"Session\";");

        assertThat(rule).contains(steps);
        var positions = steps.stream().map(rule::indexOf).toList();
        for (int i = 1; i < positions.size(); i++) {
            assertThat(positions.get(i)).as("step %d follows step %d", i, i - 1).isGreaterThan(positions.get(i - 1));
        }

        var untitledSection = aSection()
                .slots(List.of(aSlot().title("   ").build(), aSlot().id(12L).title("Colamaiar").build())).build();
        assertThat(SessionFacts.of(
                SessionMother.aHeldSession().title("Weekly FG").sections(List.of(untitledSection)).build(),
                aContext().build())).containsEntry("sessionTitle", "Colamaiar");
        assertThat(SessionFacts.of(SessionMother.aHeldSession().title(null).sections(List.of()).build(),
                aContext().build())).containsEntry("sessionTitle", null);
    }

    @Test
    void aSessionWithNeitherIsAbsentSoTheTemplatesOwnFallbackFires() {

        var facts = SessionFacts.of(SessionMother.aHeldSession().title(null).sections(List.of()).build(),
                aContext().build());

        assertThat(facts).containsEntry("sessionTitle", null);
    }

    @Test
    void theFactsMapCarriesExactlyTheKeysTheTemplatesRead() {

        // The template contract is by key, guarded with #strings.isEmpty, so a key SessionFacts stopped emitting
        // would render as silently absent rather than fail loudly. Pin the key set directly.
        var facts = SessionFacts.of(
                SessionMother.aHeldSession()
                        .sections(List.of(aSection().slots(List.of(aSlot().title("Talk").build())).build())).build(),
                aContext().build());

        assertThat(facts).containsOnlyKeys(FACT_KEYS);
        assertThat(onlyTalk(facts)).containsOnlyKeys(TALK_KEYS);
    }

    @Test
    void aContextBuiltWithNullCollectionsDefaultsToEmptyOnesNotNull() {

        var context = aContext().build();

        assertThat(context.displayNamesByUserId()).isEmpty();
        assertThat(context.resourcesBySlotId()).isEmpty();
        assertThat(context.sessionResources()).isEmpty();
        assertThat(context.actionPoints()).isEmpty();
        assertThat(context.checkedItems()).isEmpty();
    }

    @Test
    void sessionResourcesAreAbsentNotAnEmptyListWhenTheSessionCarriesNone() {

        var facts = SessionFacts.of(SessionMother.aHeldSession().build(), aContext().build());

        assertThat(facts).containsEntry("resources", null);
    }

    @Test
    void sessionResourcesCarryThroughAsTitleAndUrlPairsWhenTheSessionCarriesSome() {

        var facts = SessionFacts.of(SessionMother.aHeldSession().build(), aContext()
                .sessionResources(List.of(new ResourceLink("Agenda", "https://example.com/agenda.pdf"))).build());

        assertThat(facts).containsEntry("resources",
                List.of(Map.of("title", "Agenda", "url", "https://example.com/agenda.pdf")));
    }

    @Test
    void actionPointsAreAbsentNotAnEmptyListWhenTheSessionRaisedNone() {

        var facts = SessionFacts.of(SessionMother.aHeldSession().build(), aContext().build());

        assertThat(facts).containsEntry("actionPoints", null);
    }

    @Test
    void anActionPointCarriesItsResolvedAssigneeDueDateAndDoneState() {

        var facts = SessionFacts.of(SessionMother.aHeldSession().build(),
                aContext().displayNamesByUserId(Map.of("user-bob", "Bob"))
                        .actionPoints(List.of(new ActionPointSource("Write up the retro", "user-bob",
                                LocalDate.of(2026, 8, 20), ActionItemOutcome.OPEN)))
                        .build());

        assertThat(facts.get("actionPoints")).isEqualTo(List.of(Map.of("title", "Write up the retro", "assignee", "Bob",
                "dueDate", LocalDate.of(2026, 8, 20), "done", false, "dropped", false)));
    }

    @Test
    void anUnassignedActionPointRendersAssigneeAsAbsentNotAnId() {

        var facts = SessionFacts.of(SessionMother.aHeldSession().build(), aContext()
                .actionPoints(
                        List.of(new ActionPointSource("Follow up with vendor", null, null, ActionItemOutcome.OPEN)))
                .build());

        @SuppressWarnings("unchecked")
        var actionPoints = (List<Map<String, Object>>) facts.get("actionPoints");
        assertThat(actionPoints).singleElement().satisfies(p -> assertThat(p).containsEntry("assignee", null));
    }

    @Test
    void anUnresolvableAssigneeIdIsDroppedRatherThanLeakedIntoTheMail() {

        // The same embarrassing failure mode aPresenterIdWithNoResolvedNameIsAbsentNotTheRawId guards against, for
        // an action point's assignee: a member reading "Assigned to user-ghost" because nobody resolved the id.
        var facts = SessionFacts.of(SessionMother.aHeldSession().build(),
                aContext().actionPoints(List
                        .of(new ActionPointSource("Follow up with vendor", "user-ghost", null, ActionItemOutcome.OPEN)))
                        .build());

        @SuppressWarnings("unchecked")
        var actionPoints = (List<Map<String, Object>>) facts.get("actionPoints");
        assertThat(actionPoints).singleElement().satisfies(p -> assertThat(p).containsEntry("assignee", null));
    }

    @Test
    void aDoneActionPointCarriesDoneTrue() {

        var facts = SessionFacts.of(SessionMother.aHeldSession().build(),
                aContext()
                        .actionPoints(
                                List.of(new ActionPointSource("Ship the fix", null, null, ActionItemOutcome.DONE)))
                        .build());

        @SuppressWarnings("unchecked")
        var actionPoints = (List<Map<String, Object>>) facts.get("actionPoints");
        assertThat(actionPoints).singleElement()
                .satisfies(p -> assertThat(p).containsEntry("done", true).containsEntry("dropped", false));
    }

    @Test
    void aDroppedActionPointCarriesDroppedTrueAndDoneFalse() {

        var facts = SessionFacts.of(SessionMother.aHeldSession().build(),
                aContext().actionPoints(List
                        .of(new ActionPointSource("Retire the old dashboard", null, null, ActionItemOutcome.DROPPED)))
                        .build());

        @SuppressWarnings("unchecked")
        var actionPoints = (List<Map<String, Object>>) facts.get("actionPoints");
        assertThat(actionPoints).singleElement()
                .satisfies(p -> assertThat(p).containsEntry("dropped", true).containsEntry("done", false));
    }

    @Test
    void checkedItemsAreAbsentNotAnEmptyListWhenTheSessionCheckedNone() {

        var facts = SessionFacts.of(SessionMother.aHeldSession().build(), aContext().build());

        assertThat(facts).containsEntry("checkedItems", null);
    }

    @Test
    void aCheckedItemCarriesItsResolvedAssigneeAndTheDoneOutcomeLabel() {

        var facts = SessionFacts.of(SessionMother.aHeldSession().build(),
                aContext().displayNamesByUserId(Map.of("user-bob", "Bob"))
                        .checkedItems(List.of(
                                new CheckedItemSource("Write up the outage retro", "user-bob", CheckpointOutcome.DONE)))
                        .build());

        assertThat(facts.get("checkedItems")).isEqualTo(List.of(Map.of("title", "Write up the outage retro", "assignee",
                "Bob", "outcome", "Done", "outcomeKey", "DONE")));
    }

    @Test
    void aCarriedCheckedItemCarriesTheCarriedOutcomeLabel() {

        var facts = SessionFacts.of(SessionMother.aHeldSession().build(), aContext()
                .checkedItems(List.of(new CheckedItemSource("Renew the certificate", null, CheckpointOutcome.CARRIED)))
                .build());

        @SuppressWarnings("unchecked")
        var checkedItems = (List<Map<String, Object>>) facts.get("checkedItems");
        assertThat(checkedItems).singleElement()
                .satisfies(item -> assertThat(item).containsEntry("outcome", "Carried"));
    }

    @Test
    void aDroppedCheckedItemCarriesTheDroppedOutcomeLabel() {

        var facts = SessionFacts.of(SessionMother.aHeldSession().build(),
                aContext()
                        .checkedItems(
                                List.of(new CheckedItemSource("Sunset the beta flag", null, CheckpointOutcome.DROPPED)))
                        .build());

        @SuppressWarnings("unchecked")
        var checkedItems = (List<Map<String, Object>>) facts.get("checkedItems");
        assertThat(checkedItems).singleElement()
                .satisfies(item -> assertThat(item).containsEntry("outcome", "Dropped"));
    }

    @Test
    void anUnresolvableAssigneeIsDroppedFromACheckedItemRatherThanLeakedIntoTheMail() {

        var facts = SessionFacts.of(SessionMother.aHeldSession().build(),
                aContext().checkedItems(List
                        .of(new CheckedItemSource("Renew the certificate", "user-ghost", CheckpointOutcome.CARRIED)))
                        .build());

        @SuppressWarnings("unchecked")
        var checkedItems = (List<Map<String, Object>>) facts.get("checkedItems");
        assertThat(checkedItems).singleElement().satisfies(item -> assertThat(item).containsEntry("assignee", null));
    }

    /**
     * The two halves of this feature - the templates and the fact map - are otherwise never bound together by any test.
     * Rendering the real wrap-up template against a real {@code SessionFacts.of} output is the only place that would
     * catch a key the template reads and this class never sets, or a shape mismatch (a single string where the template
     * expects a list, and so on).
     */
    @Test
    void theWrapUpTemplateRendersEveryKeySessionFactsProduces() {

        var facts = SessionFacts.of(
                SessionMother.aHeldSession()
                        .sections(
                                List.of(aSection()
                                        .slots(List.of(aSlot().title("Signals in React")
                                                .presenterUserIds(List.of("user-ada")).tags(List.of("react")).build()))
                                        .build()))
                        .notes("Great turnout.").recordingUrl("https://example.com/rec").attendeeCount(9L).build(),
                aContext().displayNamesByUserId(Map.of("user-ada", "Ada"))
                        .resourcesBySlotId(
                                Map.of(11L, List.of(new ResourceLink("Slides", "https://example.com/deck.pdf"))))
                        .seriesTitle("Frontend Dojo").build());

        assertThat(facts).containsOnlyKeys(FACT_KEYS);
        assertThat(onlyTalk(facts)).containsOnlyKeys(TALK_KEYS);

        var html = render("session-wrap-up.html", facts);
        var text = render("session-wrap-up.txt", facts);

        assertThat(html).contains("Signals in React").contains("Ada").contains("Great turnout.")
                .contains("https://example.com/rec").contains("https://example.com/deck.pdf").contains(SESSION_URL)
                .contains("Frontend Dojo").doesNotContain("user-ada");
        assertThat(text).contains("Signals in React").contains("Ada").contains("Great turnout.")
                .contains("https://example.com/rec").contains("https://example.com/deck.pdf").contains("9 in the room.")
                .doesNotContain("user-ada");
        assertThat(html).doesNotContain("What was checked");
        assertThat(text).doesNotContain("What was checked");
    }

    /**
     * The wrap-up's own guard against Thymeleaf's inclusion-before-condition precedence trap (a {@code th:if} beside a
     * {@code th:replace} on the same element never runs, because inclusion is precedence 1 and the conditional is
     * precedence 3): the checked-item section's {@code th:if} sits on the wrapping element, never beside the
     * {@code th:replace} that pulls in {@code checkedItemRow}, so a session that checked nothing renders no trace of
     * the section at all - proven above - and one that checked something renders exactly that, in both halves.
     */
    @Test
    void theWrapUpTemplateRendersACheckedItemInBothHalves() {

        var facts = SessionFacts.of(SessionMother.aHeldSession().build(),
                aContext().displayNamesByUserId(Map.of("user-bob", "Bob"))
                        .checkedItems(List.of(
                                new CheckedItemSource("Renew the certificate", "user-bob", CheckpointOutcome.CARRIED)))
                        .build());

        var html = render("session-wrap-up.html", facts);
        var text = render("session-wrap-up.txt", facts);

        assertThat(html).contains("What was checked").contains("Renew the certificate").contains("Bob")
                .contains("Carried");
        assertThat(text).contains("What was checked").contains("Renew the certificate").contains("Bob")
                .contains("Carried");
    }

    /**
     * The three place facts, bound to the template that reads them. Nothing else pins the {@code .txt} against the
     * {@code .html}: {@code PreviewMatchesSendTest} compares neither, because {@code RenderedMessage} carries no text
     * field to compare, so an explicit assertion on both halves is the only thing keeping a part added to one from
     * being missing in the other.
     */
    @Test
    void theReminderRendersEveryPartOfTheSessionsPlaceInBothHalves() {

        var facts = SessionFacts.of(SessionMother.aHeldSession()
                .place(new MeetingPlace("Room 4B, Gasperich", "https://meet.example/dojo", "Passcode 4242")).build(),
                aContext().build());

        assertThat(facts).containsEntry("location", "Room 4B, Gasperich")
                .containsEntry("joinUrl", "https://meet.example/dojo").containsEntry("joiningDetails", "Passcode 4242");

        var html = render("session-upcoming.html", facts);
        var text = render("session-upcoming.txt", facts);

        assertThat(html).contains("Room 4B, Gasperich").contains("https://meet.example/dojo").contains("Passcode 4242")
                .doesNotContain("null");
        assertThat(text).contains("Room 4B, Gasperich").contains("https://meet.example/dojo").contains("Passcode 4242")
                .doesNotContain("null");
    }

    /**
     * The absent arm, in both halves. A {@code contains} assertion cannot catch an over-render, and an over-render is
     * exactly what a guard written beside a {@code th:replace} produces - the inclusion runs at precedence 1 and the
     * condition at 3, so the row renders for every session and the one that has no place renders a literal null.
     */
    @Test
    void aSessionThatMeetsNowhereRendersNoPlaceAtAllInEitherHalf() {

        var facts = SessionFacts.of(SessionMother.aHeldSession().place(MeetingPlace.NONE).build(), aContext().build());

        assertThat(facts).doesNotContainKeys("location", "joinUrl", "joiningDetails");
        assertThat(render("session-upcoming.html", facts)).doesNotContain(">Where<").doesNotContain("null");
        assertThat(render("session-upcoming.txt", facts)).doesNotContain("Where:").doesNotContain("null");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aCommunityBecomesAMonogramResolvedToLiteralHex() {

        var facts = SessionFacts.of(SessionMother.aHeldSession().build(), aContext()
                .community(new CommunityIdentity("backend-guild", "Backend Guild", "#de3b78")).communityId(7L).build());

        assertThat((Map<String, String>) facts.get("sender")).containsEntry("name", "Backend Guild")
                .containsEntry("initials", "BG").containsEntry("tintBackground", "#fae2eb")
                .containsEntry("tintForeground", "#b83468");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aCommunityWithNoAccentStillGetsAMonogram() {

        var facts = SessionFacts.of(SessionMother.aHeldSession().build(), aContext()
                .community(new CommunityIdentity("backend-guild", "Backend Guild", null)).communityId(7L).build());

        assertThat((Map<String, String>) facts.get("sender")).containsEntry("name", "Backend Guild")
                .containsKeys("tintBackground", "tintForeground");
    }

    /**
     * The same code-point-safe helper the presenter avatars use, for the same reason: a first letter outside the Basic
     * Multilingual Plane is a surrogate pair, and slicing it at {@code charAt(0)} would put a lone, unpaired surrogate
     * straight into a member's inbox. Asserts the exact resolved string rather than a code point count or a
     * trailing-char check: the emoji is the first word here, so a per-word {@code charAt(0)} truncation would still
     * leave a non-surrogate last char and the same code point count, passing either of those checks despite corrupting
     * the string. Only an exact match on the whole value catches it.
     */
    @Test
    @SuppressWarnings("unchecked")
    void aCommunityNameOutsideTheBasicMultilingualPlaneKeepsWholeCodePoints() {

        var rainbowEmoji = "🌈"; // U+1F308 RAINBOW

        var facts = SessionFacts.of(SessionMother.aHeldSession().build(), aContext()
                .community(new CommunityIdentity("guild", rainbowEmoji + " Guild", null)).communityId(7L).build());

        var initials = ((Map<String, String>) facts.get("sender")).get("initials");
        assertThat(initials).isEqualTo(rainbowEmoji + "G");
    }

    /**
     * Not reachable in production - {@code identityById} throws for an unknown community - but every other optional
     * fact uses the absent-not-blank contract, and a fact map built by hand in a test must not be the one shape that
     * throws.
     */
    @Test
    void aContextWithNoCommunityProducesAnAbsentFactRatherThanThrowing() {

        var facts = SessionFacts.of(SessionMother.aHeldSession().build(), aContext().build());

        assertThat(facts).containsEntry("sender", null);
    }

    /**
     * A community whose name is present but whitespace-only is still absent, not a monogram with nothing to render: the
     * same absent-not-blank contract every other optional fact here already gets. This is the true outcome of the
     * guard's second disjunct - {@code identity != null} but {@code blankToNull(identity.name()) == null} - which the
     * null-identity test above cannot reach on its own.
     */
    @Test
    void aCommunityWithABlankNameProducesAnAbsentFactRatherThanAnEmptyMonogram() {

        var facts = SessionFacts.of(SessionMother.aHeldSession().build(),
                aContext().community(new CommunityIdentity("guild", "   ", null)).communityId(7L).build());

        assertThat(facts).containsEntry("sender", null);
    }

    @Test
    void aNudgeCarriesWhichArmProducedItAndEveryOtherMessageCarriesNone() {

        assertThat(SessionFacts.of(SessionMother.aHeldSession().build(), aContext().nudgeArm("SEVEN_DAY").build()))
                .containsEntry("nudgeArm", "SEVEN_DAY");
        assertThat(SessionFacts.of(SessionMother.aHeldSession().build(), aContext().build())).containsEntry("nudgeArm",
                null);
    }

    /**
     * The gate on the open-slot invitation is ROOM, not time: a deadline is exactly what a frozen fact may not carry. A
     * slot is open when nobody is presenting it, counted across every section.
     */
    @Test
    void openSlotsAreCountedAcrossEverySectionAndOnlyWhereNobodyIsPresenting() {

        var facts = SessionFacts.of(SessionMother.aHeldSession()
                .sections(List.of(
                        aSection().slots(List.of(aSlot().title("Taken").presenterUserIds(List.of("user-ada")).build(),
                                aSlot().id(12L).title("Open one").build())).build(),
                        aSection().id(22L).slots(List.of(aSlot().id(13L).title("Open two").build())).build()))
                .build(), aContext().displayNamesByUserId(Map.of("user-ada", "Ada")).build());

        assertThat(facts).containsEntry("openSlotCount", 2);
    }

    /**
     * A presenter id nobody could resolve leaves the talk with no presenter to render, so the slot reads as open in the
     * mail. The count must agree with what the reader sees rather than with the raw row.
     */
    @Test
    void aSlotWhosePresenterCouldNotBeResolvedCountsAsOpenBecauseThatIsHowItRenders() {

        var facts = SessionFacts.of(SessionMother.aHeldSession()
                .sections(List.of(aSection()
                        .slots(List.of(aSlot().title("Taken").presenterUserIds(List.of("user-gone")).build())).build()))
                .build(), aContext().build());

        assertThat(facts).containsEntry("openSlotCount", 1);
    }
}
