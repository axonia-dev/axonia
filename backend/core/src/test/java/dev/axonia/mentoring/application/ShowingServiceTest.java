package dev.axonia.mentoring.application;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import dev.axonia.mentoring.application.port.out.CurrentUser;
import dev.axonia.mentoring.application.port.out.OrganizationContext;
import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.domain.EndReason;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.Role;
import dev.axonia.mentoring.domain.ShowAnswer;
import dev.axonia.mentoring.domain.Showing;
import dev.axonia.mentoring.domain.Visibility;
import dev.axonia.shared.api.error.ConflictException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShowingServiceTest {

    private static final String ORG = "org-1";
    private static final long PAIRING = 90L;
    private static final String MIA = "mia";
    private static final String NOAH = "noah";

    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private PairingRepository pairings;

    private ShowingService service;

    @BeforeEach
    void setUp() {

        when(organizationContext.organizationId()).thenReturn(ORG);
        when(currentUser.userId()).thenReturn(NOAH);
        service = new ShowingService(new PairingParties(organizationContext, currentUser, pairings), pairings);
    }

    private static Pairing.PairingBuilder pairing(Showing showing) {

        return Pairing.builder().id(PAIRING).communityId(7L).mentorUserId(MIA).menteeUserId(NOAH)
                .startedAt(Instant.parse("2026-11-01T09:00:00Z")).showing(showing);
    }

    private void store(Pairing pairing) {

        when(pairings.findForWrite(ORG, PAIRING)).thenReturn(Optional.of(pairing));
    }

    @Test
    void answerRecordsTheCallersSide() {

        // given
        store(pairing(Showing.ASKING).build());

        // when
        service.answer(PAIRING, ShowAnswer.YES);

        // then
        verify(pairings).saveShowing(ORG, PAIRING, new Showing(Visibility.ASKING, false, true));
    }

    @Test
    void answerRecordsTheMentorsSide() {

        // given
        when(currentUser.userId()).thenReturn(MIA);
        store(pairing(Showing.ASKING).build());

        // when
        service.answer(PAIRING, ShowAnswer.YES);

        // then
        verify(pairings).saveShowing(ORG, PAIRING, new Showing(Visibility.ASKING, true, false));
    }

    @Test
    void secondYesShowsThePairing() {

        // given
        store(pairing(Showing.ASKING.answer(Role.MENTOR, ShowAnswer.YES)).build());

        // when
        service.answer(PAIRING, ShowAnswer.YES);

        // then
        verify(pairings).saveShowing(ORG, PAIRING, Showing.SHOWN);
    }

    @Test
    void answerRefusesAPairingWithAClearedSide() {

        // given
        store(pairing(Showing.ASKING).mentorUserId(null).build());

        // when / then
        assertThatThrownBy(() -> service.answer(PAIRING, ShowAnswer.YES)).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("MENTORING_SHOWING_SETTLED");
        verify(pairings, never()).saveShowing(anyString(), anyLong(), any());
    }

    @Test
    void answerRefusesAPairingWithTheMenteeCleared() {

        // given
        when(currentUser.userId()).thenReturn(MIA);
        store(pairing(Showing.ASKING).menteeUserId(null).build());

        // when / then
        assertThatThrownBy(() -> service.answer(PAIRING, ShowAnswer.NO)).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("MENTORING_SHOWING_SETTLED");
        verify(pairings, never()).saveShowing(anyString(), anyLong(), any());
    }

    @Test
    void answerOnAnEndedPairingIsRefused() {

        // given
        store(pairing(Showing.ASKING).endedAt(Instant.parse("2026-11-10T09:00:00Z")).endReason(EndReason.MENTOR)
                .build());

        // when / then
        assertThatThrownBy(() -> service.answer(PAIRING, ShowAnswer.YES)).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("MENTORING_PAIRING_ENDED");
        verify(pairings, never()).saveShowing(anyString(), anyLong(), any());
    }

    @Test
    void answerOnASettledPairingIsRefused() {

        // given
        store(pairing(Showing.SHOWN).build());

        // when / then
        assertThatThrownBy(() -> service.answer(PAIRING, ShowAnswer.NO)).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("MENTORING_SHOWING_SETTLED");
        verify(pairings, never()).saveShowing(anyString(), anyLong(), any());
    }
}
