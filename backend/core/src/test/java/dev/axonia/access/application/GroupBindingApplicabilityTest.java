package dev.axonia.access.application;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.axonia.access.api.spi.DirectoryGroupsPolicy;

import static org.assertj.core.api.Assertions.assertThat;

class GroupBindingApplicabilityTest {

    @Test
    void withNoPolicyGroupsApply() {

        assertThat(new GroupBindingApplicability(List.of()).applyIn("acme")).isTrue();
    }

    @Test
    void aPolicyWithNothingToSayLeavesTheEnterpriseAnswer() {

        DirectoryGroupsPolicy silent = organizationId -> Optional.empty();

        assertThat(new GroupBindingApplicability(List.of(silent)).applyIn("acme")).isTrue();
    }

    @Test
    void aPolicyThatAnswersForTheOrganizationDecides() {

        DirectoryGroupsPolicy noDirectory = organizationId -> Optional.of(false);

        assertThat(new GroupBindingApplicability(List.of(noDirectory)).applyIn("rust-hub")).isFalse();
    }
}
