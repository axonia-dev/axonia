package dev.axonia.expertise.domain;

/**
 * Groups the coordinates that identify a signal source: organization, community, source type, and source id.
 */
public record SignalSource(String organizationId, Long communityId, SourceType sourceType, Long sourceId) {
}
