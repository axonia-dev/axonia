package dev.axonia.agent.application;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.shared.api.paid.PaidAction;
import dev.axonia.shared.api.paid.PaidCapability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class OrganizationAgentAccessTest {

    @Mock
    PaidCapability paidCapability;

    private OrganizationAgentAccess sut() {
        return new OrganizationAgentAccess(paidCapability);
    }

    @Test
    void isAvailableToDelegatesAGrant() {

        when(paidCapability.isOpenTo("acme", "alice")).thenReturn(true);

        assertThat(sut().isAvailableTo("acme", "alice")).isTrue();
    }

    @Test
    void isAvailableToDelegatesARefusal() {

        when(paidCapability.isOpenTo("guild", "alice")).thenReturn(false);

        assertThat(sut().isAvailableTo("guild", "alice")).isFalse();
    }

    @Test
    void isAvailableToCallerDelegatesAGrant() {

        when(paidCapability.isOpenToCaller()).thenReturn(true);

        assertThat(sut().isAvailableToCaller()).isTrue();
    }

    @Test
    void isAvailableToCallerDelegatesARefusal() {

        when(paidCapability.isOpenToCaller()).thenReturn(false);

        assertThat(sut().isAvailableToCaller()).isFalse();
    }

    @Test
    void refusalIsThePaidCapabilitysWordsForConnectingAnAgent() {

        // given
        when(paidCapability.refusal(PaidAction.CONNECT_AGENT)).thenReturn("Not open.");

        // when / then
        assertThat(sut().refusal()).isEqualTo("Not open.");
    }
}
