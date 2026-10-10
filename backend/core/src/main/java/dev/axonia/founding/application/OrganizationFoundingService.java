package dev.axonia.founding.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.access.api.AccessSeeding;
import dev.axonia.community.api.CommunityKindDefaults;
import dev.axonia.events.api.DefaultSectionTypes;
import dev.axonia.founding.api.FoundingRequest;
import dev.axonia.knowledge.api.DefaultArtifactTypes;
import dev.axonia.organization.api.Organization;
import dev.axonia.organization.api.OrganizationKind;
import dev.axonia.organization.api.OrganizationRegistration;
import dev.axonia.organization.api.OrganizationRegistry;
import dev.axonia.shared.api.error.ConflictException;
import lombok.RequiredArgsConstructor;

/**
 * Brings an Organization into existence, complete or not at all: the existence check, the registry row, the role
 * catalogue with its Owner binding, and the three reference vocabularies, in one transaction.
 *
 * <p>
 * The existence check runs first, inside this same transaction and scope, rather than in a caller that has not yet
 * entered {@code OrganizationScope} - a check made before that scope is entered would be meaningless under Row-Level
 * Security.
 *
 * <p>
 * Must be called inside an {@code OrganizationScope} for {@code request.organizationId()}, or the inserts violate the
 * organization isolation policy.
 */
@Component
@RequiredArgsConstructor
public class OrganizationFoundingService implements dev.axonia.founding.api.OrganizationFounder {

    private final OrganizationRegistry organizationService;
    private final AccessSeeding accessSeeder;
    private final CommunityKindDefaults communityKindDefaults;
    private final DefaultArtifactTypes artifactTypeDefaults;
    private final DefaultSectionTypes sectionTypeDefaults;

    @Override
    @Transactional
    public Organization found(FoundingRequest request) {

        if (organizationService.organizationExists(request.organizationId()))
            throw new ConflictException("organization_exists",
                    "Organization already exists: " + request.organizationId());

        var organization = organizationService.register(OrganizationRegistration.builder().id(request.organizationId())
                .name(request.name()).kind(OrganizationKind.ENTERPRISE).build());
        accessSeeder.seedEnterpriseAccess(request.organizationId(), request.owner());
        communityKindDefaults.seedFor(request.organizationId());
        artifactTypeDefaults.seedFor(request.organizationId());
        sectionTypeDefaults.seedFor(request.organizationId());
        return organization;
    }
}
