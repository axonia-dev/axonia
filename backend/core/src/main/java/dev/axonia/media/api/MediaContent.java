package dev.axonia.media.api;

import java.io.InputStream;

/** A stored object, opened. The caller closes the stream. {@code length} is -1 when the store did not say. */
public record MediaContent(InputStream stream, long length) {
}
