package dev.axonia.identity;

/**
 * An Organization the caller's identity can switch into, as rendered by an Org switcher. All three decorated fields are
 * nullable, but for different reasons: {@code badgeUrl} and {@code accentColor} are absent for a row the caller's
 * <em>ambient</em> RLS scope cannot currently see (falls back to initials); {@code kind} is absent only when the
 * registry row does not exist at all. {@code kind} is the raw {@code dev.axonia.organization.OrganizationKind} enum
 * name ({@code "HUB"} or {@code "ENTERPRISE"}) so the UI can group the list into Communities vs. Organizations.
 */
public record OrganizationMembershipView(String id, String name, String badgeUrl, String accentColor, String kind) {
}
