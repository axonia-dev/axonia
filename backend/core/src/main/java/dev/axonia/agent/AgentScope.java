package dev.axonia.agent;

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
    MEMBERS_READ("members:read");

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
