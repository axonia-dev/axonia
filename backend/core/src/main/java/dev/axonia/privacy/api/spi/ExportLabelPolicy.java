package dev.axonia.privacy.api.spi;

import java.util.Optional;

/**
 * The label an Organization's export packages are named by, ahead of its name.
 *
 * <p>
 * The Enterprise answer, no label, is the default: the package is named after the Organization's name. A contributor
 * answers only for the Organizations it labels and returns empty for any other; at most one contributor may answer for
 * an Organization.
 */
public interface ExportLabelPolicy {

    Optional<String> label(String organizationId);
}
