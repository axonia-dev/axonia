package dev.axonia.events.internal;

import org.springframework.stereotype.Component;

import dev.axonia.events.OrganizationAccess;
import dev.axonia.organization.OrganizationLifecycleState;
import dev.axonia.organization.OrganizationService;
import lombok.RequiredArgsConstructor;

/**
 * Org-explicit throughout: the caller is the calendar feed, which is authorized by a token rather than by a sign-in and
 * runs inside {@code OrganizationScope.runAs}, so there is no JWT to read an organization from. An Organization that
 * does not exist is not active, which keeps a deleted tenant's surviving link indistinguishable from a suspended one.
 */
@Component
@RequiredArgsConstructor
class OrganizationAccessAdapter implements OrganizationAccess {

    private final OrganizationService organizationService;

    @Override
    public boolean isActive(String organizationId) {

        return organizationService.organization(organizationId)
                .filter(organization -> organization.lifecycleState() == OrganizationLifecycleState.ACTIVE).isPresent();
    }
}
