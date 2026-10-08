package dev.axonia.roundup.internal;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.axonia.communication.MessageType;
import dev.axonia.identity.UserDirectory;
import dev.axonia.roundup.RoundupSettingsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class RoundupSubjectPolicyTest {

    private final RoundupSettingsService settings = mock(RoundupSettingsService.class);
    private final UserDirectory directory = mock(UserDirectory.class);
    private final RoundupSubjectPolicy policy = new RoundupSubjectPolicy(settings, directory);

    @Test
    void handlesOnlyTheRoundup() {

        assertThat(policy.handles()).containsExactly(MessageType.ORGANIZATION_ROUNDUP);
    }

    @Test
    void layerTwoAsksWhetherTheOrganizationsRoundupIsStillOn() {

        given(settings.enabledFor("acme")).willReturn(true);
        given(settings.enabledFor("globex")).willReturn(false);

        assertThat(policy.isEnabledFor("acme", MessageType.ORGANIZATION_ROUNDUP, "2026-Q2")).isTrue();
        assertThat(policy.isEnabledFor("globex", MessageType.ORGANIZATION_ROUNDUP, "2026-Q2")).isFalse();
    }

    @Test
    void layerFourAsksWhetherTheRecipientIsStillInTheDirectory() {

        given(directory.presentOf("acme", List.of("present-user"))).willReturn(Set.of("present-user"));
        given(directory.presentOf("acme", List.of("departed-user"))).willReturn(Set.of());

        assertThat(policy.isStillAMember("acme", "2026-Q2", "present-user")).isTrue();
        assertThat(policy.isStillAMember("acme", "2026-Q2", "departed-user")).isFalse();
    }
}
