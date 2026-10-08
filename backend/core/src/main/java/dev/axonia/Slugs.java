package dev.axonia;

import java.text.Normalizer;
import java.util.Locale;

import lombok.experimental.UtilityClass;

/**
 * Shared slug generation utility — normalizes a display name into a URL-safe, dash-separated identifier.
 *
 * <p>
 * Used by any module that needs to derive a stable slug from a user-supplied name. The caller provides a {@code
 * fallback} for the rare edge case where the name slugifies to the empty string (e.g., a name consisting only of
 * special characters).
 */
@UtilityClass
public class Slugs {

    private final int MAX_LENGTH = 120;

    public String slugify(String name, String fallback) {

        var ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        var hyphenated = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        var trimmed = hyphenated.replaceAll("(^-+)|(-+$)", "");
        var truncated = trimmed.length() > MAX_LENGTH ? trimmed.substring(0, MAX_LENGTH) : trimmed;
        var cleaned = truncated.replaceAll("-+$", "");
        return cleaned.isEmpty() ? fallback : cleaned;
    }
}
