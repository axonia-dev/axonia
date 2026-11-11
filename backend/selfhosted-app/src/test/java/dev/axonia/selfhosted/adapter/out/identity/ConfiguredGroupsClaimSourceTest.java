package dev.axonia.selfhosted.adapter.out.identity;

import org.junit.jupiter.api.Test;

import dev.axonia.selfhosted.configuration.SelfHostedPropertiesMother;

import static org.assertj.core.api.Assertions.assertThat;

class ConfiguredGroupsClaimSourceTest {

    @Test
    void theGroupsClaimListsTheDirectoryGroups() {

        // given
        var sut = new ConfiguredGroupsClaimSource(SelfHostedPropertiesMother.groupsReadFrom("groups"));

        // when
        var actual = sut.readsAnotherClaim("acme");

        // then
        assertThat(actual).isFalse();
    }

    @Test
    void appRolesReadAsGroupsAreAnotherClaim() {

        // given
        var sut = new ConfiguredGroupsClaimSource(SelfHostedPropertiesMother.groupsReadFrom("roles"));

        // when
        var actual = sut.readsAnotherClaim("acme");

        // then
        assertThat(actual).isTrue();
    }

    @Test
    void anotherOrganizationIsNotThisInstallationsToAnswerFor() {

        // given
        var sut = new ConfiguredGroupsClaimSource(SelfHostedPropertiesMother.groupsReadFrom("roles"));

        // when
        var actual = sut.readsAnotherClaim("globex");

        // then
        assertThat(actual).isFalse();
    }
}
