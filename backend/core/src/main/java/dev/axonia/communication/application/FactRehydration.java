package dev.axonia.communication.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a stored outbox row's {@code facts} jsonb column back into the map {@link MailRenderer} needs to render it -
 * shared by {@link OutboxRowProcessor#compose}, rendering a row for real delivery, and {@link MessageRenderAdapter},
 * rendering a stored row for a preview once one already exists. Both callers hit the identical problem: {@code events}'
 * {@code SessionFacts.of} (not linkable from here - {@code communication} never depends on {@code events}) puts real
 * {@code java.time.Instant} and {@code java.time.LocalDate} values into the facts map a producer hands to
 * {@code enqueue}, but a row read back out has crossed a JSON round trip through {@code Map<String, Object>}'s type
 * erasure - {@code jsonMapper} has no field-level type information to reconstruct either from, so both come back out as
 * the plain strings they were written as. Every {@code session-*} template calls {@code scheduledAt.atZone(...)} and
 * formats a due date expecting the real types, so an unrehydrated string fails every render, not just the one fact's
 * own formatting.
 *
 * <p>
 * Existing only once, rather than once per caller, is the point: before this class existed, the render path a preview
 * takes and the render path a real send takes each carried their own copy of this repair, and a change to one with no
 * matching change to the other would have meant a preview and its real send silently rehydrating the exact same jsonb
 * two different ways - undetectable by either caller's own tests.
 */
public final class FactRehydration {

    private static final TypeReference<Map<String, Object>> FACTS_TYPE = new TypeReference<>() {
    };

    private FactRehydration() {
    }

    /** Parses a row's raw {@code facts} jsonb and repairs both temporal facts in one call. */
    public static Map<String, Object> parse(JsonMapper jsonMapper, String factsJson) {

        var facts = new LinkedHashMap<>(jsonMapper.readValue(factsJson, FACTS_TYPE));
        rehydrateScheduledAt(facts);
        rehydrateActionPointDueDates(facts);
        return facts;
    }

    /**
     * The {@code instanceof String} check is a live assumption, not a formality: {@code events}' {@code SessionFacts}
     * is the only producer of this fact today, and {@code jsonMapper}'s current configuration is what makes "a String"
     * the only shape a JSON round trip can leave behind. Either half of that could change under this code without
     * changing this method - a future producer adding a second temporal fact, or {@code write-dates-as-timestamps} ever
     * getting enabled on the auto-configured {@code JsonMapper}, would make {@code scheduledAt} come back as something
     * other than a {@code String}, the {@code instanceof} would quietly not fire, and every template's {@code atZone}
     * call would fail exactly as it did before this method existed - just with no signal pointing back here. A
     * present-but-unrecognized value therefore fails loudly, right here, rather than silently passing through to
     * resurface as an opaque OGNL stack trace several layers into Thymeleaf.
     */
    private static void rehydrateScheduledAt(Map<String, Object> facts) {

        var value = facts.get("scheduledAt");
        if (value == null || value instanceof Instant) {
            return;
        }
        if (value instanceof String iso) {
            facts.put("scheduledAt", Instant.parse(iso));
            return;
        }
        throw new IllegalStateException(
                "scheduledAt fact is neither absent, an Instant, nor a String: " + value.getClass().getName());
    }

    /**
     * The exact same repair as {@link #rehydrateScheduledAt}, for the second temporal fact {@code events}'
     * {@code SessionFacts} now writes: {@code actionPoints[].dueDate} is a real {@code java.time.LocalDate} going into
     * {@code JdbcOutbox}, but {@code facts} just crossed the same type-erased JSON round trip, so each {@code dueDate}
     * comes back out as the plain {@code yyyy-MM-dd} string it was written as, or is simply absent when that action
     * point carries no due date. Every {@code session-wrap-up} template that formats a due date would otherwise either
     * print the raw ISO string or throw reaching for a method a {@code String} does not have - the same failure
     * {@code scheduledAt} would have hit unrehydrated.
     *
     * <p>
     * {@code actionPoints} itself being absent is not an error - a session that raised none never sets the fact at all,
     * see {@code events}' {@code SessionFacts.of}. Once it is present, though, this repairs every element's
     * {@code dueDate} unconditionally, with the same absent / already-typed / {@code String} / else-throw ladder
     * {@link #rehydrateScheduledAt} uses and for the identical reason: a future change to what shape {@code dueDate}
     * travels in - a new producer, a mapper reconfigured to write dates as numbers - must fail loudly right here, not
     * three layers deeper inside Thymeleaf with a stack trace that names OGNL, not the real cause.
     */
    private static void rehydrateActionPointDueDates(Map<String, Object> facts) {

        var value = facts.get("actionPoints");
        if (value == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        var actionPoints = (List<Map<String, Object>>) value;
        actionPoints.forEach(FactRehydration::rehydrateDueDate);
    }

    private static void rehydrateDueDate(Map<String, Object> actionPoint) {

        var value = actionPoint.get("dueDate");
        if (value == null || value instanceof LocalDate) {
            return;
        }
        if (value instanceof String iso) {
            actionPoint.put("dueDate", LocalDate.parse(iso));
            return;
        }
        throw new IllegalStateException(
                "actionPoint dueDate fact is neither absent, a LocalDate, nor a String: " + value.getClass().getName());
    }
}
