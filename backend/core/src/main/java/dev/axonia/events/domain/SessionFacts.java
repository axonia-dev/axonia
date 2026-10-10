package dev.axonia.events.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import dev.axonia.events.api.ActionItemOutcome;
import dev.axonia.events.api.MeetingPlace;
import dev.axonia.events.api.ResourceLink;
import dev.axonia.events.api.Section;
import dev.axonia.events.api.Session;
import dev.axonia.events.api.Slot;
import dev.axonia.shared.api.text.Initials;

/**
 * Turns a held session into the flat fact map the mail templates render (see {@code templates/mail/session-*}). A pure
 * function: the same session and {@link SessionFactsContext} in always produce the same facts out - no repository
 * access, no port, no clock, no formatting decisions. Thymeleaf formats the instant, joins the tag lists and guards
 * absence with {@code #strings.isEmpty}; this class only decides what is present versus absent.
 *
 * <p>
 * Every fact a bare {@link Session} cannot supply by itself - a resolved presenter name, a working share URL, the
 * series' own title, what comes next, the resources attached to a session or a slot, the session's action points -
 * travels in through {@link SessionFactsContext} rather than being reached for or guessed here. See that record's
 * javadoc for where each one comes from.
 *
 * <p>
 * {@code displayNamesByUserId} exists because {@link Slot#presenterUserIds()} carries ids, never names, and a raw id
 * must never reach a member's inbox (it would render literally, e.g. "With user-123"). A presenter whose id is missing
 * from the map (an erased member the caller already resolved to a fallback label, or simply an id nobody supplied a
 * name for) is dropped rather than defaulting to the id: absent is a state the templates already render ("We did not
 * catch who presented this one."), a leaked id is not. An action point's assignee, and a checked item's, are each
 * resolved through the exact same map, for the exact same reason and with the exact same drop-if-unresolved behaviour -
 * see {@link #actionPoint} and {@link #checkedItem}.
 */
public final class SessionFacts {

    private static final String PRESENTER_JOIN_SEPARATOR = ", ";

    private SessionFacts() {
    }

    public static Map<String, Object> of(Session session, SessionFactsContext context) {

        var facts = new LinkedHashMap<String, Object>();
        facts.put("sessionTitle", sessionTitle(session));
        facts.put("seriesTitle", blankToNull(context.seriesTitle()));
        facts.put("scheduledAt", session.scheduledAt());
        facts.put("hasTime", session.hasTime());
        facts.put("timeZone", timeZone(session));
        // Not blankToNull: SessionFactsContext's own constructor already rejects a blank sessionUrl, unlike every
        // other fact here - every message this feeds wants its call to action, so there is no absent state to
        // guard for.
        facts.put("sessionUrl", context.sessionUrl());
        putPlace(facts, session);
        var talks = talks(session, context);
        facts.put("talks", talks);
        facts.put("openSlotCount", openSlotCount(talks));
        facts.put("backlogUrl", blankToNull(context.backlogUrl()));
        facts.put("resources", sessionResources(context));
        facts.put("actionPoints", actionPoints(context));
        facts.put("checkedItems", checkedItems(context));
        facts.put("followUpNotes", blankToNull(session.notes()));
        facts.put("recordingUrl", blankToNull(session.recordingUrl()));
        facts.put("attendeeCount", session.attendeeCount());
        facts.put("nextSession", blankToNull(context.nextSession()));
        facts.put("sender", sender(context));
        facts.put("nudgeArm", blankToNull(context.nudgeArm()));
        return Collections.unmodifiableMap(facts);
    }

