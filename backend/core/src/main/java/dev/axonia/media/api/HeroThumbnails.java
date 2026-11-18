package dev.axonia.media.api;

import java.util.Optional;

public interface HeroThumbnails {

    /**
     * The composed thumbnail's public URL; empty, never a failure, when the hero cannot be read, composed or recorded.
     */
    Optional<String> urlFor(HeroThumbnailRequest request);
}
