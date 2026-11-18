package dev.axonia.media.api;

import dev.axonia.shared.api.colour.AccentHex;
import lombok.Builder;

/** One session poster's slice of a community hero, tinted with the community accent as a {@code #rrggbb} hex. */
@Builder
public record HeroThumbnailRequest(String organizationId, long communityId, String heroKey, int heroFocusX,
        int heroFocusY, int sliceIndex, String accentHex) {

    public static final int CENTRED_FOCUS = 50;

    public static final String NEUTRAL_ACCENT = AccentHex.NEUTRAL;

    public static int focusOrCentre(Integer focus) {

        return focus == null ? CENTRED_FOCUS : focus;
    }

    /** The accent as six lowercase hex digits, or the neutral accent when there is none or it is not a hex. */
    public static String accentHexOf(String accentColor) {

        return AccentHex.of(accentColor);
    }
}