    /**
     * The zone the untimed date line is read on, and the reason {@code hasTime} travels beside {@code scheduledAt} at
     * all. An untimed occurrence stores midnight in its series' zone, so the instant of a {@code Europe/Paris}
     * occurrence anchored to Thursday 13 August is {@code 2026-08-12T22:00Z}: formatted in UTC, as the templates format
     * every timed instant, the reminder named an hour nobody chose AND the day before the one on the schedule.
     *
     * <p>
     * A one-off's own clock wins, because a one-off belongs to no series and its zone is the only one anybody chose for
     * it. An occurrence has none of its own and reads its series'. UTC stands in only for a one-off created before the
     * column existed, which is the behaviour that session already had.
     *
     * <p>
     * This fact now governs more than the untimed day line. Every timed message reads it as the session's own clock,
     * named alongside the recipient's whenever the two differ - see {@code communication.application.MomentLine} - so a
     * one-off that fell back to UTC here would tell a member their session runs on a clock nobody set.
     */
    private static String timeZone(Session session) {

        var own = blankToNull(session.timeZone());
        if (own != null) {
            return own;
        }
        var series = blankToNull(session.seriesTimeZone());
        return series == null ? "UTC" : series;
    }

    /**
     * Where the session meets, as the three facts the templates render separately - the only facts here that are
     * OMITTED rather than set to null when there is none, because a template asks whether each key is there at all
     * before drawing its line. {@link Session#place()} is already the effective place, resolved from the occurrence's
     * own override and its series' standing place by {@link MeetingPlace}, so nothing is decided here beyond which
     * parts there are.
     *
     * <p>
     * Three keys rather than one nested map: every other fact in this map is flat, and the templates guard each line on
     * its own key with {@code th:if}, which a nested map would turn into a two-step navigation that renders a literal
     * {@code null} the moment the outer half is absent.
     */
    private static void putPlace(Map<String, Object> facts, Session session) {

        var place = session.place() == null ? MeetingPlace.NONE : session.place();
        putIfPresent(facts, "location", place.location());
        putIfPresent(facts, "joinUrl", place.joinUrl());
        putIfPresent(facts, "joiningDetails", place.joiningDetails());
    }

    private static void putIfPresent(Map<String, Object> facts, String key, String value) {

        if (value != null) {
            facts.put(key, value);
        }
    }

    /**
     * The same rule {@code session.ts}'s own {@code sessionTitle} applies: the first titled slot across the session's
     * sections, in order, then the session's own title. A session routinely carries no title of its own - a series
     * occurrence is identified by its series and its date - and the app has always named those by what is on them.
     * Reading {@code session.title} alone meant a mail was headed differently from the page its own button links to,
     * and its subject line carried the same wrong name.
     *
     * <p>
     * The final fallback is deliberately NOT ported. The app ends at the literal "Session"; the templates end at "This
     * session", which reads as a heading rather than as a list row, and only fires for a session with no title anywhere
     * in it.
     */
    private static String sessionTitle(Session session) {

        return session.sections().stream().flatMap(section -> section.slots().stream()).map(Slot::title)
                .map(SessionFacts::blankToNull).filter(Objects::nonNull).findFirst()
                .orElseGet(() -> blankToNull(session.title()));
    }

    private static List<Map<String, Object>> talks(Session session, SessionFactsContext context) {

        var talks = new ArrayList<Map<String, Object>>();
        for (Section section : session.sections()) {
            for (Slot slot : section.slots()) {
                talks.add(Collections.unmodifiableMap(talk(slot, context)));
            }
        }
        return Collections.unmodifiableList(talks);
    }

    /**
     * How many talks have nobody presenting them - the gate on the notice's invitation, and deliberately derived from
     * the ALREADY-BUILT talks rather than from the session's slots. A presenter id that resolved to nothing leaves a
     * talk with no presenter to render, so the reader sees an open slot; counting the raw slots instead would disagree
     * with the mail the reader is holding. Takes the built talks list as a parameter rather than re-reading it out of
     * the fact map: that keeps this method free of the unchecked cast a re-read would otherwise force, and this repo
     * never suppresses a PMD finding.
     */
    private static int openSlotCount(List<Map<String, Object>> talks) {

        return (int) talks.stream().filter(talk -> talk.get("presenter") == null).count();
    }

