package dev.axonia.privacy.application;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.axonia.privacy.api.spi.MemberDataRightsPolicy;

import static org.assertj.core.api.Assertions.assertThat;

class MemberDataRightsTest {

    @Test
    void withNoPolicyTheOrganizationExercisesThem() {

        assertThat(new MemberDataRights(List.of()).exercisedByMembers("acme")).isFalse();
    }

    @Test
    void aPolicyWithNothingToSayLeavesTheEnterpriseAnswer() {

        MemberDataRightsPolicy silent = organizationId -> Optional.empty();

        assertThat(new MemberDataRights(List.of(silent)).exercisedByMembers("acme")).isFalse();
    }

    @Test
    void aPolicyThatAnswersForTheOrganizationDecides() {

        MemberDataRightsPolicy selfServe = organizationId -> Optional.of(true);

        assertThat(new MemberDataRights(List.of(selfServe)).exercisedByMembers("rust-hub")).isTrue();
    }
}
