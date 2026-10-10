package dev.axonia.community.application;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.communication.api.ChatFailureKind;
import dev.axonia.communication.api.spi.ChatDestinationsPort.ChatFailure;
import dev.axonia.community.application.port.out.ChatDestinationStore;
import dev.axonia.community.domain.ChatDestinationRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The break policy of a chat destination, tested directly rather than only through {@code ChatDestinationService}'s
 * delegating methods: this is the one place that actually decides whether a destination breaks.
 */
@MockitoSettings
class ChatHealthRecorderTest {

    private static final String ORG = "org-1";
    private static final long DESTINATION_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-08-07T12:00:00Z");

    @Mock
    private ChatDestinationStore destinations;

    @InjectMocks
    private ChatHealthRecorder recorder;

    private ChatDestinationRecord healthy() {

        return ChatDestinationRecord.builder().id(DESTINATION_ID).organizationId(ORG).communityId(1L)
                .name("Team channel").webhookUrl("https://hooks.slack.com/services/x").build();
    }

    @Test
    void aGoneFailureBreaksTheDestinationOnTheFirstStrike() {

        when(destinations.find(ORG, DESTINATION_ID)).thenReturn(Optional.of(healthy()));
        when(destinations.update(any())).thenAnswer(call -> call.getArgument(0));

        recorder.recordFailure(ORG, DESTINATION_ID, new ChatFailure(ChatFailureKind.GONE, "410 Gone", NOW), 3);

        var saved = ArgumentCaptor.forClass(ChatDestinationRecord.class);
        verify(destinations).update(saved.capture());
        assertThat(saved.getValue().isBroken()).isTrue();
        assertThat(saved.getValue().getConsecutiveFailures()).isEqualTo(1);
        assertThat(saved.getValue().getLastFailureAt()).isEqualTo(NOW);
        assertThat(saved.getValue().getLastError()).isEqualTo("410 Gone");
    }

    @Test
    void ordinaryWeatherOnlyBreaksItOnTheThirdConsecutiveFailure() {

        when(destinations.find(ORG, DESTINATION_ID))
                .thenReturn(Optional.of(healthy().toBuilder().consecutiveFailures(1).build()));
        when(destinations.update(any())).thenAnswer(call -> call.getArgument(0));

        recorder.recordFailure(ORG, DESTINATION_ID, new ChatFailure(ChatFailureKind.TRANSIENT, "503", NOW), 3);

        var saved = ArgumentCaptor.forClass(ChatDestinationRecord.class);
        verify(destinations).update(saved.capture());
        assertThat(saved.getValue().isBroken()).isFalse();
        assertThat(saved.getValue().getConsecutiveFailures()).isEqualTo(2);
    }

    @Test
    void ordinaryWeatherBreaksItOnceFailuresReachTheThreshold() {

        when(destinations.find(ORG, DESTINATION_ID))
                .thenReturn(Optional.of(healthy().toBuilder().consecutiveFailures(2).build()));
        when(destinations.update(any())).thenAnswer(call -> call.getArgument(0));

        recorder.recordFailure(ORG, DESTINATION_ID, new ChatFailure(ChatFailureKind.TRANSIENT, "503", NOW), 3);

        var saved = ArgumentCaptor.forClass(ChatDestinationRecord.class);
        verify(destinations).update(saved.capture());
        assertThat(saved.getValue().isBroken()).isTrue();
        assertThat(saved.getValue().getConsecutiveFailures()).isEqualTo(3);
    }

    @Test
    void aDeliveryResetsTheStreakAndClearsTheBrokenState() {

        when(destinations.find(ORG, DESTINATION_ID)).thenReturn(
                Optional.of(healthy().toBuilder().broken(true).consecutiveFailures(3).lastError("410").build()));
        when(destinations.update(any())).thenAnswer(call -> call.getArgument(0));

        recorder.recordDelivered(ORG, DESTINATION_ID, NOW);

        var saved = ArgumentCaptor.forClass(ChatDestinationRecord.class);
        verify(destinations).update(saved.capture());
        assertThat(saved.getValue().isBroken()).isFalse();
        assertThat(saved.getValue().getConsecutiveFailures()).isZero();
        assertThat(saved.getValue().getLastError()).isNull();
        assertThat(saved.getValue().getLastPostedAt()).isEqualTo(NOW);
    }

    @Test
    void aWriteForADestinationThatIsGoneIsSilentlyDropped() {

        when(destinations.find(ORG, DESTINATION_ID)).thenReturn(Optional.empty());

        recorder.recordDelivered(ORG, DESTINATION_ID, NOW);

        verify(destinations, never()).update(any());
    }

    @Test
    void aFailureForADestinationThatIsGoneIsSilentlyDropped() {

        when(destinations.find(ORG, DESTINATION_ID)).thenReturn(Optional.empty());

        recorder.recordFailure(ORG, DESTINATION_ID, new ChatFailure(ChatFailureKind.TRANSIENT, "503", NOW), 3);

        verify(destinations, never()).update(any());
    }
}