    private static Map<String, Object> talk(Slot slot, SessionFactsContext context) {

        var presenterNames = resolvedPresenterNames(slot, context.displayNamesByUserId());
        var talk = new LinkedHashMap<String, Object>();
        talk.put("title", blankToNull(slot.title()));
        talk.put("presenter", presenterNames.isEmpty() ? null : String.join(PRESENTER_JOIN_SEPARATOR, presenterNames));
        // Beside presenter, not instead of it: presenter is the flat string every template already renders,
        // presenters is the unflattened list a talk block needs to draw one avatar per person. Same source list,
        // same drop-if-unresolved rule, kept in step by construction rather than by two separate lookups.
        talk.put("presenters", presenterNames.stream().map(SessionFacts::presenterFact).toList());
        // slot.tags() needs no wrap of its own: Slot's canonical constructor already runs it through
        // List.copyOf, so it is unmodifiable before this method ever sees it.
        talk.put("subjects", slot.tags());
        talk.put("durationMinutes", slot.durationMinutes());
        // Always present, never null: a slot the context's resource map has no entry for genuinely has nothing
        // attached, and the template iterates this list unconditionally once talk.resources is non-empty.
        var slotResources = context.resourcesBySlotId().getOrDefault(slot.id(), List.of());
        talk.put("resources", slotResources.stream().map(SessionFacts::resourceFact).toList());
        return talk;
    }

    private static Map<String, Object> presenterFact(String name) {

        var fact = new LinkedHashMap<String, Object>();
        fact.put("name", name);
        fact.put("initials", Initials.of(name));
        return Collections.unmodifiableMap(fact);
    }

    /**
     * The sender fact - {@code null}, not an empty map, when the context carries none, the same absent-state contract
     * {@code seriesTitle} and {@code nextSession} use. The two tints are literal hex rather than a colour name or a
     * token, because mail can evaluate neither: see {@link AccentTint}.
     *
     * <p>
     * A session message's sender is always the community it came from. The key is {@code sender} rather than
     * {@code community} because the shell renders the same three things for any sender, and a roundup's is the
     * organization: see the header comment in {@code templates/mail/_shell.html}.
     */
    private static Map<String, String> sender(SessionFactsContext context) {

        var identity = context.community();
        if (identity == null || blankToNull(identity.name()) == null) {
            return null;
        }
        var tint = AccentTint.of(identity.accentColor(), context.communityId());
        var fact = new LinkedHashMap<String, String>();
        fact.put("name", identity.name());
        fact.put("initials", Initials.of(identity.name()));
        fact.put("tintBackground", tint.background());
        fact.put("tintForeground", tint.foreground());
        return Collections.unmodifiableMap(fact);
    }

    /**
     * The session-level resources fact - {@code null}, not an empty list, when the session carries none: unlike a
     * talk's own {@code resources} (always present, because the template loops it unconditionally once inside an
     * existing talk), the whole "Resources and slides" section this feeds is either there or it is not, the same
     * absent-state contract {@code seriesTitle} and {@code nextSession} already use.
     */
    private static List<Map<String, Object>> sessionResources(SessionFactsContext context) {

        var resources = context.sessionResources();
        return resources.isEmpty() ? null : resources.stream().map(SessionFacts::resourceFact).toList();
    }

    private static Map<String, Object> resourceFact(ResourceLink link) {

        var fact = new LinkedHashMap<String, Object>();
        fact.put("title", blankToNull(link.title()));
        fact.put("url", link.url());
        return Collections.unmodifiableMap(fact);
    }

    /**
     * The {@code actionPoints} fact - {@code null}, not an empty list, when the session raised none: the "What we
     * agreed to do" section this feeds is either there or it is not, the same absent-state contract
     * {@link #sessionResources} uses.
     */
    private static List<Map<String, Object>> actionPoints(SessionFactsContext context) {

        var sources = context.actionPoints();
        return sources.isEmpty() ? null : sources.stream().map(source -> actionPoint(source, context)).toList();
    }

