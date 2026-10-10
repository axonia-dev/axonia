package dev.axonia.community.application;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.axonia.community.api.spi.CommunityCountPolicy;

import static org.assertj.core.api.Assertions.assertThat;

class CommunityCountRuleTest {

    @Test
    void withNoPolicyAnOrganizationStartsCommunitiesFreely() {

        assertThat(new CommunityCountRule(List.of()).countFixedAtFounding("acme")).isFalse();
    }

    @Test
    void aPolicyWithNothingToSayLeavesTheEnterpriseAnswer() {

        CommunityCountPolicy silent = organizationId -> Optional.empty();

        assertThat(new CommunityCountRule(List.of(silent)).countFixedAtFounding("acme")).isFalse();
    }

    @Test
    void aPolicyThatAnswersForTheOrganizationDecides() {

        CommunityCountPolicy fixed = organizationId -> Optional.of(true);

        assertThat(new CommunityCountRule(List.of(fixed)).countFixedAtFounding("rust-hub")).isTrue();
    }
}
