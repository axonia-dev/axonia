package dev.axonia.community.application;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.axonia.community.application.port.out.CommunityStore;
import dev.axonia.community.application.port.out.MembershipLedger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MentoringMembershipServiceTest {

    private final MembershipLedger ledger = mock(MembershipLedger.class);
    private final CommunityStore communityStore = mock(CommunityStore.class);
    private final MentoringMembershipService sut = new MentoringMembershipService(ledger, communityStore);

    @Test
    void inAMentoringCommunityAsksTheLedger() {

        // given
        when(ledger.inAMentoringCommunity("acme", "alice")).thenReturn(true);

        // when
        var actual = sut.inAMentoringCommunity("acme", "alice");

        // then
        assertThat(actual).isTrue();
    }

    @Test
    void theMentoringCommunitiesAreTheStoresAnswer() {

        // given
        when(communityStore.mentoringCommunityIds("acme")).thenReturn(List.of(7L));

        // when
        var actual = sut.mentoringCommunityIds("acme");

        // then
        assertThat(actual).containsExactly(7L);
    }
}
