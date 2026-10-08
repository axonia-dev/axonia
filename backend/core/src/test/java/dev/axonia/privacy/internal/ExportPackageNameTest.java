package dev.axonia.privacy.internal;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The name reaches a {@code Content-Disposition} header and a ZIP entry path, so what it does with an unexpected value
 * matters more than what it does with a tidy one. It is also the outside of a file that leaves the building, which is
 * why no rung of the preference order is the Organization's identifier.
 */
class ExportPackageNameTest {

    private static final LocalDate DATE = LocalDate.parse("2026-07-29");

    @Test
    void prefersTheVanityAddressOverEveryOtherName() {

        var name = ExportPackageName.of("acme-engineering", "Acme Engineering", DATE);

        assertThat(name).isEqualTo("axonia-export-acme-engineering-2026-07-29");
    }

    @Test
    void fallsBackToTheOrganizationsNameWhenThereIsNoVanityAddress() {

        assertThat(ExportPackageName.of(null, "Acme Engineering", DATE))
                .isEqualTo("axonia-export-Acme-Engineering-2026-07-29");
        assertThat(ExportPackageName.of("  ", "Acme Engineering", DATE))
                .isEqualTo("axonia-export-Acme-Engineering-2026-07-29");
        assertThat(ExportPackageName.of("", "Acme Engineering", DATE))
                .isEqualTo("axonia-export-Acme-Engineering-2026-07-29");
    }

    /** A hub with neither still has to get a root folder, or the archive has none. */
    @Test
    void namesTheOrganizationGenericallyWhenItHasNeither() {

        assertThat(ExportPackageName.of(null, null, DATE)).isEqualTo("axonia-export-organization-2026-07-29");
        assertThat(ExportPackageName.of(" ", "  ", DATE)).isEqualTo("axonia-export-organization-2026-07-29");
    }

    /**
     * A name reduced to nothing usable is no answer at all rather than a row of dashes, so an Organization named only
     * in a non-Latin script still falls through to the generic word.
     */
    @Test
    void aNameThatSurvivesNoReductionFallsThroughRatherThanBecomingDashes() {

        assertThat(ExportPackageName.of(null, "///", DATE)).isEqualTo("axonia-export-organization-2026-07-29");
        assertThat(ExportPackageName.of("///", "Acme Engineering", DATE))
                .isEqualTo("axonia-export-Acme-Engineering-2026-07-29");
    }

    /** A quote or a newline in a header value is a response-splitting primitive, not a filename. */
    @Test
    void reducesAnythingElseToASafeLabel() {

        var name = ExportPackageName.of("ac\"me\r\nX-Injected: 1", null, DATE);

        assertThat(name).isEqualTo("axonia-export-ac-me-X-Injected-1-2026-07-29");
    }
}
