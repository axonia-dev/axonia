package dev.axonia.events.internal;

import java.util.List;

/**
 * The Java half of a palette that must not fork: a direct port of {@code frontend/src/lib/kind-hue.ts}, same eight hues
 * in the same order, same character-sum hash. A community with no chosen accent is coloured by this in the app's badges
 * and by this in its mail, so the two agree by construction rather than by anyone remembering to keep them in step.
 * {@code KindHueTest} reads the TypeScript file itself and fails if the palettes ever diverge.
 *
 * <p>
 * {@code charAt} is the right unit, not {@code codePointAt}: JavaScript's {@code charCodeAt} reads UTF-16 code units,
 * so a key outside the Basic Multilingual Plane must sum its two surrogates separately here to reach the same total the
 * app reaches. Every key this is called with today is a decimal id, but a hash that only matches for ASCII would be a
 * silent divergence rather than a loud one.
 */
final class KindHue {

    private static final List<String> HUES = List.of("#de3b78", "#6e3ceb", "#d9871f", "#1aa6a0", "#f2603c", "#1f9d63",
            "#3b82de", "#9b3cc4");

    private KindHue() {
    }

    static String of(String key) {

        var sum = 0;
        for (var i = 0; i < key.length(); i++) {
            sum += key.charAt(i);
        }
        return HUES.get(sum % HUES.size());
    }

    static int indexOfHue(String hue) {

        return HUES.indexOf(hue);
    }
}
