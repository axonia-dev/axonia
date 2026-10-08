package dev.axonia.events.internal;

import dev.axonia.events.TopicKind;
import dev.axonia.events.TopicStatus;
import lombok.Builder;

/** Criteria for listing a community's topics within a tenant: optional kind and status filters. */
@Builder
public record TopicListQuery(String organizationId, Long communityId, TopicKind kind, TopicStatus status) {
}
