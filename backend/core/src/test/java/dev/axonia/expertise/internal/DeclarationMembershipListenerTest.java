package dev.axonia.expertise.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.community.CommunityMemberLeft;

import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class DeclarationMembershipListenerTest {

    @Mock
    private DeclarationRepository declarationRepository;
    @Mock
    private VocabularyLock vocabularyLock;

    @InjectMocks
    private DeclarationMembershipListener sut;

    @Test
    void aDepartureSharesTheCommunityLockThenLocksTheDeclarerThenDeletesTheirDeclarations() {

        sut.on(new CommunityMemberLeft("acme", 7L, "alice"));

        var order = inOrder(vocabularyLock, declarationRepository);
        order.verify(vocabularyLock).share("acme", 7L);
        order.verify(declarationRepository).lockDeclarer("acme", 7L, "alice");
        order.verify(declarationRepository).deleteAllIn("acme", 7L, "alice");
    }
}
