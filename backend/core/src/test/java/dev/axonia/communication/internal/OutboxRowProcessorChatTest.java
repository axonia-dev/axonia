package dev.axonia.communication.internal;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.PublicUrlProperties;
import dev.axonia.communication.ChatChannelStatus;
import dev.axonia.communication.ChatDestinationsPort;
import dev.axonia.communication.ChatFailureKind;
import dev.axonia.communication.MessageType;
import dev.axonia.communication.RecipientDirectory;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The chat half of {@link OutboxRowProcessor#process}: a row whose {@code channel} is {@code CHAT} takes an entirely
 * different path than {@link OutboxRowProcessorTest} covers - it never resolves an address, never touches
 * {@link MailBatch}, and, on a successful post, reports which destination it reached only through {@code process}'s own
 * return value rather than by calling {@link ChatDestinationsPort} itself.
 *
 * <p>
 * Two distinct row shapes exercise two distinct methods. A placeholder - {@link #placeholder}, no
 * {@code chatDestinationId} - drives {@link OutboxRowProcessor#expand}:
 * {@link #aPlaceholderExpandsAndRetiresWithoutPosting} is the one test that matters most for that half, since a
 * placeholder that posted instead of expanding would send one copy of a message to whichever destination the resolution
 * happened to return first, silently dropping every other one. An expanded row - {@link #expanded},
 * {@code chatDestinationId} {@value #DESTINATION_ID} - drives {@link OutboxRowProcessor#sendToDestination}, and posts
 * to that destination alone.
 *
 * <p>
 * Neither health write is made from this class, and for the identical reason:
 * {@link ChatDestinationsPort#recordFailure} and {@link ChatDestinationsPort#recordDelivered} are both ultimately
 * {@code @Transactional} methods reached through a real cross-bean proxy, so calling either from inside
 * {@link OutboxRowProcessor#process}'s own transaction would make it PARTICIPATE in that transaction rather than run
 * independently of it - a caught exception one frame up does not stop Spring from marking the whole thing rollback-only
 * regardless (see {@code OutboxRowProcessor}'s own class javadoc and {@code DrainWorkers.recordChatDelivered}'s javadoc
 * for the full mechanism). {@link #aFailedPostPropagatesAndNeverRecordsTheFailureItself} pins the failure half of that;
 * {@link #aSuccessfulPostReturnsTheDestinationIdItReachedRatherThanRecordingItItself} pins the delivered half - both
 * write from {@link DrainWorkersTest} instead, after this class's own transaction has committed.
 */
@ExtendWith(MockitoExtension.class)
class OutboxRowProcessorChatTest {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");
    private static final String ORG = "org-1";
    private static final String SUBJECT = "41";
    private static final long ROW_ID = 1L;
    private static final long OTHER_ROW_ID = 2L;
    private static final long DESTINATION_ID = 7L;
    private static final long OTHER_DESTINATION_ID = 9L;
    private static final String URL = "https://hooks.slack.com/services/x";
    private static final String OTHER_URL = "https://hooks.slack.com/services/y";

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

    private OutboxRowProcessor sut;

    @BeforeEach
    void setUp() {

        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        sut = new OutboxRowProcessor(outbox, gate, renderer, recipientDirectory, clock, JsonMapper.builder().build(),
                unsubscribeTokens, new PublicUrlProperties("https://axonia.test"), chatDestinations, chatTransport);
    }

    private static OutboxEntity placeholder() {

        return OutboxEntity.builder().id(ROW_ID).organizationId(ORG).messageType(MessageType.SESSION_UPCOMING.code())
                .subjectRef(SUBJECT).channel("CHAT").facts("{}").status("PENDING").attempts(0).build();
    }

    private static OutboxEntity expanded() {

        return expandedTo(DESTINATION_ID);
    }

    private static OutboxEntity expandedTo(long destinationId) {

        return OutboxEntity.builder().id(ROW_ID).organizationId(ORG).messageType(MessageType.SESSION_UPCOMING.code())
                .subjectRef(SUBJECT).channel("CHAT").chatDestinationId(destinationId).facts("{}").status("PENDING")
                .attempts(0).build();
    }

    @Test
    void aPlaceholderExpandsAndRetiresWithoutPosting() {

        var placeholder = placeholder();
        when(chatDestinations.destinationsFor(ORG, SUBJECT)).thenReturn(List.of(7L, 9L));

        var delivered = sut.process(placeholder, batch);

        verify(outbox).expandChatRow(placeholder, List.of(7L, 9L), NOW);
        verify(outbox).markSuppressed(ROW_ID, ORG, SuppressionReason.EXPANDED.name(), NOW);
        verifyNoInteractions(chatTransport);
        assertThat(delivered).isEmpty();
    }

    /**
     * {@code gate.admit} admits a placeholder the moment it sees ANY destination, but the two reads do not share a
     * snapshot: under read-committed isolation, the community's last destination can be removed in the window between
     * the gate's read and this method's own re-read. A row that retired {@code EXPANDED} having written no children
     * would read as a message that fanned out while it actually reached nobody - the exact failure "why did nothing
     * post to #eng-leads" exists to catch. The correct record for that race is {@code NO_DESTINATION}, the same reason
     * a placeholder with no destination gets at the gate.
     */
    @Test
    void aPlaceholderWhoseDestinationsVanishedBetweenAdmissionAndExpansionIsSuppressedAsNoDestination() {

        var placeholder = placeholder();
        when(chatDestinations.destinationsFor(ORG, SUBJECT)).thenReturn(List.of());

        var delivered = sut.process(placeholder, batch);

        verify(outbox, never()).expandChatRow(any(), any(), any());
        verify(outbox).markSuppressed(ROW_ID, ORG, SuppressionReason.NO_DESTINATION.name(), NOW);
        verifyNoInteractions(chatTransport);
        assertThat(delivered).isEmpty();
    }

    /**
     * {@code process}'s return value, not a call to {@link ChatDestinationsPort#recordDelivered} made from in here, is
     * how a successful post is reported: see this class's own javadoc for why that write cannot be made from inside
     * this method's transaction. {@link DrainWorkersTest#aReturnedDestinationIdRecordsDeliveryOnThisMethodsOwnStack}
     * pins what the caller does with it.
     */
    @Test
    void aSuccessfulPostReturnsTheDestinationIdItReachedRatherThanRecordingItItself() {

        var expanded = expanded();
        when(chatDestinations.statusFor(ORG, DESTINATION_ID)).thenReturn(new ChatChannelStatus.Ready(URI.create(URL)));

        var delivered = sut.process(expanded, batch);

        var order = inOrder(outbox, chatTransport);
        order.verify(outbox).markSent(ROW_ID, ORG, NOW);
        order.verify(chatTransport).post(eq(URI.create(URL)), eq(MessageType.SESSION_UPCOMING), anyMap());
        assertThat(delivered).contains(DESTINATION_ID);
        verify(chatDestinations, never()).recordDelivered(any(), anyLong(), any());
    }

    @Test
    void aChatRowNeverResolvesAnAddressAndNeverMintsAnUnsubscribeToken() {

        when(chatDestinations.statusFor(ORG, DESTINATION_ID)).thenReturn(new ChatChannelStatus.Ready(URI.create(URL)));

        sut.process(expanded(), batch);

        verifyNoInteractions(recipientDirectory, unsubscribeTokens);
    }

    @Test
    void aChatRowNeverTouchesTheMailBatch() {

        when(chatDestinations.statusFor(ORG, DESTINATION_ID)).thenReturn(new ChatChannelStatus.Ready(URI.create(URL)));

        sut.process(expanded(), batch);

        verifyNoInteractions(batch);
    }

    /**
     * {@link ChatDestinationsPort#recordFailure} must not be called from here - see this class's own javadoc and
     * {@link DrainWorkersTest} for where the equivalent assertion for the FAILURE write actually lives.
     */
    @Test
    void aFailedPostPropagatesAndNeverRecordsTheFailureItself() {

        when(chatDestinations.statusFor(ORG, DESTINATION_ID)).thenReturn(new ChatChannelStatus.Ready(URI.create(URL)));
        doThrow(new ChatSendException(ChatFailureKind.TRANSIENT, "503")).when(chatTransport).post(any(), any(),
                anyMap());

        assertThatThrownBy(() -> sut.process(expanded(), batch)).isInstanceOf(ChatSendException.class);

        verify(chatDestinations, never()).recordFailure(any(), anyLong(), any());
    }

    /**
     * The status is re-read at send time rather than carried from the gate (see the production code's own javadoc), so
     * a destination that was {@code Ready} when the gate admitted the row but is no longer by the time this method runs
     * - disconnected in between - is still handled rather than posting to a URL nobody vouches for any more.
     */
    @Test
    void aDestinationThatIsNoLongerReadyAtSendTimeIsSuppressedRatherThanPostedTo() {

        when(chatDestinations.statusFor(ORG, DESTINATION_ID)).thenReturn(new ChatChannelStatus.NotConfigured());

        var delivered = sut.process(expanded(), batch);

        verify(outbox).markSuppressed(ROW_ID, ORG, "NO_DESTINATION", NOW);
        verifyNoInteractions(chatTransport);
        assertThat(delivered).isEmpty();
    }

    /**
     * The window: two chat rows for the same community in one claimed batch, one worker breaks the destination between
     * the gate's read and this method's own re-read. Before {@link ChatChannelSuppression} was shared with
     * {@link DeliveryGate#admitChat}, this method collapsed every non-{@code Ready} status to {@code NO_DESTINATION},
     * which would have told an operator to add a destination that is configured and broken.
     */
    @Test
    void aDestinationThatBrokeBetweenAdmissionAndSendIsSuppressedAsBrokenNotNoDestination() {

        when(chatDestinations.statusFor(ORG, DESTINATION_ID)).thenReturn(new ChatChannelStatus.Broken());

        var delivered = sut.process(expanded(), batch);

        verify(outbox).markSuppressed(ROW_ID, ORG, "DESTINATION_BROKEN", NOW);
        verifyNoInteractions(chatTransport);
        assertThat(delivered).isEmpty();
    }

    /**
     * The destinations' independence, pinned by actually processing two rows of the same message rather than by an
     * assertion no code path could fail: destination {@value #DESTINATION_ID} and destination
     * {@value #OTHER_DESTINATION_ID} each get their own {@code Ready} status at their own URL, so
     * {@link ChatTransport#post} can fail one without the mock being unable to tell them apart. One revoked webhook
     * exhausts its own attempts and rolls back its own row; the destination beside it is a wholly separate
     * {@link #process} call, on a row {@code outbox} and {@code chatDestinations} have not touched yet, and it still
     * reaches {@code markSent}, the transport, and returns its own destination id for the caller to record.
     */
    @Test
    void oneDestinationFailingTerminallyLeavesTheOtherDestinationOfTheSameMessageDelivered() {

        var failing = expandedTo(DESTINATION_ID);
        var surviving = OutboxEntity.builder().id(OTHER_ROW_ID).organizationId(ORG)
                .messageType(MessageType.SESSION_UPCOMING.code()).subjectRef(SUBJECT).channel("CHAT")
                .chatDestinationId(OTHER_DESTINATION_ID).facts("{}").status("PENDING").attempts(0).build();
        when(chatDestinations.statusFor(ORG, DESTINATION_ID)).thenReturn(new ChatChannelStatus.Ready(URI.create(URL)));
        when(chatDestinations.statusFor(ORG, OTHER_DESTINATION_ID))
                .thenReturn(new ChatChannelStatus.Ready(URI.create(OTHER_URL)));
        doThrow(new ChatSendException(ChatFailureKind.GONE, "410 Gone")).when(chatTransport).post(eq(URI.create(URL)),
                any(), any());

        assertThatThrownBy(() -> sut.process(failing, batch)).isInstanceOf(ChatSendException.class);
        var delivered = sut.process(surviving, batch);

        verify(outbox).markSent(OTHER_ROW_ID, ORG, NOW);
        verify(chatTransport).post(eq(URI.create(OTHER_URL)), eq(MessageType.SESSION_UPCOMING), anyMap());
        assertThat(delivered).contains(OTHER_DESTINATION_ID);
    }

    /**
     * {@link DeliveryGate#admit} already suppresses an unrecognized type before a row can ever reach this method (see
     * {@link OutboxRowProcessorTest}'s own backstop test for the mail path), so a real deploy cannot hit this - {@code
     * gate} is mocked here to admit anyway, exercising the same backstop on the chat path.
     */
    @Test
    void aChatRowWithAnUnrecognizedMessageTypeFailsLoudlyInsteadOfPostingAnUnrenderableMessage() {

        var row = OutboxEntity.builder().id(ROW_ID).organizationId(ORG).messageType("Not-A-Type").subjectRef(SUBJECT)
                .channel("CHAT").chatDestinationId(DESTINATION_ID).facts("{}").status("PENDING").attempts(0).build();
        when(chatDestinations.statusFor(ORG, DESTINATION_ID)).thenReturn(new ChatChannelStatus.Ready(URI.create(URL)));

        assertThatThrownBy(() -> sut.process(row, batch)).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(chatTransport);
    }
}
