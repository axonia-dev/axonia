package dev.axonia.health.api;

import dev.axonia.health.api.spi.DirectorySignals;

/**
 * A contributor's org-unit, health-local by design: keeps the domain core free of identity's {@code OrgUnit} type. Any
 * level may be null (coarse → fine: business unit ⊃ department ⊃ team). Supplied by {@link DirectorySignals}.
 */
public record OrgUnitLabels(String businessUnit, String department, String team) {
}
