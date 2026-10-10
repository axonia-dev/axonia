package dev.axonia.privacy.domain;

import java.time.LocalDate;
import java.util.Optional;

import dev.axonia.shared.api.personaldata.ExportScope;

/**
 * Everything one scope's package is named from, read once, so the {@code .zip} download and the archive's root folder
 * cannot be named from two different reads.
 */
public record ExportPackageNaming(ExportScope scope, Optional<String> label, Optional<String> organizationName) {

    public String root(LocalDate date) {

        return ExportPackageName.forScope(scope, label, organizationName, date);
    }
}
