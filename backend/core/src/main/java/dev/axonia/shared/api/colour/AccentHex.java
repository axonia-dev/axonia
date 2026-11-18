package dev.axonia.shared.api.colour;

import java.util.Locale;
import java.util.regex.Pattern;

public final class AccentHex {

    public static final String NEUTRAL = "#786a7e";

    private static final Pattern SHORT_HEX = Pattern.compile("#[0-9a-f]{3}");

    private static final Pattern LONG_HEX = Pattern.compile("#[0-9a-f]{6}");

    private AccentHex() {
    }

    /** The accent as six lowercase hex digits, or the neutral accent when there is none or it is not a hex. */
    public static String of(String accentColor) {

        var accent = accentColor == null ? "" : accentColor.strip().toLowerCase(Locale.ROOT);
        if (SHORT_HEX.matcher(accent).matches()) {
            return "#" + accent.charAt(1) + accent.charAt(1) + accent.charAt(2) + accent.charAt(2) + accent.charAt(3)
                    + accent.charAt(3);
        }
        return LONG_HEX.matcher(accent).matches() ? accent : NEUTRAL;
    }
}
