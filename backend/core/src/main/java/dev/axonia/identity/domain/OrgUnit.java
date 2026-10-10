package dev.axonia.identity.domain;

import java.util.List;
import java.util.stream.Stream;

/**
 * A person's position in the org structure, coarse → fine: {@code business_unit ⊃ department ⊃ team}. Any level may be
 * null; sourced from the synced directory (SCIM enterprise extension). All-null means nothing was synced.
 */
public record OrgUnit(String businessUnit, String department, String team) {

    /** Finest non-null label — the person's primary unit (the "team"). */
    public String leaf() {

        if (team != null) {
            return team;
        }
        if (department != null) {
            return department;
        }
        return businessUnit;
    }

    /** Non-null labels, coarse → fine. */
    public List<String> path() {

        return Stream.of(businessUnit, department, team).filter(label -> label != null).toList();
    }

    /** Stable identity for distinct-unit counting: the full path. */
    public String key() {

        return String.join(" / ", path());
    }

    /** True when no label is set — nothing was synced for this person. */
    public boolean isEmpty() {

        return businessUnit == null && department == null && team == null;
    }
}
