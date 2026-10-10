package dev.axonia.events.application;

import dev.axonia.events.api.TopicKind;
import dev.axonia.events.api.TopicStatus;
import lombok.Builder;

/** Criteria for listing a community's topics within a tenant: optional kind and status filters. */
@Builder
public record TopicListQuery(String organizationId, Long communityId, TopicKind kind, TopicStatus status) {
}
