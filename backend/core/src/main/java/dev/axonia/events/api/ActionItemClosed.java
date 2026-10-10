package dev.axonia.events.api;

import java.util.List;

/**
 * An action item was explicitly closed - the moment its assignee earns credit for the work. Carries no organizationId
 * by convention: org is resolved from the JWT on the synchronous dispatch thread (see ActivityProjectionListener).
 */
public record ActionItemClosed(Long actionItemId, Long communityId, String assigneeUserId, List<String> tags) {
}
