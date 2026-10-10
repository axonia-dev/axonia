package dev.axonia.communication.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import dev.axonia.communication.configuration.MailTemplateConfig;
import dev.axonia.events.api.ActionItemOutcome;
import dev.axonia.events.api.ResourceLink;
import dev.axonia.events.api.Section;
import dev.axonia.events.api.Session;
import dev.axonia.events.api.SessionStatus;
import dev.axonia.events.api.Slot;
import dev.axonia.events.domain.ActionPointSource;
import dev.axonia.events.domain.CheckedItemSource;
import dev.axonia.events.domain.CheckpointOutcome;
import dev.axonia.events.domain.SessionFacts;
import dev.axonia.events.domain.SessionFactsContext;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code PreviewMatchesSendTest} proves the preview and the send never diverge, but its {@code FACTS_JSON} is a
 * three-key hand-written literal - it never contains {@code actionPoints} and would never contain a NEW fact either, so
 * it cannot catch the class of bug this class exists to catch: a fact whose Java type does not survive a jsonb round
 * trip. That bug has already shipped twice - {@code scheduledAt} as an {@link Instant}, then
 * {@code actionPoints[].dueDate} as a {@link LocalDate} - both caught by reading, never by a test.
 *
 * <p>
 * This test builds its facts the only way that can generalise to a fact nobody has thought to hand-write into a test
 * fixture yet: by calling the REAL {@link SessionFacts#of}, with every optional fact populated at once (a talk with a
 * resolved presenter, tags and its own resource; session-level resources; two action points, one with a due date and
 * one done; a checked item; follow-up notes; a recording URL; an attendee count; a next session). Whatever
 * {@code SessionFacts.of} produces today or ever adds tomorrow flows through unmodified - serialised with the real,
 * Spring-auto-configured {@link JsonMapper} (not a bare {@code JsonMapper.builder().build()} - see
 * {@code JdbcOutboxTest}'s own javadoc for why that distinction matters), rehydrated through the real
 * {@link FactRehydration#parse}, and rendered through the real {@code session-wrap-up} template. A fact JSON cannot
 * round-trip - a third {@link Instant} or {@link LocalDate} added under a new key a template reads - either throws
 * reaching for a method a {@code String} does not have, or renders the raw ISO text verbatim; either way this test goes
 * red without anyone having to remember to update it first.
 *
 * <p>
 * Deliberately test-only, and deliberately placed here rather than in {@code events}: {@link FactRehydration} sits in
 * {@code communication.application}, outside {@code communication.api}, so no other module may call it, and calling it
 * is the entire point of this test - reading the stored facts back is where both prior bugs actually lived. The
 * production dependency graph is untouched by this file: main code still has no edge from {@code communication} to
 * {@code events} in either direction; only this test reaches "backwards" into {@code events.domain}'s
 * {@link SessionFacts} to build realistic input, the same one-directional, test-only reach {@code WrapUpProducerTest}
 * already makes in the opposite direction against {@code communication}'s own public types.
 */
class WrapUpFactsRoundTripTest {

    private static final Instant SCHEDULED_AT = Instant.parse("2026-08-15T18:30:00Z");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class));

    @Test
    void aFullyPopulatedFactsMapSurvivesTheJsonbRoundTripAndRenders() {

        contextRunner.run(context -> {
            var jsonMapper = context.getBean(JsonMapper.class);
            var facts = SessionFacts.of(fullyPopulatedSession(), fullyPopulatedContext());

            var json = jsonMapper.writeValueAsString(facts);
            var rehydrated = FactRehydration.parse(jsonMapper, json);

            var renderer = new MailRenderer(new MailTemplateConfig().mailTemplateEngine());
            var rendered = renderer.render("session-wrap-up", rehydrated);

            // The scheduled instant formatted as a date, not the raw ISO string a String would have left behind.
            assertThat(rendered.html()).contains("Saturday, 15 August 2026, 18:30 in UTC");
            // The open action point's due date, formatted from a real LocalDate, not "2026-08-22".
            assertThat(rendered.html()).contains("Due 22 Aug");
            assertThat(rendered.html()).doesNotContain("2026-08-22").doesNotContain("2026-08-15T18:30:00Z");
            // Every other optional fact rendered too, proving this was genuinely fully populated, not a lucky subset.
            assertThat(rendered.html()).contains("Understanding Signals").contains("Ada Lovelace")
                    .contains("Follow up with the vendor").contains("Write the summary blog post")
                    .contains("Renew the certificate").contains("Carried").contains("Session notes")
                    .contains("Great session, lots of questions.").contains("Open the session").contains("12 September")
                    .contains("5 in the room");
        });
    }

    private static Session fullyPopulatedSession() {

        var slot = Slot.builder().id(10L).sectionId(1L).position(0).presenterUserIds(List.of("user-1"))
                .title("Understanding Signals").durationMinutes(30).tags(List.of("frontend", "reactivity")).build();
        var section = Section.builder().id(1L).sessionId(42L).position(0).sectionTypeId(1L).sectionType("Talk")
                .title("Opening talk").durationMinutes(30).slots(List.of(slot)).build();
        // hasTime is stated rather than left to the builder's primitive default: false would mean "nobody chose an
        // hour", and the whole point of this fixture is that every fact is populated the way a real row carries it.
        return Session.builder().id(42L).seriesId(7L).communityId(3L).title("Frontend Dojo").status(SessionStatus.HELD)
                .scheduledAt(SCHEDULED_AT).hasTime(true).seriesTimeZone("UTC").durationMinutes(60)
                .facilitatorUserId("user-1").attendeeCount(5L).notes("Great session, lots of questions.")
                .recordingUrl("https://videos.example.test/rec/42").sections(List.of(section)).build();
    }

    private static SessionFactsContext fullyPopulatedContext() {

        var actionPoints = List.of(
                new ActionPointSource("Follow up with the vendor", "user-2", LocalDate.of(2026, 8, 22),
                        ActionItemOutcome.OPEN),
                new ActionPointSource("Write the summary blog post", "user-1", null, ActionItemOutcome.DONE));
        var checkedItems = List.of(new CheckedItemSource("Renew the certificate", "user-1", CheckpointOutcome.CARRIED));
        return SessionFactsContext.builder().sessionUrl("https://axonia.test/communities/frontend/sessions/42")
                .displayNamesByUserId(Map.of("user-1", "Ada Lovelace", "user-2", "Grace Hopper"))
                .seriesTitle("Frontend Dojo Series").nextSession("12 September")
                .resourcesBySlotId(Map.of(10L, List.of(new ResourceLink("Slides", "https://example.test/slides.pdf"))))
                .sessionResources(List.of(new ResourceLink("Session notes", "https://example.test/notes.pdf")))
                .actionPoints(actionPoints).checkedItems(checkedItems).build();
    }
}
