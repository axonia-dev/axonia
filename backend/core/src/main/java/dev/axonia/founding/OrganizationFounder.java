package dev.axonia.founding;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.ConflictException;
import dev.axonia.access.AccessSeeder;
import dev.axonia.community.CommunityKindDefaults;
import dev.axonia.events.SectionTypeDefaults;
import dev.axonia.knowledge.ArtifactTypeDefaults;
import dev.axonia.organization.Organization;
import dev.axonia.organization.OrganizationKind;
import dev.axonia.organization.OrganizationRegistration;
import dev.axonia.organization.OrganizationService;
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
public class OrganizationFounder {

    private final OrganizationService organizationService;
    private final AccessSeeder accessSeeder;
    private final CommunityKindDefaults communityKindDefaults;
    private final ArtifactTypeDefaults artifactTypeDefaults;
    private final SectionTypeDefaults sectionTypeDefaults;

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
