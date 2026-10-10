package dev.axonia.events.api;

/**
 * A closed action item was reopened - moved back to the backlog after a close, e.g. one made by mistake. The credit its
 * assignee earned on close is withdrawn: the expertise projection deletes the ACTION_ITEM source for this action item.
 * Carries no organizationId by convention: org is resolved from the JWT on the synchronous dispatch thread (see
 * ActivityProjectionListener).
 */
public record ActionItemReopened(Long actionItemId, Long communityId) {
}
