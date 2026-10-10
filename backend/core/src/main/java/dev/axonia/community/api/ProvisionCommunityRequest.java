package dev.axonia.community.api;

import lombok.Builder;

/**
 * Blueprint of a hub's initial community: same name as the hub, slug = hub slug, description seeded from the tagline,
 * creator bound as ORGANIZER.
 */
@Builder
public record ProvisionCommunityRequest(String organizationId, String slug, String name, String tagline,
        String creatorUserId) {
}