    /**
     * {@code done} and {@code dropped} are both derived from the full three-state {@link ActionItemOutcome}, never
     * collapsed into one boolean: a {@code DROPPED} item is resolved, exactly like a {@code DONE} one, but the template
     * must tell the two apart - see {@code actionPointRow} in {@code _shell.html}. Collapsing to a single {@code done}
     * boolean (this class's own earlier shape) rendered a dropped item identically to an open one: struck through
     * neither, badged neither, sorted among the resolved by the repository's own {@code ORDER BY} yet drawn as if still
     * outstanding.
     */
    private static Map<String, Object> actionPoint(ActionPointSource source, SessionFactsContext context) {

        var actionPoint = new LinkedHashMap<String, Object>();
        actionPoint.put("title", blankToNull(source.title()));
        actionPoint.put("assignee", resolvedAssignee(source.assigneeUserId(), context.displayNamesByUserId()));
        actionPoint.put("dueDate", source.dueDate());
        actionPoint.put("done", source.outcome() == ActionItemOutcome.DONE);
        actionPoint.put("dropped", source.outcome() == ActionItemOutcome.DROPPED);
        return Collections.unmodifiableMap(actionPoint);
    }

    /**
     * The {@code checkedItems} fact - {@code null}, not an empty list, when the session answered none: the "What was
     * checked" section this feeds is either there or it is not, the same absent-state contract {@link #actionPoints}
     * uses.
     */
    private static List<Map<String, Object>> checkedItems(SessionFactsContext context) {

        var sources = context.checkedItems();
        return sources.isEmpty() ? null : sources.stream().map(source -> checkedItem(source, context)).toList();
    }

    /**
     * {@code outcomeKey} carries the raw {@code CheckpointOutcome} name (e.g. {@code "CARRIED"}) alongside
     * {@code outcome}'s rendered label ({@code "Carried"}): the template colours the outcome badge off this key, never
     * off the label copy - see {@code checkedItemRow} in {@code _shell.html}. Colouring off the rendered word would tie
     * the badge's colour to English wording that could change; colouring off the key does not.
     */
    private static Map<String, Object> checkedItem(CheckedItemSource source, SessionFactsContext context) {

        var checkedItem = new LinkedHashMap<String, Object>();
        checkedItem.put("title", blankToNull(source.title()));
        checkedItem.put("assignee", resolvedAssignee(source.assigneeUserId(), context.displayNamesByUserId()));
        checkedItem.put("outcome", outcomeLabel(source.outcome()));
        checkedItem.put("outcomeKey", source.outcome().name());
        return Collections.unmodifiableMap(checkedItem);
    }

    /**
     * {@code CheckpointOutcome} rendered the way a member reads it, not the way the database names it: Thymeleaf
     * decides nothing about wording here, it only places the label this returns.
     */
    private static String outcomeLabel(CheckpointOutcome outcome) {

        return switch (outcome) {
            case DONE -> "Done";
            case CARRIED -> "Carried";
            case DROPPED -> "Dropped";
        };
    }

    private static String resolvedAssignee(String assigneeUserId, Map<String, String> names) {

        return assigneeUserId == null ? null : names.get(assigneeUserId);
    }

    /**
     * A blank {@code display_name} is a real row shape (the column is {@code NOT NULL} with no check against empty),
     * not a hypothetical: dropped here, the same as an unresolved id, so {@code presenter} and {@code presenters} never
     * disagree about who counts as resolved - one filtered list feeds both facts.
     */
    private static List<String> resolvedPresenterNames(Slot slot, Map<String, String> names) {

        return slot.presenterUserIds().stream().map(names::get).filter(name -> name != null && !name.isBlank())
                .toList();
    }

    private static String blankToNull(String value) {

        return value == null || value.isBlank() ? null : value;
    }
}
