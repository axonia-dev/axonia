package dev.axonia.community.application;

import org.junit.jupiter.api.Test;

import dev.axonia.community.application.port.out.MembershipLedger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MentoringMembershipServiceTest {

    @Test
    void inAMentoringCommunityAsksTheLedger() {

        // given
        var ledger = mock(MembershipLedger.class);
        when(ledger.inAMentoringCommunity("acme", "alice")).thenReturn(true);
        var sut = new MentoringMembershipService(ledger);

        // when
        var actual = sut.inAMentoringCommunity("acme", "alice");

        // then
        assertThat(actual).isTrue();
    }
}
