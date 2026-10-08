package dev.axonia.privacy.internal;

import org.springframework.stereotype.Component;

import dev.axonia.ExportScope;
import dev.axonia.organization.Organization;
import dev.axonia.organization.OrganizationService;
import lombok.RequiredArgsConstructor;

/**
 * Gathers what {@link ExportPackageName} needs for a scope into one {@link ExportPackageNaming}, so the {@code .zip}
 * download and the archive's root folder are named from the same reads and always agree.
 */
@Component
@RequiredArgsConstructor
public class ExportPackageNames {

    private final OrganizationService organizations;
    private final ExportLabels labels;

    public ExportPackageNaming of(ExportScope scope) {

        var organization = scope.organization();
        return new ExportPackageNaming(scope, organization.flatMap(labels::of),
                organization.flatMap(organizations::organization).map(Organization::name));
    }
}
