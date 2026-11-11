package dev.axonia.selfhosted.adapter.out.access;

import org.junit.jupiter.api.Test;

import dev.axonia.selfhosted.configuration.SelfHostedPropertiesMother;

import static org.assertj.core.api.Assertions.assertThat;

class ConfiguredOwnerGroupAnchorTest {

    @Test
    void theInstallationsOrganizationIsOwnedThroughItsConfiguredGroup() {

        // given
        var sut = new ConfiguredOwnerGroupAnchor(SelfHostedPropertiesMother.any());

        // when
        var actual = sut.anchoredOutsideTheProduct("acme");

        // then
        assertThat(actual).contains(true);
    }

    @Test
    void anotherOrganizationIsNotThisInstallationsToAnswerFor() {

        // given
        var sut = new ConfiguredOwnerGroupAnchor(SelfHostedPropertiesMother.any());

        // when
        var actual = sut.anchoredOutsideTheProduct("globex");

        // then
        assertThat(actual).isEmpty();
    }
}
