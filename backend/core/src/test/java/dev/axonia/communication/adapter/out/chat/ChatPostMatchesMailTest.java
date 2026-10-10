package dev.axonia.communication.adapter.out.chat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.application.FactRehydration;
import dev.axonia.communication.application.MailRenderer;
import dev.axonia.communication.configuration.MailTemplateConfig;
import dev.axonia.community.api.CommunityIdentity;
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
 * A post and its email are one message said twice, not two messages kept in step by hand - the one property the chat
 * channel design was chosen for. Both render from the identical rehydrated facts map returned by
 * {@link FactRehydration#parse}, so a fact that reaches one render and not the other is a failure here, in a fast unit
 * test, rather than a divergence somebody notices in production months later.
 *
 * <p>
 * The facts come from the real {@link SessionFacts#of}, not a hand-typed literal map: a hand-typed map only proves the
 * two renderers agree on the keys someone remembered to type, which is exactly the class of gap this test exists to
 * catch. Calling the production fact-builder means a key {@code SessionFacts.of} adds tomorrow shows up here too,
 * without anyone updating this file first - the same reasoning {@code WrapUpFactsRoundTripTest} already applies to the
 * jsonb round trip; this class applies it to the mail/post pair.
 */
class ChatPostMatchesMailTest {

    private static final Instant SCHEDULED_AT = Instant.parse("2026-08-15T18:30:00Z");

    /**
     * Read by both {@link MailRenderer} (via {@code session-wrap-up}) and {@link ChatPostBuilders#build}.
     *
     * <p>
     * {@code talks} and {@code resources} are each claimed here once, for the whole key -
     * {@code everySessionFactIsAccountedForOnOneSideOfTheChatBoundary} below partitions TOP-LEVEL fact keys only. The
     * post reads only {@code talks[].title} and {@code talks[].presenter} out of every field a talk carries
     * ({@code subjects}, {@code durationMinutes}, {@code resources}, {@code presenters}), and only
     * {@code resources[].title}/{@code resources[].url} rather than anything more that key might one day carry. A
     * future nested field the mail starts rendering off an existing talk or an existing session-level resource would
     * not trip that completeness check; only a brand-new top-level fact does.
     */
    private static final Set<String> FACTS_THE_POST_CONSUMES = Set.of("sessionTitle", "sender", "sessionUrl",
            "scheduledAt", "hasTime", "timeZone", "talks", "recordingUrl", "attendeeCount", "followUpNotes",
            "resources");

    /**
     * {@code SessionFacts.of} writes these for every wrap-up too, and the wrap-up mail genuinely renders every one of
     * them - see {@code factsTheWrapUpMailRendersButThePostHasNoSlotForNeverReachTheChatPost} below, which renders each
     * one and then proves it absent from the post. Each has its own reason to stay out, not one blanket "the post is
     * smaller":
     * <ul>
     * <li>{@code seriesTitle} - not among what the post carries (presenters, the description, the follow-up note,
     * resources and the recording link), and the post's headline already carries the session's own title, leaving no
     * second heading slot for a series name too.</li>
     * <li>{@code actionPoints} - likewise not carried: a per-assignee, per-due-date list is exactly the structured,
     * multi-row content a flat chat card cannot hold without becoming unreadable, the same reason
     * {@code talks[].resources} stays out below.</li>
     * <li>{@code nextSession} - likewise not carried, and a second forward-looking pointer would compete with the
     * post's one call to action (one button, and a second useful thing is a link in the body).</li>
     * <li>{@code checkedItems} - the same structured, multi-row shape as {@code actionPoints} and not carried either:
     * what a session checked was added later, and it carries the exact reason {@code actionPoints} stays out - a
     * per-assignee, per-outcome list is exactly what a flat chat card cannot hold.</li>
     * </ul>
     * {@code ChatPost} is deliberately not widened to carry any of them: a real gap gets fixed in
     * {@code ChatPostBuilders}, never by adding a field to {@code ChatPost}. Any fact {@code
     * SessionFacts.of} adds in the future that the wrap-up mail renders belongs in exactly one of this set or
     * {@link #FACTS_THE_POST_CONSUMES} - deciding which is the whole point of
     * {@code everySessionFactIsAccountedForOnOneSideOfTheChatBoundary} below.
     */
    private static final Set<String> FACTS_THE_WRAPUP_MAIL_RENDERS_BUT_THE_POST_CANNOT_CARRY = Set.of("seriesTitle",
            "actionPoints", "checkedItems", "nextSession");

    /**
     * {@code SessionFacts.of} writes one fact map shared by all three session templates: {@code openSlotCount} and
     * {@code backlogUrl} exist for {@code session-upcoming}, {@code nudgeArm} for {@code session-awaiting-close}, so a
     * wrap-up row carries all three regardless of whether wrap-up has any use for them. Neither the wrap-up mail nor
     * the wrap-up post reads any of them; they are not part of the deliberate asymmetry
     * {@link #FACTS_THE_WRAPUP_MAIL_RENDERS_BUT_THE_POST_CANNOT_CARRY} names, they are simply inert here.
     */
    private static final Set<String> FACTS_IRRELEVANT_TO_A_WRAP_UP = Set.of("openSlotCount", "backlogUrl", "nudgeArm");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class));

    private MailRenderer renderer;
    private JsonMapper jsonMapper;
    private String storedFactsJson;

    @BeforeEach
    void setUp() {

        renderer = new MailRenderer(new MailTemplateConfig().mailTemplateEngine());
        // The real, Spring-auto-configured JsonMapper, not a bare JsonMapper.builder().build(): scheduledAt is a
        // java.time.Instant and actionPoints[].dueDate a java.time.LocalDate going in, and only the auto-configured
        // mapper writes either as the ISO string FactRehydration.parse's own repair expects back - see
        // WrapUpFactsRoundTripTest's javadoc, which this setup mirrors for the identical reason.
        contextRunner.run(context -> {
            jsonMapper = context.getBean(JsonMapper.class);
            storedFactsJson = jsonMapper.writeValueAsString(SessionFacts.of(aWrapUpSession(), aWrapUpContext()));
        });
    }

    private String storedFactsJsonForAWrapUp() {

        return storedFactsJson;
    }

    @Test
    void everySalientFactInTheMailAlsoReachesThePost() {

        var facts = FactRehydration.parse(jsonMapper, storedFactsJsonForAWrapUp());

        var mail = renderer.render("session-wrap-up", facts);
        var post = new ChatPostBuilders().build(MessageType.SESSION_WRAP_UP, facts);

        // sessionTitle: the talk's own title here, since the session carries no title of its own and SessionFacts
        // falls back to the first titled slot - a fact rename or a fallback changed on one side only fails this.
        assertThat(mail.html()).contains("Event sourcing in anger");
        assertThat(post.headline()).isEqualTo("Event sourcing in anger");

        // followUpNotes -> the mail's note block and the post's body: a value transformed differently on either
        // side (escaped one way, truncated, re-labelled) fails this exact-equality check, not just a contains.
        assertThat(mail.html()).contains("We agreed to spike it.");
        assertThat(post.body()).isEqualTo("We agreed to spike it.");

        // recordingUrl: the mail's href and the post's own "Recording" detail row.
        assertThat(mail.html()).contains("https://rec.example/1");
        assertThat(post.details()).containsEntry("Recording", "https://rec.example/1");

        // resources (session-level): the mail's "From the session" block and the post's own "Resources" detail
        // row - per-talk resources are a deliberate, separately-named omission below, not this one.
        assertThat(mail.html()).contains("Session notes").contains("https://axonia.example/notes/1");
        assertThat(post.details()).containsEntry("Resources", "Session notes: https://axonia.example/notes/1");

        // sender: SessionFacts#sender is the same map key mail and post both read off "sender" - a rename to
        // "community" on one side (which has already happened once in this codebase, see SessionFacts' own
        // javadoc) fails only the side that was not renamed.
        assertThat(mail.html()).contains("Backend Guild");
        assertThat(post.sender()).isEqualTo("Backend Guild");

        // attendeeCount: a count the mail states as a stat-strip chip and the post states as a detail row - a
        // count silently rounded, dropped, or off by one on either side fails this.
        assertThat(mail.html()).contains("3 in the room");
        assertThat(post.details()).containsEntry("Attended", "3");

        // talks[].presenter, joined into the post's flat "Talks" line - proves the same presenter name that made
        // it past FactRehydration and into the mail also made it into the post's own rendering of the same list.
        assertThat(mail.html()).contains("Ada");
        assertThat(post.details().get("Talks")).contains("Ada");

        // scheduledAt is the fact FactRehydration exists to repair: an unrehydrated String would still format in
        // the mail (Thymeleaf's atZone call is on the real fact), but ChatPostBuilders#when's own
        // "instanceof Instant" guard would silently drop the date from the post's whenLine instead of throwing -
        // exactly the kind of one-sided failure this class exists to catch.
        assertThat(mail.html()).contains("15 August 2026");
        assertThat(post.whenLine()).contains("15 August 2026");
    }

    /**
     * {@code Map.copyOf(facts)} taken before both renders and compared after would be vacuous: it is a SHALLOW copy of
     * {@code facts}' own entries, so a bug that mutated a NESTED value reached through {@code facts} - one talk's own
     * map, say - would leave {@code before} and the live {@code facts} pointing at the exact same mutated nested
     * object, and the two would still compare equal. Neither {@link MailRenderer} nor {@link ChatPostBuilders} mutates
     * anything today (Thymeleaf's {@code Context} copies {@code facts}' entries into its own internal map rather than
     * reading through the reference it was handed, and {@link ChatPostBuilders} only ever reads), so this specific bug
     * class does not exist yet - but a test that cannot fail even when deliberately provoked proves nothing about that.
     * Snapshotting through the same {@code jsonMapper} instead serialises every value {@code facts} reaches BY CONTENT,
     * not by reference, so a mutation anywhere in the structure - top level or nested - changes the snapshot and fails
     * this test.
     */
    @Test
    void bothReadTheSameFactsInstanceSoNeitherCanBeGivenADifferentOne() {

        var facts = FactRehydration.parse(jsonMapper, storedFactsJsonForAWrapUp());
        var before = jsonMapper.writeValueAsString(facts);

        renderer.render("session-wrap-up", facts);
        new ChatPostBuilders().build(MessageType.SESSION_WRAP_UP, facts);

        assertThat(jsonMapper.writeValueAsString(facts)).isEqualTo(before);
    }

    /**
     * States the deliberate asymmetry rather than hiding it: every fact named in
     * {@link #FACTS_THE_WRAPUP_MAIL_RENDERS_BUT_THE_POST_CANNOT_CARRY} genuinely renders in the mail below, proving the
     * fixture is realistic and not a lucky subset, and then {@code post.toString()} - covering every field
     * {@link ChatPost} has at once, so a future field added to the record needs no matching line here to be watched -
     * is asserted to contain none of it. A regression that started leaking one of these facts into the post (or, since
     * this is a record, widening {@code ChatPost} to carry one without a real design decision behind it) fails here.
     */
    @Test
    void factsTheWrapUpMailRendersButThePostHasNoSlotForNeverReachTheChatPost() {

        var facts = FactRehydration.parse(jsonMapper, storedFactsJsonForAWrapUp());

        var mail = renderer.render("session-wrap-up", facts);
        var post = new ChatPostBuilders().build(MessageType.SESSION_WRAP_UP, facts);

        assertThat(mail.html()).contains("Event Sourcing Series") // seriesTitle
                .contains("Write the RFC") // actionPoints
                .contains("Renew the certificate") // checkedItems
                .contains("Backend Dojo on 29 August") // nextSession
                .contains("kafka") // talks[].subjects, a per-talk fact the post's flat talk line drops too
                .contains("https://axonia.example/slides/99"); // talks[].resources, likewise dropped

        assertThat(post.toString()).doesNotContain("Event Sourcing Series").doesNotContain("Write the RFC")
                .doesNotContain("Renew the certificate").doesNotContain("Backend Dojo on 29 August")
                .doesNotContain("kafka").doesNotContain("https://axonia.example/slides/99");
    }

    /**
     * The mail and the post read one instant, so they have to read {@code hasTime} the same way too. An occurrence
     * nobody chose an hour for stores midnight in its SERIES' zone, so a {@code Europe/Paris} occurrence anchored to
     * Thursday 13 August is {@code 2026-08-12T22:00Z}: formatted in UTC, both surfaces named an hour nobody chose and
     * the day before the one on the schedule.
     */
    @Test
    void anUntimedWrapUpNamesItsOwnDayOnBothSides() {

        contextRunner.run(context -> {
            var untimed = aTimedWrapUpSession().hasTime(false).seriesTimeZone("Europe/Paris")
                    .scheduledAt(Instant.parse("2026-08-12T22:00:00Z")).build();
            var facts = FactRehydration.parse(context.getBean(JsonMapper.class),
                    context.getBean(JsonMapper.class).writeValueAsString(SessionFacts.of(untimed, aWrapUpContext())));

            var mail = renderer.render("session-wrap-up", facts);
            var post = new ChatPostBuilders().build(MessageType.SESSION_WRAP_UP, facts);

            assertThat(mail.html()).contains("Held Thursday, 13 August 2026").doesNotContain("22:00")
                    .doesNotContain("12 August 2026");
            assertThat(post.whenLine()).isEqualTo("Held 13 August 2026");
        });
    }

    /**
     * The completeness check the other tests lean on: every key {@code SessionFacts.of} writes is claimed by exactly
     * one of the three sets above. An unclaimed key - the shape a future fact takes the day someone adds it to the mail
     * and forgets the post - fails here immediately, forcing the decision this class's javadoc asks for rather than
     * letting the gap sit unnoticed until the CPD-guarded rendering assertions above happen not to cover it.
     */
    @Test
    void everySessionFactIsAccountedForOnOneSideOfTheChatBoundary() {

        var facts = FactRehydration.parse(jsonMapper, storedFactsJsonForAWrapUp());

        var accountedFor = new HashSet<String>();
        accountedFor.addAll(FACTS_THE_POST_CONSUMES);
        accountedFor.addAll(FACTS_THE_WRAPUP_MAIL_RENDERS_BUT_THE_POST_CANNOT_CARRY);
        accountedFor.addAll(FACTS_IRRELEVANT_TO_A_WRAP_UP);

        assertThat(facts.keySet()).isEqualTo(accountedFor);
    }

    private static Session aWrapUpSession() {

        return aTimedWrapUpSession().build();
    }

    /**
     * {@code hasTime} and {@code seriesTimeZone} are stated rather than left to the builder's defaults, because
     * {@code false} is a real state with a rendering of its own rather than an unset field: see
     * {@link #anUntimedWrapUpNamesItsOwnDayOnBothSides} for the pair those two facts drive.
     */
    private static Session.SessionBuilder aTimedWrapUpSession() {

        var slot = Slot.builder().id(1L).sectionId(1L).position(0).presenterUserIds(List.of("user-1"))
                .title("Event sourcing in anger").tags(List.of("kafka")).build();
        var section = Section.builder().id(1L).sessionId(1L).position(0).sectionTypeId(1L).sectionType("Talk")
                .slots(List.of(slot)).build();
        return Session.builder().id(1L).communityId(9L).status(SessionStatus.HELD).scheduledAt(SCHEDULED_AT)
                .hasTime(true).seriesTimeZone("UTC").notes("We agreed to spike it.")
                .recordingUrl("https://rec.example/1").attendeeCount(3L).sections(List.of(section));
    }

    private static SessionFactsContext aWrapUpContext() {

        var actionPoints = List.of(
                new ActionPointSource("Write the RFC", "user-1", LocalDate.of(2026, 8, 20), ActionItemOutcome.OPEN));
        var checkedItems = List.of(new CheckedItemSource("Renew the certificate", "user-1", CheckpointOutcome.CARRIED));
        return SessionFactsContext.builder().sessionUrl("https://axonia.test/s/1")
                .displayNamesByUserId(Map.of("user-1", "Ada")).seriesTitle("Event Sourcing Series")
                .nextSession("Backend Dojo on 29 August")
                .resourcesBySlotId(Map.of(1L, List.of(new ResourceLink(null, "https://axonia.example/slides/99"))))
                .sessionResources(List.of(new ResourceLink("Session notes", "https://axonia.example/notes/1")))
                .actionPoints(actionPoints).checkedItems(checkedItems)
                .community(new CommunityIdentity("backend-guild", "Backend Guild", null)).communityId(9L).build();
    }
}
