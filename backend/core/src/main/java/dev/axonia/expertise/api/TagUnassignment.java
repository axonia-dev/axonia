package dev.axonia.expertise.api;

import java.util.Optional;

/** The canonical a tag was taken out of a named area as, and the named area it left, without it, if any held it. */
public record TagUnassignment(String tag, Optional<CuratedNamedArea> fromArea) {
}
