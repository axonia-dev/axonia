package dev.axonia.events.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import dev.axonia.events.api.Slot;
import dev.axonia.shared.api.colour.PersonTints;
import dev.axonia.shared.api.text.Initials;

/** One talk of {@link SessionFacts}: who presented it, what it was about and what it left behind. */
final class TalkFacts {

    private static final String PRESENTER_JOIN_SEPARATOR = ", ";
    private static final int DESCRIPTION_LIMIT = 200;
    private static final String ELLIPSIS = "…";
    private static final int WORD_CUT_PERCENT = 60;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private TalkFacts() {
    }

    static Map<String, Object> of(Slot slot, SessionFactsContext context) {

        var presenterNames = resolvedPresenterNames(slot, context.displayNamesByUserId());
        var talk = new LinkedHashMap<String, Object>();
        talk.put("title", SessionFacts.blankToNull(slot.title()));
        talk.put("presenter", presenterNames.isEmpty() ? null : String.join(PRESENTER_JOIN_SEPARATOR, presenterNames));
        // Beside presenter, not instead of it: presenter is the flat string every template already renders,
        // presenters is the unflattened list a talk block needs to draw one avatar per person.
        talk.put("presenters", presenterNames.stream().map(TalkFacts::presenterFact).toList());
        talk.put("subjects", slot.tags());
        talk.put("durationMinutes", slot.durationMinutes());
        // Always present, never null: the template iterates this list unconditionally once inside a talk.
        var slotResources = context.resourcesBySlotId().getOrDefault(slot.id(), List.of());
        talk.put("resources", slotResources.stream().map(link -> ResourceFacts.of(link, slot.title())).toList());
        talk.put("description", description(slot.notes()));
        talk.put("avatar", presenterNames.isEmpty() ? null : avatarFact(presenterNames.getFirst()));
        talk.put("morePresenterCount", Math.max(0, presenterNames.size() - 1));
        return Collections.unmodifiableMap(talk);
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

    private static Map<String, Object> presenterFact(String name) {

        var fact = new LinkedHashMap<String, Object>();
        fact.put("name", name);
        fact.put("initials", Initials.of(name));
        return Collections.unmodifiableMap(fact);
    }

    private static Map<String, String> avatarFact(String name) {

        var fact = new LinkedHashMap<String, String>();
        fact.put("initials", Initials.of(name));
        fact.put("tint", PersonTints.of(name));
        return Collections.unmodifiableMap(fact);
    }

    /**
     * The talk's notes on one line, cut at a word near the limit, never inside a character. A last space far back (a
     * long URL after a short word) would leave a stub, so the cut is hard there instead.
     */
    private static String description(String notes) {

        var text = notes == null ? null : SessionFacts.blankToNull(WHITESPACE.matcher(notes.strip()).replaceAll(" "));
        if (text == null || text.length() <= DESCRIPTION_LIMIT) {
            return text;
        }
        var room = text.substring(0, DESCRIPTION_LIMIT - ELLIPSIS.length() + 1);
        var space = room.lastIndexOf(' ');
        var end = space >= room.length() * WORD_CUT_PERCENT / 100 ? space : room.length() - 1;
        if (Character.isHighSurrogate(room.charAt(end - 1))) {
            end--;
        }
        return room.substring(0, end).stripTrailing() + ELLIPSIS;
    }
}
