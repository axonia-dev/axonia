package dev.axonia.events.application;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.events.api.Session;
import dev.axonia.events.api.SessionClosureRequest;
import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class SessionClosureServiceTest {

    private static final String RECORDING = "https://videos.example.test/guild-42";

    @Mock
    private SessionService sessionService;

    private SessionClosureService sut;

    @BeforeEach
    void wire() {

        sut = new SessionClosureService(sessionService);
    }

    @Test
    void closesAsHeldThenRecordsTheFollowUp_andReturnsTheDetail() {

        var detail = Session.builder().id(42L).recordingUrl(RECORDING).build();
        when(sessionService.getDetail(42L)).thenReturn(detail);

        var result = sut.closeAsHeld(42L,
                SessionClosureRequest.builder().wrapUp(false).recordingUrl(RECORDING).notes("Notes").build());

        assertThat(result).isSameAs(detail);
        InOrder order = inOrder(sessionService);
        order.verify(sessionService).close(42L, new CloseSessionRequest(true, false));
        order.verify(sessionService).recordFollowUp(42L, new FollowUpRequest("Notes", RECORDING, List.of()));
        order.verify(sessionService).getDetail(42L);
    }

    @Test
    void carriesAnExplicitWrapUp() {

        sut.closeAsHeld(42L, SessionClosureRequest.builder().wrapUp(true).build());

        verify(sessionService).close(42L, new CloseSessionRequest(true, true));
    }

    /** The follow-up is what marks the slots' topics done, so a close with nothing to record still runs it. */
    @Test
    void withNothingToRecord_stillRecordsAnEmptyFollowUp() {

        sut.closeAsHeld(42L, SessionClosureRequest.builder().recordingUrl(" ").notes("").build());

        verify(sessionService).recordFollowUp(42L, new FollowUpRequest(null, null, List.of()));
    }

    @Test
    void stripsWhatItRecords() {

        sut.closeAsHeld(42L,
                SessionClosureRequest.builder().recordingUrl(" " + RECORDING + " ").notes(" Notes ").build());

        verify(sessionService).recordFollowUp(42L, new FollowUpRequest("Notes", RECORDING, List.of()));
    }

    @Test
    void aRecordingThatIsNotAnHttpLink_isRefusedBeforeAnythingChanges() {

        assertThatThrownBy(
                () -> sut.closeAsHeld(42L, SessionClosureRequest.builder().recordingUrl("javascript:alert(1)").build()))
                .isInstanceOf(ValidationException.class).extracting("code").isEqualTo("INVALID_RECORDING_URL");
        verifyNoInteractions(sessionService);
    }

    @Test
    void aRecordingLongerThanItsColumn_isRefusedBeforeAnythingChanges() {

        var tooLong = "https://v.example/" + "a".repeat(SessionClosureService.MAX_RECORDING_URL_LENGTH);

        assertThatThrownBy(() -> sut.closeAsHeld(42L, SessionClosureRequest.builder().recordingUrl(tooLong).build()))
                .isInstanceOf(ValidationException.class).extracting("code").isEqualTo("INVALID_RECORDING_URL");
        verifyNoInteractions(sessionService);
    }

    @Test
    void aRecordingExactlyAsLongAsItsColumn_isRecorded() {

        var prefix = "https://v.example/";
        var longest = prefix + "a".repeat(SessionClosureService.MAX_RECORDING_URL_LENGTH - prefix.length());

        sut.closeAsHeld(42L, SessionClosureRequest.builder().recordingUrl(longest).build());

        verify(sessionService).recordFollowUp(42L, new FollowUpRequest(null, longest, List.of()));
    }
}
