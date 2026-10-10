package dev.axonia.expertise.application;

import dev.axonia.expertise.domain.EvidenceItem;

/**
 * One contribution on the map.
 *
 * <p>
 * The contribution carries an {@link EvidenceItem} rather than restating its fields, so the map resolves titles, deep
 * links and type badges through the code the evidence list is already correct with. {@code reach} is how many people it
 * reached, or null when that is unknown or does not apply.
 */
public record MapContribution(long signalId, String canonicalTag, String userId, Integer reach, EvidenceItem evidence) {
}
