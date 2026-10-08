package dev.axonia.organization;

import lombok.Builder;

/**
 * The caller's own Organization as its administrator sees it: the registry entry, and how many members it has as
 * {@link MemberCountPolicy} counts them.
 */
@Builder
public record CurrentOrganization(String id, String name, OrganizationKind kind,
        OrganizationLifecycleState lifecycleState, boolean communicationsEnabled, long memberCount) {

    static CurrentOrganization of(Organization organization, long memberCount) {

        return CurrentOrganization.builder().id(organization.id()).name(organization.name()).kind(organization.kind())
                .lifecycleState(organization.lifecycleState())
                .communicationsEnabled(organization.communicationsEnabled()).memberCount(memberCount).build();
    }
}
