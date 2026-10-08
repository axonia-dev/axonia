package dev.axonia.events;

import lombok.Builder;

/**
 * Query-string filters for the topics list endpoint, bound from request params. {@link Builder} is the repo convention
 * for a record at five components or more, this codebase's PMD ceiling for a positional constructor; Spring's
 * request-param binding still uses the canonical constructor underneath, so it is unaffected by the builder's presence.
 */
@Builder
public record TopicQuery(TopicKind kind, TopicStatus status, TopicPhase phase, String q) {
}
