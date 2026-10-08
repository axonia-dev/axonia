package dev.axonia.events.internal;

import dev.axonia.events.ActionItemOutcome;

/**
 * The optional narrowing {@code findFiltered} applies, carried as one value so the tenant and community stay separate,
 * required arguments - the shape {@link TopicFilter} already uses for topics.
 */
public record ActionItemFilter(ActionItemOutcome outcome, Long originSessionId, Long checkpointSessionId) {
}
