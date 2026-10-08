package dev.axonia.events.internal;

import dev.axonia.events.ActionItemOutcome;
import lombok.Builder;

/**
 * Criteria for listing a community's action items within a tenant: optional outcome, origin-session and
 * checkpoint-session filters. {@link Builder} is the repo convention for a record at five components or more, this
 * codebase's PMD ceiling for a positional constructor.
 */
@Builder
public record ActionItemListQuery(String organizationId, Long communityId, ActionItemOutcome outcome,
        Long originSessionId, Long checkpointSessionId) {
}
