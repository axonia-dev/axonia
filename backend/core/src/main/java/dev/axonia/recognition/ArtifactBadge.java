package dev.axonia.recognition;

/**
 * The visual identity of an artifact's type: its id, label and (tenant-managed, optional) icon and accent colour. Lets
 * the impact profile render the real artifact-type badge instead of a generic one.
 */
public record ArtifactBadge(Long typeId, String label, String icon, String accentColor) {
}
