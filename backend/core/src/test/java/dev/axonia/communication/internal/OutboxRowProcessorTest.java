package dev.axonia.communication.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailSendException;
import org.thymeleaf.exceptions.TemplateInputException;

import dev.axonia.PublicUrlProperties;
import dev.axonia.communication.ChatDestinationsPort;
import dev.axonia.communication.MessageType;
import dev.axonia.communication.RecipientDirectory;
import dev.axonia.communication.RecipientDirectory.Presence;
import dev.axonia.communication.RecipientDirectory.Recipient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * One row, top to bottom: the gate, address resolution, render, send, and the terminal mark - everything
 * {@link OutboxDrain} used to own before it moved here so it could carry its own transaction (see this class's
 * production-code javadoc). Every exception-shaped test below asserts the throw propagates out of
 * {@link OutboxRowProcessor#process} rather than being swallowed: unlike the old {@code OutboxDrain.processRow}, this
 * method catches nothing - {@link OutboxDrain#processRow} is where a failure is now recorded, on a connection this
 * class's own failure never touched, and {@code OutboxDrainTest} is what proves that split holds a batch together.
 */
@ExtendWith(MockitoExtension.class)
class OutboxRowProcessorTest {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");
    private static final String ORG = "org-1";

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

        lenient().when(renderer.render(any(), any())).thenReturn(new RenderedMail("Subject", "<p>html</p>", "text"));
        lenient().when(unsubscribeTokens.sign(any(), any(), any())).thenReturn("signed-token");
    }

    private void givenAddress(String userId, String email) {

        when(recipientDirectory.recipientsOf(ORG, List.of(userId))).thenReturn(List.of(aRecipient(userId, email)));
    }

    private void givenNoAddress(String userId) {

        when(recipientDirectory.recipientsOf(ORG, List.of(userId))).thenReturn(List.of(aRecipient(userId, null)));
    }

    private void givenErased(String userId) {

        when(recipientDirectory.recipientsOf(ORG, List.of(userId)))
                .thenReturn(List.of(new Recipient(userId, "Deleted member", null, Presence.ERASED, null)));
    }

    private static Recipient aRecipient(String userId, String email) {

        return new Recipient(userId, userId, email, Presence.PRESENT, "Europe/Berlin");
    }

    private static OutboxEntity aPendingRow(long id, String userId) {

        return aPendingRow(id, userId, "{}");
    }

    private static OutboxEntity aPendingRow(long id, String userId, String facts) {

        return OutboxEntity.builder().id(id).organizationId(ORG).messageType("session_wrap_up").subjectRef("42")
                .userId(userId).template("session-wrap-up").facts(facts).status("PENDING").attempts(0).build();
    }

    @Test
    void anAdmittedRowIsRenderedSentAndMarked() {

        var row = aPendingRow(1L, "user-1");
        givenAddress("user-1", "ada@example.com");
        when(gate.admit(row)).thenReturn(Optional.empty());

        processor.process(row, batch);

        verify(batch).send(argThat(m -> "ada@example.com".equals(m.to())));
        verify(outbox).markSent(1L, ORG, NOW);
    }

    /**
     * The recipient's own clock is resolved beside the address it was already being resolved with, and travels into the
     * render. Resolving it anywhere else is how the address a message went to and the hour it stated would come to
     * describe two different people.
     */
    @Test
    void theRecipientsOwnClockReachesTheRender() {

        var row = aPendingRow(1L, "user-1");
        givenAddress("user-1", "ada@example.com");
        when(gate.admit(row)).thenReturn(Optional.empty());

        processor.process(row, batch);

        verify(renderer).render(eq("session-wrap-up"), argThat(f -> "Europe/Berlin".equals(f.get("readerTimeZone"))));
    }

    /** Null rather than a default, so the render lands on the session's own clock instead of a guessed zone. */
    @Test
    void anUnmeasuredRecipientPassesNoClockAtAll() {

        var row = aPendingRow(1L, "user-1");
        when(recipientDirectory.recipientsOf(ORG, List.of("user-1")))
                .thenReturn(List.of(new Recipient("user-1", "Ada", "ada@example.com", Presence.PRESENT, null)));
        when(gate.admit(row)).thenReturn(Optional.empty());

        processor.process(row, batch);

        verify(renderer).render(eq("session-wrap-up"), argThat(f -> f.get("readerTimeZone") == null));
    }

    @Test
    void aSuppressedRowIsNeverRenderedAndNeverSent() {

        var row = aPendingRow(1L, "user-1");
        when(gate.admit(row)).thenReturn(Optional.of(SuppressionReason.MEMBER_OPTED_OUT));

        processor.process(row, batch);

        verify(renderer, never()).render(any(), any());
        verify(batch, never()).send(any());
        verify(outbox).markSuppressed(1L, ORG, "MEMBER_OPTED_OUT", NOW);
    }

    @Test
    void aMemberWithNoAddressIsSuppressedRatherThanRetriedForever() {

        var row = aPendingRow(1L, "user-1");
        givenNoAddress("user-1");
        when(gate.admit(row)).thenReturn(Optional.empty());

        processor.process(row, batch);

        verify(outbox).markSuppressed(1L, ORG, "NO_ADDRESS", NOW);
        verify(renderer, never()).render(any(), any());
        verify(batch, never()).send(any());
    }

    /**
     * The exact scenario a single shared transaction could not survive: a transport failure - or, for real, any
     * {@code DataAccessException} - must roll back cleanly rather than being swallowed here. Nothing in this class
     * marks the row {@code FAILED}: {@link OutboxDrain#processRow} does, on a connection this failure never touched.
     * {@link DataIntegrityViolationException} stands in for a genuine database error rather than
     * {@link MailSendException} specifically, since propagation is identical either way - both are unchecked, and
     * neither is caught here.
     *
     * <p>
     * The {@code markSent} this asserts DID happen is the point rather than an inconvenience: it is written before the
     * send, so a transport failure propagating out of {@code process} is what rolls it back. A mock cannot show that
     * rollback - {@code OutboxDrainTransactionIsolationTest} proves it against a real Postgres transaction instead.
     */
    @Test
    void aTransportFailurePropagatesInsteadOfBeingSwallowedHere() {

        var row = aPendingRow(1L, "user-1");
        givenAddress("user-1", "ada@example.com");
        when(gate.admit(row)).thenReturn(Optional.empty());
        var failure = new MailSendException("nope");
        doThrow(failure).when(batch).send(any());

        assertThatThrownBy(() -> processor.process(row, batch)).isSameAs(failure);

        verify(outbox).markSent(1L, ORG, NOW);
        verify(outbox, never()).markSuppressed(any(), any(), any(), any());
    }

    /**
     * A genuine {@link DataAccessException} subtype, standing in for the database error the per-row boundary targets:
     * propagates exactly like any other {@code RuntimeException} from this method's point of view - the transactional
     * boundary that makes it safe for the REST of a batch lives in {@link OutboxDrain}, not here.
     */
    @Test
    void aDataAccessExceptionPropagatesInsteadOfBeingSwallowedHere() {

        var row = aPendingRow(1L, "user-1");
        givenAddress("user-1", "ada@example.com");
        when(gate.admit(row)).thenReturn(Optional.empty());
        var failure = new DataIntegrityViolationException("simulated database failure");
        doThrow(failure).when(batch).send(any());

        assertThatThrownBy(() -> processor.process(row, batch)).isSameAs(failure);

        verify(outbox, never()).markSuppressed(any(), any(), any(), any());
    }

    /**
     * The duplicate-send this method's ordering exists to prevent, at the only level a unit test can see it: the mail
     * must not leave the process until the row's {@code SENT} mark has been written. Written the other way round - send
     * first, mark second, as this method did before - a mark that throws rolls back a transaction whose mail is already
     * in somebody's inbox, and the next {@link JdbcOutbox#claim} delivers it to them a second time.
     *
     * <p>
     * Verified by mutation: swapping {@code markSent} back after {@code batch.send} in {@code process} turns this test
     * red on the {@code never()} below, and green again when restored.
     */
    @Test
    void aFailureToRecordTheSendNeverLetsTheMailLeave() {

        var row = aPendingRow(1L, "user-1");
        givenAddress("user-1", "ada@example.com");
        when(gate.admit(row)).thenReturn(Optional.empty());
        var failure = new DataIntegrityViolationException("the mark itself could not be written");
        doThrow(failure).when(outbox).markSent(1L, ORG, NOW);

        assertThatThrownBy(() -> processor.process(row, batch)).isSameAs(failure);

        verify(batch, never()).send(any());
    }

    /**
     * {@code MailRenderer#render} throws {@code TemplateInputException}, unchecked, for a template name this deploy
     * does not have - propagates exactly like a transport failure.
     *
     * <p>
     * The {@code verifyNoInteractions} below is also the other half of {@code process}'s ordering, and the reason
     * {@code markSent} sits between the render and the send rather than ahead of both: a render failure is retryable
     * and must leave no mark to roll back at all.
     */
    @Test
    void aMissingTemplatePropagatesInsteadOfBeingSwallowedHere() {

        var row = aPendingRow(1L, "user-1");
        givenAddress("user-1", "ada@example.com");
        when(gate.admit(row)).thenReturn(Optional.empty());
        var failure = new TemplateInputException("no such template");
        when(renderer.render(eq("session-wrap-up"), any())).thenThrow(failure);

        assertThatThrownBy(() -> processor.process(row, batch)).isSameAs(failure);

        verify(batch, never()).send(any());
        verifyNoInteractions(outbox);
    }

    /**
     * {@link RecipientDirectory#recipientsOf} is total by construction: a user id it cannot see under the current RLS
     * scope comes back {@code ERASED} rather than absent, whether that member was genuinely erased or the scope was
     * never entered at all. Collapsing that into {@link SuppressionReason#NO_ADDRESS} would suppress the row forever,
     * silently, the first time the caller's scoping is wrong - propagating instead keeps it retryable and diagnosable.
     */
    @Test
    void anErasedOrInvisibleMemberFailsLoudlyInsteadOfSuppressingForever() {

        var row = aPendingRow(1L, "user-1");
        givenErased("user-1");
        when(gate.admit(row)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> processor.process(row, batch)).isInstanceOf(IllegalStateException.class);

        verify(renderer, never()).render(any(), any());
        verify(batch, never()).send(any());
        verifyNoInteractions(outbox);
    }

    /**
     * The backstop {@link #anErasedOrInvisibleMemberFailsLoudlyInsteadOfSuppressingForever} exercises for {@code
     * Presence.ERASED} has a second, even less likely shape: {@code recipientsOf} answering with an empty list rather
     * than one result per requested id at all - a contract violation of "total by construction" this method still
     * refuses to treat as a silent {@code NO_ADDRESS}, for the identical reasoning.
     */
    @Test
    void aDirectoryResponseWithNoResultAtAllFailsLoudlyInsteadOfSuppressingForever() {

        var row = aPendingRow(1L, "user-1");
        when(recipientDirectory.recipientsOf(ORG, List.of("user-1"))).thenReturn(List.of());
        when(gate.admit(row)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> processor.process(row, batch)).isInstanceOf(IllegalStateException.class);

        verify(renderer, never()).render(any(), any());
        verify(batch, never()).send(any());
        verifyNoInteractions(outbox);
    }

    /**
     * The unsubscribe token is minted here, at send time, not carried through from the facts a sender wrote at fan-out:
     * this is the one place that already knows both the row's {@code user_id} and its {@code message_type}, and the row
     * deliberately carries no address for {@link UnsubscribeTokens} to sign over either. The same token becomes two
     * DIFFERENT URLs: the {@code POST}-only API URL on {@code OutgoingMail} (for {@code SmtpDelivery}'s
     * {@code List-Unsubscribe} header), and a separate frontend page URL under the {@code unsubscribePageUrl} fact (for
     * the footer link a person actually clicks).
     */
    @Test
    void anOutgoingMailAlwaysCarriesAFreshlyMintedUnsubscribeApiUrl() {

        var row = aPendingRow(1L, "user-1");
        givenAddress("user-1", "ada@example.com");
        when(gate.admit(row)).thenReturn(Optional.empty());
        when(unsubscribeTokens.sign("user-1", ORG, MessageType.SESSION_WRAP_UP)).thenReturn("signed-token");

        processor.process(row, batch);

        var expectedApiUrl = "https://axonia.test/public/notifications/unsubscribe?token=signed-token";
        verify(batch).send(argThat(m -> expectedApiUrl.equals(m.unsubscribeUrl())));
    }

    @Test
    void theRenderedFactsAlwaysCarryAFreshlyMintedUnsubscribePageUrl() {

        var row = aPendingRow(1L, "user-1");
        givenAddress("user-1", "ada@example.com");
        when(gate.admit(row)).thenReturn(Optional.empty());
        when(unsubscribeTokens.sign("user-1", ORG, MessageType.SESSION_WRAP_UP)).thenReturn("signed-token");

        processor.process(row, batch);

        var expectedPageUrl = "https://axonia.test/unsubscribe?token=signed-token";
        verify(renderer).render(eq("session-wrap-up"),
                argThat(facts -> expectedPageUrl.equals(facts.get("unsubscribePageUrl"))));
    }

    /**
     * No sender populates this fact yet - this class always overwrites it - but a future sender's stale value must
     * never leak into a real mail either.
     */
    @Test
    void aProducerSuppliedUnsubscribePageUrlInTheFactsIsOverriddenByTheFreshlyMintedOne() {

        var row = aPendingRow(1L, "user-1", "{\"unsubscribePageUrl\":\"https://stale.example/old\"}");
        givenAddress("user-1", "ada@example.com");
        when(gate.admit(row)).thenReturn(Optional.empty());
        when(unsubscribeTokens.sign("user-1", ORG, MessageType.SESSION_WRAP_UP)).thenReturn("signed-token");

        processor.process(row, batch);

        var expectedPageUrl = "https://axonia.test/unsubscribe?token=signed-token";
        verify(renderer).render(eq("session-wrap-up"),
                argThat(facts -> expectedPageUrl.equals(facts.get("unsubscribePageUrl"))));
    }

    /**
     * {@link SessionFacts#of} puts a real {@link Instant} under {@code scheduledAt}, but {@link JdbcOutbox} persists
     * {@code facts} as JSON and this class reads it back through a type-erased {@code Map<String, Object>} - without
     * rehydration, {@code scheduledAt} comes back out as the plain ISO-8601 string it was written as, and every
     * {@code session-*} template's {@code scheduledAt.atZone(...)} throws {@code NoSuchMethodException} on
     * {@code String.atZone}. This reproduces the exact shape a real drain tick sees (a JSON string survived a real
     * round trip through the outbox row), not a hand-built {@code Instant} a unit test could get away with skipping.
     */
    @Test
    void aJsonEncodedScheduledAtIsRehydratedIntoAnInstantBeforeRendering() {

        var row = aPendingRow(1L, "user-1", "{\"scheduledAt\":\"2026-08-03T22:49:00Z\"}");
        givenAddress("user-1", "ada@example.com");
        when(gate.admit(row)).thenReturn(Optional.empty());

        processor.process(row, batch);

        verify(renderer).render(eq("session-wrap-up"),
                argThat(facts -> Instant.parse("2026-08-03T22:49:00Z").equals(facts.get("scheduledAt"))));
    }

    /**
     * The exact same defect class {@link #aJsonEncodedScheduledAtIsRehydratedIntoAnInstantBeforeRendering} guards
     * against, for the second temporal fact {@link SessionFacts} writes: {@code actionPoints[].dueDate} is a real
     * {@link LocalDate} going in, but a plain {@code yyyy-MM-dd} string coming back out of the same type-erased JSON
     * round trip.
     */
    @Test
    void aJsonEncodedActionPointDueDateIsRehydratedIntoALocalDateBeforeRendering() {

        var row = aPendingRow(1L, "user-1",
                "{\"actionPoints\":[{\"title\":\"Write the retro\",\"assignee\":\"Ada\",\"dueDate\":\"2026-08-20\",\"done\":false}]}");
        givenAddress("user-1", "ada@example.com");
        when(gate.admit(row)).thenReturn(Optional.empty());

        processor.process(row, batch);

        verify(renderer).render(eq("session-wrap-up"), argThat(facts -> {
            @SuppressWarnings("unchecked")
            var actionPoints = (List<Map<String, Object>>) facts.get("actionPoints");
            return LocalDate.of(2026, 8, 20).equals(actionPoints.get(0).get("dueDate"));
        }));
    }

    /**
     * An action point with no due date is a real state ({@code ActionPointSource#dueDate()} is nullable, an advisory
     * target date), not an error - {@link FactRehydration}'s repair must leave it absent rather than choke on a missing
     * key.
     */
    @Test
    void anActionPointWithNoDueDateIsLeftAbsentByRehydration() {

        var row = aPendingRow(1L, "user-1", "{\"actionPoints\":[{\"title\":\"No due date yet\",\"done\":false}]}");
        givenAddress("user-1", "ada@example.com");
        when(gate.admit(row)).thenReturn(Optional.empty());

        processor.process(row, batch);

        verify(renderer).render(eq("session-wrap-up"), argThat(facts -> {
            @SuppressWarnings("unchecked")
            var actionPoints = (List<Map<String, Object>>) facts.get("actionPoints");
            return actionPoints.get(0).get("dueDate") == null;
        }));
    }

    /**
     * Pins the failure mode, not just the happy path, of the rehydration {@code send} performs: today {@code
     * scheduledAt} can only ever come back from the JSON round trip as a {@code String} (an ISO-8601 instant) or be
     * absent, but that is a fact about {@link SessionFacts} and the configured {@code JsonMapper} today, not a
     * guarantee this method can see. If either ever changes, {@code scheduledAt} would come back as neither, and this
     * row must fail loudly right here rather than send a mail whose date silently never rendered.
     */
    @Test
    void aScheduledAtThatIsNeitherAStringNorAnInstantFailsLoudlyInsteadOfRenderingIt() {

        var row = aPendingRow(1L, "user-1", "{\"scheduledAt\":1785900000}");
        givenAddress("user-1", "ada@example.com");
        when(gate.admit(row)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> processor.process(row, batch)).isInstanceOf(RuntimeException.class);

        verify(renderer, never()).render(any(), any());
        verify(batch, never()).send(any());
    }

    /**
     * The exact same failure-mode guarantee
     * {@link #aScheduledAtThatIsNeitherAStringNorAnInstantFailsLoudlyInsteadOfRenderingIt} pins for {@code
     * scheduledAt}, for {@code actionPoints[].dueDate}.
     */
    @Test
    void anActionPointDueDateThatIsNeitherAStringNorALocalDateFailsLoudlyInsteadOfRenderingIt() {

        var row = aPendingRow(1L, "user-1", "{\"actionPoints\":[{\"dueDate\":1785900000}]}");
        givenAddress("user-1", "ada@example.com");
        when(gate.admit(row)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> processor.process(row, batch)).isInstanceOf(RuntimeException.class);

        verify(renderer, never()).render(any(), any());
        verify(batch, never()).send(any());
    }

    /**
     * {@link DeliveryGate#admit} already suppresses {@code UNKNOWN_TYPE} before a row ever reaches this method, so a
     * real deploy cannot hit this. The gate is mocked here to admit anyway, exercising the backstop the same way
     * {@link #anErasedOrInvisibleMemberFailsLoudlyInsteadOfSuppressingForever} exercises the identity one: failing
     * loudly, not sending mail nobody could ever unsubscribe from.
     */
    @Test
    void anUnrecognizedMessageTypeFailsLoudlyInsteadOfSendingAnUnlinkableMail() {

        var row = OutboxEntity.builder().id(1L).organizationId(ORG).messageType("Not-A-Type").subjectRef("42")
                .userId("user-1").template("session-wrap-up").facts("{}").status("PENDING").attempts(0).build();
        givenAddress("user-1", "ada@example.com");
        when(gate.admit(row)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> processor.process(row, batch)).isInstanceOf(IllegalStateException.class);

        verify(batch, never()).send(any());
    }
}
