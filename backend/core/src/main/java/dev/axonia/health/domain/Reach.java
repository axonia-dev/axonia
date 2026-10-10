package dev.axonia.health.domain;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import dev.axonia.health.api.OrgUnitLabels;

/**
 * Cross-team reach: distinct org-units among contributors, counted at three grains. Units in, counts out — identity
 * (the userId keys) was dropped before this type, so nothing downstream can name a person or a single-person team.
 * Same-named units under different parents stay distinct because each grain keys on its full path prefix.
 */
public final class Reach {

    private static final String SEP = "\u0000";

    private final int teams;
    private final int departments;
    private final int businessUnits;
    private final int contributorsWithUnit;
    private final int totalContributors;

    private Reach(int teams, int departments, int businessUnits, int contributorsWithUnit, int totalContributors) {

        this.teams = teams;
        this.departments = departments;
        this.businessUnits = businessUnits;
        this.contributorsWithUnit = contributorsWithUnit;
        this.totalContributors = totalContributors;
    }

    /** Builds the reach from the org-units of the contributors that have one, against the total contributor count. */
    public static Reach of(Collection<OrgUnitLabels> units, int totalContributors) {

        Set<String> businessUnits = new HashSet<>();
        Set<String> departments = new HashSet<>();
        Set<String> teams = new HashSet<>();
        for (OrgUnitLabels unit : units) {
            if (unit.businessUnit() != null) {
                businessUnits.add(unit.businessUnit());
            }
            if (unit.department() != null) {
                departments.add(unit.businessUnit() + SEP + unit.department());
            }
            if (unit.team() != null) {
                teams.add(unit.businessUnit() + SEP + unit.department() + SEP + unit.team());
            }
        }
        // units holds only contributors that have a synced unit, so its size is contributorsWithUnit
        return new Reach(teams.size(), departments.size(), businessUnits.size(), units.size(), totalContributors);
    }

    public int teams() {

        return teams;
    }

    public int departments() {

        return departments;
    }

    public int businessUnits() {

        return businessUnits;
    }

    public int contributorsWithUnit() {

        return contributorsWithUnit;
    }

    public int totalContributors() {

        return totalContributors;
    }

    /** False when no contributor has any synced unit — the dial reads "unavailable", never "0 teams". */
    public boolean available() {

        return contributorsWithUnit > 0;
    }
}
