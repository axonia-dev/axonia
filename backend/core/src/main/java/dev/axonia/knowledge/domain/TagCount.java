package dev.axonia.knowledge.domain;

import lombok.Builder;

/** A tag and how many of the library's artifacts in a window carry it. */
@Builder
public record TagCount(String tag, long count) {
}
