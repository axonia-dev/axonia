package dev.axonia.access.internal;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.PolicyOpinions;
import dev.axonia.access.KnownPermissions;
import dev.axonia.access.Permission;
import dev.axonia.access.PermissionCatalogue;
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
