package dev.axonia.media.api;

import java.io.InputStream;
import java.util.Optional;

/**
 * Object storage for community media (badge, hero banner). The domain works in terms of opaque keys; adapters decide
 * where the bytes physically live. The address an image is loaded from is {@link MediaLinks}'s.
 */
public interface MediaStorage {

    void store(String key, InputStream data, String contentType, long size);

    void delete(String key);

    /**
     * The bytes behind a key, for a caller that needs to read media back rather than merely link to it - a data export
     * bundling a person's avatar alongside their exported rows, say. Empty when the key names no object, rather than
     * throwing: one dead blob must not be allowed to fail a caller working through many keys.
     */
    Optional<MediaContent> open(String key);

    /** The object's length in bytes, or empty when the store holds none under this key. Reads nothing of the object. */
    Optional<Long> length(String key);
}
