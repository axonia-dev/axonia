package dev.axonia.mentoring.adapter.in.event;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.community.api.CommunityMemberLeft;
import dev.axonia.mentoring.application.PairingService;

import static org.mockito.Mockito.verify;

@MockitoSettings
class MentoringMembershipListenerTest {

    @Mock
    private PairingService pairingService;

    @InjectMocks
    private MentoringMembershipListener sut;

    @Test
    void leavingEndsTheMembersMentoringInTheEventsOrganization() {

        // when
        sut.on(new CommunityMemberLeft("acme", 7L, "alice"));

        // then
        verify(pairingService).departedFromGuild("acme", 7L, "alice");
    }
}
