package dev.axonia.organization.internal;

import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.axonia.CurrentToken;
import dev.axonia.NotFoundException;
import dev.axonia.organization.Organization;
import dev.axonia.organization.OrganizationService;
import dev.axonia.organization.Organizations;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class OrganizationsAdapter implements Organizations {

    private final OrganizationService service;

    /**
     * Pure read — never mints a registry row. The identity module's user-provisioning filter is the sole place that
     * ensures an organization's row exists, and only once the identity provider vouches for it; a request that reaches
     * here either already has that row or its token is stale (e.g. scoped to a deleted hub), in which case the caller
     * deserves a 404, not a resurrection.
     */
    @Override
    public Organization current() {
        var organizationId = CurrentToken.organization();
        return service.organization(organizationId)
                .orElseThrow(() -> new NotFoundException("Organization not found: " + organizationId));
    }

    @Override
    public Optional<Organization> byId(String id) {
        return service.organization(id);
    }
}
