package dev.axonia.events.domain;

import lombok.Builder;

/**
 * What {@code ActionItemStore#pinLateBound} resolves to: the community whose open, unpinned items late-bind, and the
 * session they bind to. A parameter record rather than a five-argument method, per this repository's five-parameter
 * ceiling; the two {@link java.time.Instant} arguments stay outside it as plain named parameters, because Spring Data
 * JDBC's SpEL binding bypasses the {@code Instant} to timestamp converter, so {@code :#{#v.scheduledAt}} would reach
 * the driver as an unparseable ISO string.
 */
@Builder
public record LateBindingPin(String organizationId, Long communityId, Long sessionId) {
}
