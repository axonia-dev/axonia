package dev.axonia.health.domain;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import dev.axonia.health.api.OrgUnitLabels;

/**
 * Cross-team reach: distinct org-units among speakers, counted at three grains. Units in, counts out — identity (the
 * userId keys) was dropped before this type, so nothing downstream can name a person or a single-person team.
 * Same-named units under different parents stay distinct because each grain keys on its full path prefix.
 */
public final class Reach {

    private static final String SEP = "\u0000";

    private final int teams;
    private final int departments;
    private final int businessUnits;
    private final int speakersWithUnit;
    private final int totalSpeakers;

    private Reach(int teams, int departments, int businessUnits, int speakersWithUnit, int totalSpeakers) {

        this.teams = teams;
        this.departments = departments;
        this.businessUnits = businessUnits;
        this.speakersWithUnit = speakersWithUnit;
        this.totalSpeakers = totalSpeakers;
    }

    /** Builds the reach from the org-units of the speakers that have one, against the total speaker count. */
    public static Reach of(Collection<OrgUnitLabels> units, int totalSpeakers) {

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
        // units holds only speakers that have a synced unit, so its size is speakersWithUnit
        return new Reach(teams.size(), departments.size(), businessUnits.size(), units.size(), totalSpeakers);
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

    public int speakersWithUnit() {

        return speakersWithUnit;
    }

    public int totalSpeakers() {

        return totalSpeakers;
    }

    /** False when no speaker has any synced unit — the dial reads "unavailable", never "0 teams". */
    public boolean available() {

        return speakersWithUnit > 0;
    }
}
