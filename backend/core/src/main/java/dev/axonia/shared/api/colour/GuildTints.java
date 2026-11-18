package dev.axonia.shared.api.colour;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A community's accent mixed into the few surfaces its roundup block paints, as plain hexes: mail clients have no
 * {@code color-mix}. The band's dark mix is the one {@code HeroThumbnailComposer} scrims with, so a band with and
 * without a thumbnail read as one colour.
 */
public final class GuildTints {

    private static final int WHITE = 0xffffff;
    private static final int BAND_INK = 0x120c16;
    private static final int BODY_INK = 0x211a26;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int CHANNEL = 0xff;
    private static final int HEX_RADIX = 16;

    private GuildTints() {
    }

    public static Map<String, String> of(String accentColor) {

        var accentHex = AccentHex.of(accentColor);
        var accent = Integer.parseInt(accentHex.substring(1), HEX_RADIX);
        var tints = new LinkedHashMap<String, String>();
        tints.put("accent", accentHex);
        tints.put("blockBackground", mix(accent, WHITE, 0.07));
        tints.put("blockBorder", mix(accent, WHITE, 0.16));
        tints.put("bandDark", mix(accent, BAND_INK, 0.30));
        tints.put("chipBackground", mix(accent, WHITE, 0.09));
        tints.put("chipText", mix(accent, BODY_INK, 0.70));
        tints.put("linkText", mix(accent, BODY_INK, 0.80));
        return Collections.unmodifiableMap(tints);
    }

    private static String mix(int accent, int base, double share) {

        var red = channel(accent >> RED_SHIFT, base >> RED_SHIFT, share);
        var green = channel(accent >> GREEN_SHIFT, base >> GREEN_SHIFT, share);
        var blue = channel(accent, base, share);
        return "#%02x%02x%02x".formatted(red, green, blue);
    }

    private static int channel(int accent, int base, double share) {

        return (int) Math.round((accent & CHANNEL) * share + (base & CHANNEL) * (1 - share));
    }
}
