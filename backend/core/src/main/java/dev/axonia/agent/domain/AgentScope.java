package dev.axonia.agent.domain;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.SequencedSet;
import java.util.stream.Collectors;

/** The agent scope catalog: capabilities a personal token may be granted. Distinct from the RBAC Permission enum. */
public enum AgentScope {

    READ("read"), LIBRARY_CONTRIBUTE("library:contribute"), TOPIC_PROPOSE("topic:propose"), ACTION_ITEM_RAISE(
            "actionItem:raise"), SESSION_SIGNUP("session:signup"), ATTENDANCE_SIGNAL(
                    "attendance:signal"), COMMUNITY_JOIN("community:join"), SESSION_ATTACH(
                            "session:attach"), SESSION_DETACH("session:detach"), SCHEDULE_MANAGE("schedule:manage"),

    /**
     * Adding and removing the caller's own areas to learn: one scope for both, since removing takes back only the
     * member's own statement.
     */
    LEARNING_DECLARE("learning:declare"),

    /**
     * The community roster. A read scope, but deliberately not part of {@link #READ}: reading a community's schedule
     * and reading the people in it are different decisions, and a member must be able to grant one without the other.
     */
    MEMBERS_READ("members:read"),

    /**
     * Deleting a session that never took place. Destructive and irreversible, so not part of {@link #SCHEDULE_MANAGE}:
     * an agent trusted to backfill history is not thereby trusted to erase it.
     */
    SESSION_DELETE("session:delete"),

    /**
     * Keeping a community's tags small and consistent: merging, splitting and renaming tags, parking merge suggestions,
     * and grouping tags into named areas. A curator's act, like {@link #TAG_DELETE}, but kept apart from it: every one
     * of these keeps the content and the tag's history, while a delete erases both.
     */
    TAG_CURATE("tag:curate"),

    /** Stripping a tag from all of a community's content. Destructive, and a curator's act rather than a member's. */
    TAG_DELETE("tag:delete"),

    /**
     * Removing an artifact from a community's library for good. Destructive and irreversible, so not part of
     * {@link #LIBRARY_CONTRIBUTE}: an agent trusted to add to the library is not thereby trusted to empty it.
     */
    LIBRARY_REMOVE("library:remove");

    private final String code;

    AgentScope(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** Every code, in catalog (declaration) order. */
    public static SequencedSet<String> allCodes() {
        var codes = Arrays.stream(values()).map(AgentScope::code)
                .collect(Collectors.toCollection(LinkedHashSet<String>::new));
        return Collections.unmodifiableSequencedSet(codes);
    }

    public static boolean isKnown(String code) {
        return allCodes().contains(code);
    }
}
