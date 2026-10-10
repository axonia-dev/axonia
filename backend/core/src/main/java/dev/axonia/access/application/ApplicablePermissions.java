package dev.axonia.access.application;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.access.api.Permission;
import dev.axonia.access.api.spi.PermissionCatalogue;
import dev.axonia.shared.api.policy.PolicyOpinions;
import lombok.RequiredArgsConstructor;

/**
 * Asks the contributed {@link PermissionCatalogue}s, and falls back to the Enterprise answer: every permission the
 * edition knows.
 */
@Component
@RequiredArgsConstructor
public class ApplicablePermissions {

    private final List<PermissionCatalogue> catalogues;
    private final KnownPermissions known;

    public Set<Permission> in(String organizationId) {

        return PolicyOpinions.sole(catalogues, catalogue -> catalogue.applicableTo(organizationId))
                .orElseGet(known::all);
    }
}
