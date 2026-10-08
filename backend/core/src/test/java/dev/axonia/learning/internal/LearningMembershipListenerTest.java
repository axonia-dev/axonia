package dev.axonia.learning.internal;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.community.CommunityMemberLeft;
import dev.axonia.learning.Vocabulary;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@MockitoSettings
class LearningMembershipListenerTest {

    @Mock
    private Vocabulary vocabulary;
    @Mock
    private LearningIntentRepository repository;

    @InjectMocks
    private LearningMembershipListener sut;

    @Test
    void leavingDeletesTheMembersIntentsThereUnderTheLocksAnAddTakes() {

        sut.on(new CommunityMemberLeft("acme", 7L, "alice"));

        var order = inOrder(vocabulary, repository);
        order.verify(vocabulary).shareLock("acme", 7L);
        order.verify(repository).lockLearner("acme", 7L, "alice");
        order.verify(repository).deleteAllIn("acme", 7L, "alice");
        verifyNoMoreInteractions(vocabulary, repository);
    }
}
