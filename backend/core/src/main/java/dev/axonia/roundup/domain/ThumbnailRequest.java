package dev.axonia.roundup.domain;

import java.util.Locale;
import java.util.regex.Pattern;

import lombok.Builder;

/** One session poster's slice of a community hero, tinted with the community accent as a {@code #rrggbb} hex. */
@Builder
public record ThumbnailRequest(String organizationId, long communityId, String heroKey, int heroFocusX, int heroFocusY,
        int sliceIndex, String accentHex) {

    public static final int CENTRED_FOCUS = 50;

    public static final String NEUTRAL_ACCENT = "#786a7e";

    private static final Pattern SHORT_HEX = Pattern.compile("#[0-9a-f]{3}");

    private static final Pattern LONG_HEX = Pattern.compile("#[0-9a-f]{6}");

    public static int focusOrCentre(Integer focus) {

        return focus == null ? CENTRED_FOCUS : focus;
    }

    /** The accent as six lowercase hex digits, or the neutral accent when there is none or it is not a hex. */
    public static String accentHexOf(String accentColor) {

        var accent = accentColor == null ? "" : accentColor.strip().toLowerCase(Locale.ROOT);
        if (SHORT_HEX.matcher(accent).matches()) {
            return "#" + accent.charAt(1) + accent.charAt(1) + accent.charAt(2) + accent.charAt(2) + accent.charAt(3)
                    + accent.charAt(3);
        }
        return LONG_HEX.matcher(accent).matches() ? accent : NEUTRAL_ACCENT;
    }
}
