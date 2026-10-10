package dev.axonia.learning.adapter.in.event;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.community.api.CommunityMemberLeft;
import dev.axonia.learning.application.LearningService;

import static org.mockito.Mockito.verify;

@MockitoSettings
class LearningMembershipListenerTest {

    @Mock
    private LearningService learning;

    @InjectMocks
    private LearningMembershipListener sut;

    @Test
    void leavingForgetsTheMembersIntentsInTheEventsOrganization() {

        sut.on(new CommunityMemberLeft("acme", 7L, "alice"));

        verify(learning).forgetDepartedLearner("acme", 7L, "alice");
    }
}
