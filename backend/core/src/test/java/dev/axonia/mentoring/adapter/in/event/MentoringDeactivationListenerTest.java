package dev.axonia.mentoring.adapter.in.event;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.identity.api.MemberDeactivated;
import dev.axonia.mentoring.application.PairingService;

import static org.mockito.Mockito.verify;

@MockitoSettings
class MentoringDeactivationListenerTest {

    @Mock
    private PairingService pairingService;

    @InjectMocks
    private MentoringDeactivationListener sut;

    @Test
    void deactivationEndsTheMembersMentoringInTheEventsOrganization() {

        // when
        sut.on(new MemberDeactivated("acme", "alice"));

        // then
        verify(pairingService).deactivated("acme", "alice");
    }
}
