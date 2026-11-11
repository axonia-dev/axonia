package dev.axonia.expertise.api;

import java.time.Instant;

import lombok.Builder;

/**
 * A member newly declared themselves open to questions on an area others can see them on: not a repeat of an area
 * already declared, and not one a pause, an opt-out or a hidden area keeps from the community. Published inside the
 * declaring request, so a listener may read that request's organization.
 */
@Builder
public record DeclarationMade(String organizationId, long communityId, String communitySlug, String userId,
        String canonicalTag, Instant declaredAt) {
}
