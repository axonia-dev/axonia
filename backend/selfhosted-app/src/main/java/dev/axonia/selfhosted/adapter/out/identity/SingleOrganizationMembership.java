package dev.axonia.selfhosted.adapter.out.identity;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.axonia.identity.api.spi.OrganizationMembershipSource;
import dev.axonia.organization.api.Organizations;
import dev.axonia.selfhosted.configuration.SelfHostedProperties;
import dev.axonia.shared.api.tenancy.OrganizationScope;

/**
 * Every subject belongs to the installation's one Organization: a token the issuer signed for this application is a
 * member's, whether or not axonia has seen that member before. Named as the registry names it, which every other page
 * shows; before the Organization is founded nobody belongs to anything.
 */
@Component
class SingleOrganizationMembership implements OrganizationMembershipSource {

    private final String organizationId;

    private final Organizations organizations;

    SingleOrganizationMembership(SelfHostedProperties properties, Organizations organizations) {
        this.organizationId = properties.organization().id();
        this.organizations = organizations;
    }

    @Override
    public List<OrganizationMembership> membershipsFor(String subject) {
        return OrganizationScope.runAs(organizationId, () -> organizations.byId(organizationId)).stream()
                .map(organization -> new OrganizationMembership(organization.id(), organization.name())).toList();
    }
}
