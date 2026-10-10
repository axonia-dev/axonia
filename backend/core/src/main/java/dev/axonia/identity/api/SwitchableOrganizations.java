package dev.axonia.identity.api;

import java.util.List;

/** The Organizations an identity belongs to and can switch into. */
public interface SwitchableOrganizations {

    /** The Organizations a named identity belongs to, sorted by name then id. Needs no token. */
    List<OrganizationMembershipView> organizationsOf(String subject);
}
