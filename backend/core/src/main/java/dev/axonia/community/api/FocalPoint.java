package dev.axonia.community.api;

import dev.axonia.shared.api.error.ValidationException;

/**
 * Which part of an uploaded image should survive its frame, as percentages of the image's own width and height.
 * Rendered as a CSS object-position, so it holds at any card width.
 *
 * <p>
 * Null, rather than {@link #CENTRE}, is what the product stores for an image whose point has never been set. The two
 * are visually identical today and they are not the same fact: one is an Organizer's decision and the other is the
 * absence of one.
 */
public record FocalPoint(int x, int y) {

    private static final int MIN = 0;
    private static final int MAX = 100;

    public static FocalPoint of(int x, int y) {

        requirePercentage(x);
        requirePercentage(y);
        return new FocalPoint(x, y);
    }

    /** Rebuilds a point from its two nullable columns; a half-written pair is no point at all. */
    public static FocalPoint fromColumns(Integer x, Integer y) {

        return x == null || y == null ? null : new FocalPoint(x, y);
    }

    private static void requirePercentage(int value) {

        if (value < MIN || value > MAX) {
            throw new ValidationException("INVALID_FOCAL_POINT", "Focal point must be between 0 and 100");
        }
    }
}
