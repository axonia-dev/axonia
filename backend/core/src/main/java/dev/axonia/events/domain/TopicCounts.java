package dev.axonia.events.domain;

import lombok.Builder;

/** How many topics sit in each lifecycle stage of a community's backlog view. */
@Builder
public record TopicCounts(long proposed, long scheduled, long done) {
}
