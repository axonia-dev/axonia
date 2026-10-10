package dev.axonia.selfhosted.adapter.out.identity;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import dev.axonia.identity.api.spi.OrganizationCardLookSource;
import dev.axonia.organization.api.Organization;
import dev.axonia.organization.api.Organizations;
import dev.axonia.shared.api.tenancy.OrganizationScope;
import lombok.RequiredArgsConstructor;

/** The registry's kind, and no look: a single-company installation has no hub card to dress an Organization in. */
@Component
@RequiredArgsConstructor
class PlainOrganizationCard implements OrganizationCardLookSource {

    private final Organizations organizations;

    @Override
    public Map<String, CardLook> lookFor(Collection<String> organizationIds) {
        return Map.of();
    }

    @Override
    public Map<String, String> kindFor(Collection<String> organizationIds) {
        return organizationIds.stream().distinct().map(this::registered).flatMap(Optional::stream)
                .collect(Collectors.toUnmodifiableMap(Organization::id, organization -> organization.kind().name()));
    }

    private Optional<Organization> registered(String organizationId) {
        return OrganizationScope.runAs(organizationId, () -> organizations.byId(organizationId));
    }
}
