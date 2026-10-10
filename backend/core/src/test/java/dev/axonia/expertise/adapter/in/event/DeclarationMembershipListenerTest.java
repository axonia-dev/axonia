package dev.axonia.expertise.adapter.in.event;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.community.api.CommunityMemberLeft;
import dev.axonia.expertise.application.ExpertiseDeclarationService;

import static org.mockito.Mockito.verify;

@MockitoSettings
class DeclarationMembershipListenerTest {

    @Mock
    private ExpertiseDeclarationService declarations;

    @InjectMocks
    private DeclarationMembershipListener sut;

    @Test
    void aDepartureForgetsTheMembersDeclarationsInTheEventsOrganization() {

        sut.on(new CommunityMemberLeft("acme", 7L, "alice"));

        verify(declarations).forgetDepartedDeclarer("acme", 7L, "alice");
    }
}
