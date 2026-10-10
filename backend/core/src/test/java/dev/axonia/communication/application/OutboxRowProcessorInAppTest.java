package dev.axonia.communication.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.communication.adapter.out.chat.ChatTransport;
import dev.axonia.communication.adapter.out.persistence.JdbcOutbox;
import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.api.SuppressionReason;
import dev.axonia.communication.api.spi.ChatDestinationsPort;
import dev.axonia.communication.api.spi.RecipientDirectory;
import dev.axonia.communication.application.port.out.MailBatch;
import dev.axonia.communication.domain.OutboxDelivery;
import dev.axonia.shared.configuration.PublicUrlProperties;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The in-app half of {@link OutboxRowProcessor#process}: an {@code IN_APP} row has no transport at all, so
 * {@code process} is the whole send - it marks the row {@code SENT} directly, resolving no address, composing no mail,
 * and handing nothing to {@link MailBatch}. This is the branch that closes this hazard: before it, an {@code IN_APP}
 * row fell through to the mail path unrecognized and would have been emailed alongside its real {@code EMAIL} sibling.
 */
@ExtendWith(MockitoExtension.class)
class OutboxRowProcessorInAppTest {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");
    private static final String ORG = "acme";
    private static final long ROW_ID = 7L;

    @Mock
    private JdbcOutbox outbox;
    @Mock
    private DeliveryGate gate;
    @Mock
    private MailRenderer renderer;
    @Mock
    private MailBatch batch;
    @Mock
    private RecipientDirectory recipientDirectory;
    @Mock
    private UnsubscribeTokens unsubscribeTokens;
    @Mock
    private ChatDestinationsPort chatDestinations;
    @Mock
    private ChatTransport chatTransport;

    private OutboxRowProcessor processor;

    @BeforeEach
    void setUp() {

        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        processor = new OutboxRowProcessor(outbox, gate, renderer, recipientDirectory, clock,
                JsonMapper.builder().build(), unsubscribeTokens, new PublicUrlProperties("https://axonia.test"),
                chatDestinations, chatTransport);
    }

    private static OutboxDelivery inAppRow() {

        return OutboxDelivery.builder().id(ROW_ID).organizationId(ORG).messageType(MessageType.SESSION_UPCOMING.code())
                .subjectRef("41").userId("user-1").channel("IN_APP").facts("{}").status("PENDING").attempts(0).build();
    }

    @Test
    void anInAppRowIsMarkedSentWithoutResolvingAnAddress() {

        when(gate.admit(any())).thenReturn(Optional.empty());

        processor.process(inAppRow(), batch);

        verify(outbox).markSent(7L, "acme", NOW);
        verifyNoInteractions(recipientDirectory);
        verifyNoInteractions(batch);
    }

    @Test
    void aSuppressedInAppRowIsMarkedSuppressedAndNeverSent() {

        when(gate.admit(any())).thenReturn(Optional.of(SuppressionReason.NOT_A_MEMBER));

        processor.process(inAppRow(), batch);

        verify(outbox).markSuppressed(7L, "acme", "NOT_A_MEMBER", NOW);
        verify(outbox, never()).markSent(anyLong(), anyString(), any());
    }
}
