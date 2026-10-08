package dev.axonia.roundup.internal;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.axonia.roundup.RoundupAvailabilityPolicy;

import static org.assertj.core.api.Assertions.assertThat;

class RoundupAvailabilityTest {

    @Test
    void withNoPolicyTheRoundupIsAvailable() {

        assertThat(new RoundupAvailability(List.of()).in("acme")).isTrue();
    }

    @Test
    void aPolicyWithNothingToSayLeavesTheEnterpriseAnswer() {

        RoundupAvailabilityPolicy silent = organizationId -> Optional.empty();

        assertThat(new RoundupAvailability(List.of(silent)).in("acme")).isTrue();
    }

    @Test
    void aPolicyThatAnswersForTheOrganizationDecides() {

        RoundupAvailabilityPolicy noDirectory = organizationId -> Optional.of(false);

        assertThat(new RoundupAvailability(List.of(noDirectory)).in("rust-hub")).isFalse();
    }
}
