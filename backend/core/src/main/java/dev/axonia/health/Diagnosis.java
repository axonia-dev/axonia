package dev.axonia.health;

import java.util.List;

/** A panel reading: independent findings, ordered risk → gap → strength. Never a composite score. */
public record Diagnosis(List<Finding> findings) {

    public Diagnosis {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
