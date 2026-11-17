package dev.axonia.roundup.application.port.out;

import java.util.Optional;

import dev.axonia.roundup.domain.ThumbnailRequest;

public interface SessionThumbnails {

    /** The composed thumbnail's public URL; empty, never a failure, when the hero cannot be read or composed. */
    Optional<String> urlFor(ThumbnailRequest request);
}
