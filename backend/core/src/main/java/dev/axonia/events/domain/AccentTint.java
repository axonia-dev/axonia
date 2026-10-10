package dev.axonia.events.domain;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Turns a community's accent into the two literal hexes a mail can actually use for its monogram, because mail cannot
 * evaluate the {@code color-mix} the app renders the same badge with. The two mixes mirror {@code KindBadge.tsx}
 * exactly - background is the accent at 15% over {@code --surface}, ink is the accent at 80% over {@code --foreground}
 * - and {@code --surface} is {@code #ffffff}, which is also the mail card, so the app and the mail render the same
 * colour rather than a similar one.
 *
 * <p>
 * The contrast clamp is where this deliberately diverges from the app. The ink walks toward {@code --foreground} in
 * falling weights until it clears 4.5:1 against its own background, and measured across the eight {@link KindHue}
 * palette hues, five of them - {@code #d9871f}, {@code #1aa6a0}, {@code #f2603c}, {@code #1f9d63} and {@code #3b82de} -
 * already fall short of 4.5:1 at the unclamped 80% weight. So the clamp engages for most of the palette's own hues, not
 * only for an unvalidated tenant accent such as {@code #ffe066}: the app's 80% ink is tuned for a themed page with a
 * hover state, and a mail has one shot and no recourse, so it darkens further regardless of where the accent came from.
 * The background tint is unaffected by the clamp - it is always the accent at 15% over white, matching the app exactly.
 */
final class AccentTint {

    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");

    /**
     * The mail card's own background and its own body ink, named rather than inlined so
     * {@code AccentTintTest#theSurfaceAndForegroundAreTheMailCardsOwn} can read them straight off this class and go
     * looking for them in {@code _shell.html}. The claim in this class's javadoc - that the app and the mail render the
     * SAME colour rather than a similar one - only holds while the monogram actually sits on this surface.
     */
    static final String SURFACE_HEX = "#ffffff";
    static final String FOREGROUND_HEX = "#211a26";

    private static final int[] SURFACE = channels(SURFACE_HEX);
    private static final int[] FOREGROUND = channels(FOREGROUND_HEX);
    private static final double BACKGROUND_WEIGHT = 0.15;
    private static final double MIN_CONTRAST = 4.5;
    private static final List<Double> INK_WEIGHTS = List.of(0.80, 0.65, 0.50, 0.35, 0.20);

    record Tint(String background, String foreground) {
    }

    private AccentTint() {
    }

    static Tint of(String accentColor, long communityId) {

        var accent = channels(resolve(accentColor, communityId));
        var background = mix(accent, SURFACE, BACKGROUND_WEIGHT);
        var ink = INK_WEIGHTS.stream().map(weight -> mix(accent, FOREGROUND, weight))
                .filter(candidate -> contrast(candidate, background) >= MIN_CONTRAST).findFirst().orElse(FOREGROUND);
        return new Tint(hex(background), hex(ink));
    }

    private static String resolve(String accentColor, long communityId) {

        return accentColor != null && HEX.matcher(accentColor).matches()
                ? accentColor
                : KindHue.of(String.valueOf(communityId));
    }

    private static int[] channels(String hex) {

        return new int[]{Integer.parseInt(hex.substring(1, 3), 16), Integer.parseInt(hex.substring(3, 5), 16),
                Integer.parseInt(hex.substring(5, 7), 16)};
    }

    private static int[] mix(int[] accent, int[] over, double weight) {

        var mixed = new int[3];
        for (var channel = 0; channel < 3; channel++) {
            mixed[channel] = (int) Math.round(accent[channel] * weight + over[channel] * (1 - weight));
        }
        return mixed;
    }

    private static String hex(int[] channels) {

        return "#%02x%02x%02x".formatted(channels[0], channels[1], channels[2]);
    }

    /** WCAG 2.1 relative-luminance contrast, package-private so the tests can assert legibility rather than a hex. */
    static double contrast(String foreground, String background) {

        return contrast(channels(foreground), channels(background));
    }

    private static double contrast(int[] foreground, int[] background) {

        var lighter = Math.max(luminance(foreground), luminance(background));
        var darker = Math.min(luminance(foreground), luminance(background));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(int[] channels) {

        return 0.2126 * linear(channels[0]) + 0.7152 * linear(channels[1]) + 0.0722 * linear(channels[2]);
    }

    private static double linear(int channel) {

        var value = channel / 255.0;
        return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
