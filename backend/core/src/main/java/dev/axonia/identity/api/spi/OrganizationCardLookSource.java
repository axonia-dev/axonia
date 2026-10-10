package dev.axonia.identity.api.spi;

import java.util.Collection;
import java.util.Map;

/**
 * Read-only port over the Organization registry, keyed by organization id. Declared here so the identity module keeps
 * zero module dependencies (enumeration stays with the identity provider); the hub module implements it.
 *
 * <p>
 * {@link #lookFor} and {@link #kindFor} are deliberately gated differently. Look (badge, accent colour) is sensitive
 * presentation data: it stays behind the caller's <strong>ambient</strong> RLS scope, so a row the caller cannot
 * currently see is simply absent — decoration never filters the membership list, the UI falls back to initials. Kind
 * (HUB vs. ENTERPRISE) is coarse, non-sensitive taxonomy: it must be resolvable for every organization id the caller's
 * own membership already vouches for, regardless of which single organization happens to be ambiently active right now
 * - otherwise the switcher mislabels every membership row until the caller has switched into it at least once, which
 * defeats the switcher's purpose. An id is absent from {@link #kindFor}'s result only when its registry row genuinely
 * does not exist.
 */
public interface OrganizationCardLookSource {

    Map<String, CardLook> lookFor(Collection<String> organizationIds);

    /**
     * {@code dev.axonia.organization.api.OrganizationKind}'s raw enum name (e.g. {@code "HUB"}, {@code "ENTERPRISE"})
     * for every organization id whose registry row exists, keyed by id — independent of whether that organization has
     * any card look configured, and independent of {@link #lookFor}'s ambient RLS scope. Carried as {@code String}, not
     * the enum itself, so the identity module never takes a compile-time dependency on the organization module.
     */
    Map<String, String> kindFor(Collection<String> organizationIds);

    /** A card's look; both parts nullable — consumers fall back to initials + derived hue. */
    record CardLook(String badgeUrl, String accentColor) {
    }
}
