package dev.axonia.organization;

import org.springframework.stereotype.Service;

import dev.axonia.organization.internal.MemberTally;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrganizationAdminService {

    private final Organizations organizations;
    private final OrganizationService organizationService;
    private final MemberTally memberTally;

    /** The caller's own Organization, with its member tally as {@link MemberTally} counts it. */
    public CurrentOrganization current() {
        return withTally(organizations.current());
    }

    /** Answers with the same Organization as {@link #current()}: the admin console caches this answer in its place. */
    public CurrentOrganization setCommunicationsEnabled(boolean enabled) {
        return withTally(organizationService.setCommunicationsEnabled(enabled));
    }

    private CurrentOrganization withTally(Organization organization) {
        return CurrentOrganization.of(organization, memberTally.of(organization.id()));
    }
}
