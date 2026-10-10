package dev.axonia.founding.api;

import dev.axonia.organization.api.Organization;

/**
 * Brings an Organization into existence, complete or not at all. Must be called inside an {@code OrganizationScope} for
 * {@code request.organizationId()}.
 */
public interface OrganizationFounder {

    Organization found(FoundingRequest request);
}
