package dev.axonia.privacy.domain;

import java.time.Instant;

/**
 * Who an export package is about, in the words its reader already uses rather than the keys axonia files them under. A
 * record rather than five parameters on {@code ExportIndexPage#render}, which PMD would refuse, and one value rather
 * than two so the {@code index.html} header and {@code manifest.json} cannot name the same package differently.
 *
 * <p>
 * Every component is nullable for a real reason: {@code member} and {@code memberEmail} are absent for a
 * whole-Organization export, and a departed member resolves to a name with no address.
 */
public record ExportHeader(String organization, String member, String memberEmail, Instant generatedAt) {
}
