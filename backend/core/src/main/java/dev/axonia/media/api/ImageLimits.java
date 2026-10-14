package dev.axonia.media.api;

/**
 * What one use of an image accepts: a byte ceiling, and a pixel ceiling on the longest edge. Bytes alone let a
 * well-compressed 10000 pixel PNG through, which every browser then decodes in full on every frame.
 */
public record ImageLimits(long maxBytes, int maxEdge) {
}
