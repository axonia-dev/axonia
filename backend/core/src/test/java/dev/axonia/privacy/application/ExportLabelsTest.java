package dev.axonia.privacy.application;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.axonia.privacy.api.spi.ExportLabelPolicy;

import static org.assertj.core.api.Assertions.assertThat;

class ExportLabelsTest {

    @Test
    void withNoPolicyAnOrganizationHasNoLabelAheadOfItsName() {

        assertThat(new ExportLabels(List.of()).of("acme")).isEmpty();
    }

    @Test
    void aPolicyWithNothingToSayLeavesNoLabel() {

        ExportLabelPolicy silent = organizationId -> Optional.empty();

        assertThat(new ExportLabels(List.of(silent)).of("acme")).isEmpty();
    }

    @Test
    void aPolicyThatAnswersForTheOrganizationLabelsIt() {

        ExportLabelPolicy vanity = organizationId -> Optional.of("rustaceans");

        assertThat(new ExportLabels(List.of(vanity)).of("rust-hub")).contains("rustaceans");
    }
}
