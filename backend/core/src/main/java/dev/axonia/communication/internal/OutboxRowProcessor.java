package dev.axonia.communication.internal;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.PublicUrlProperties;
import dev.axonia.communication.ChatChannelStatus;
import dev.axonia.communication.ChatDestinationsPort;
import dev.axonia.communication.MessageType;
import dev.axonia.communication.RecipientDirectory;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * One outbox row, one transaction: the gate, address resolution, the render, whichever mark the outcome calls for, and
 * the send all happen inside {@link #process}'s single {@code @Transactional} boundary, in that order. A distinct
 * Spring bean, never a private method on {@link OutboxDrain}, for the identical reason {@link SweepUnit}'s own javadoc
 * gives for being its own bean: {@code @Transactional} only ever works through a real proxy invocation, and
 * {@code OutboxDrain} calling a method on itself - {@code this.process(row)} - would bypass that proxy entirely and
 * make the annotation silently do nothing, exactly the trap {@code OrganizationScope}'s own javadoc warns about.
 * {@code OutboxDrain} calls this bean through its injected field instead, a genuine cross-bean call the proxy actually
 * intercepts.
 *
 * <p>
 * Plain {@code @Transactional} (default {@code REQUIRED}), not {@code REQUIRES_NEW}: {@code OutboxDrain.drain} itself
 * carries no {@code @Transactional} of its own (there is deliberately no ambient transaction wrapping the whole batch
 * any more - see {@link SweepUnit}'s class javadoc for why), and nothing above it in the real call chain
 * ({@code CommunicationSweep.drainTick}, {@code SweepUnit.drain}) opens one either, so in production {@code REQUIRED}
 * always finds nothing to join and behaves identically to {@code REQUIRES_NEW}. {@code REQUIRED} is preferred over
 * {@code REQUIRES_NEW} anyway, matching {@link JdbcOutbox}'s own choice and the reasoning in its class javadoc: a
 * future test exercising this method inside a {@code @DataJdbcTest}-style ambient transaction must be able to join it,
 * not be silently forced onto a second connection that cannot see whatever that ambient transaction has not committed
 * yet.
 *
 * <p>
 * It also keeps one row's database error from sinking the batch: the old design's single transaction across the whole
 * batch meant a database error from one row - a {@code DataAccessException}, not a render or transport failure - left
 * Postgres refusing every further statement on that same connection, including the {@code markFailed} meant to record
 * the very failure that caused it. One row, one connection, one transaction means a row's own database error can only
 * ever abort that row's own transaction: {@link DrainWorkers} catches whatever escapes here and reports that one row
 * failed, and {@link OutboxDrain} records the failure through {@link JdbcOutbox#markFailed}, on a connection this
 * method's failure never touched - by the time that mark is written, this method's own transaction has already rolled
 * back and released its connection, so there is nothing left for {@code markFailed}'s {@code REQUIRED} to even join; it
 * opens a fresh one, same as this method did.
 *
 * <p>
 * Nothing here catches {@link RuntimeException}: that is deliberate. A failure of any kind - suppression bookkeeping,
 * address resolution, rendering, transport, the mark itself - must roll back this row's own transaction rather than
 * leave a half-written mark behind, and must propagate out to {@link DrainWorkers}, which is the only place that knows
 * an account refusal from an ordinary failure, and on to {@link OutboxDrain}, which is the only place that records that
 * failure against a connection this one never poisoned. That is also why {@link #sendToDestination}'s chat health write
 * does not live here at all, even guarded: {@code ChatDestinationsPort#recordDelivered} is itself
 * {@code @Transactional} and reached through a real proxy, so calling it from inside this method's own boundary makes
 * it a PARTICIPATING transaction - Spring marks the ambient transaction rollback-only the moment a participating call
 * throws, catching the exception one frame up notwithstanding. {@link #process} returns the destination id a post
 * actually reached instead, and {@link DrainWorkers} makes the write from its own stack, after this method's
 * transaction has already committed - see {@code DrainWorkers.recordChatDelivered}'s own javadoc for the full
 * reasoning.
 *
 * <p>
 * <b>{@code markSent} is written BEFORE the mail is handed to {@link MailBatch}, not after.</b> SMTP is the one step in
 * this method that cannot be undone, so it goes last, with only the commit itself behind it. The mark is an ordinary
 * transactional write: if it fails, it fails before anybody receives anything, and the row is retried having sent
 * nothing. Written after the send instead, a failing mark - any {@code DataAccessException}, a lost connection, an RLS
 * scope that was never entered - would roll back a transaction whose mail had already left, and the next claim would
 * deliver that same message to that same member a second time. Ordering the two this way costs nothing and removes that
 * entire class of duplicate: the only failure left that can still duplicate is one landing between a successful send
 * and a successful commit, which no ordering can close (see {@link JdbcOutbox#claim}'s "accepted tradeoff").
 *
 * <p>
 * It buys one further, less obvious guarantee. The {@code UPDATE} takes an exclusive row lock that is then held for the
 * duration of the send, and {@link JdbcOutbox#claim} selects {@code FOR UPDATE SKIP LOCKED} - so a second instance
 * whose tick arrives after this row's {@code claimLease} has expired skips a row that is genuinely still in flight
 * rather than reclaiming and re-sending it. That is precisely the duplicate {@code claim}'s javadoc warns "happens
 * routinely, under plain SMTP slowness, with no crash involved at all", and between concurrent instances it no longer
 * does.
 *
 * <p>
 * Rendering deliberately stays ahead of the mark, in {@link #compose}: a template this deploy does not have, or a fact
 * {@link FactRehydration} refuses, is a retryable failure that must not leave a {@code SENT} mark to roll back at all.
 *
 * <p>
 * {@link #compose} mints the unsubscribe token itself, overwriting whatever a sender may have put under
 * {@link UnsubscribeLinks#FACT_KEY}, rather than trusting one carried through from fan-out. This is the one place that
 * already knows both the row's {@code user_id} and its {@code message_type}; the row deliberately carries no address
 * for {@link UnsubscribeTokens} to sign over either, so the link could not have been minted any earlier than this.
 *
 * <p>
 * One token, two URLs, deliberately not one: {@code List-Unsubscribe} - what {@link SmtpDelivery} puts in the header
 * for a mail client's one-click POST - must keep pointing straight at the API, {@code
 * /public/notifications/unsubscribe}, which answers only {@code POST}. The footer link a person actually clicks is a
 * {@code GET}, so it cannot point at the same URL - that would 405 every time, leaving unsubscribe working only for
 * mail clients that honour the header, never for a human. The footer instead points at a frontend page route,
 * {@code /unsubscribe}, carrying the same token; that page is what issues the actual {@code POST}. The two facts are
 * named distinctly - {@link OutgoingMail#unsubscribeUrl} for the header, {@link UnsubscribeLinks#FACT_KEY} for the
 * footer - so {@link MailRenderer}'s templates and {@link SmtpDelivery} can never be crossed by accident.
 */
@Component
@RequiredArgsConstructor
class OutboxRowProcessor {

    private static final String UNSUBSCRIBE_API_PATH = "/public/notifications/unsubscribe?token=";

    private final JdbcOutbox outbox;
    private final DeliveryGate gate;
    private final MailRenderer renderer;
    private final RecipientDirectory recipientDirectory;
    private final Clock clock;
    private final JsonMapper jsonMapper;
    private final UnsubscribeTokens unsubscribeTokens;
    private final PublicUrlProperties publicUrl;
    private final ChatDestinationsPort chatDestinations;
    private final ChatTransport chatTransport;

    /**
     * {@code batch} is an argument, not a collaborator this bean holds: one is created per worker, for a whole claimed
     * slice, while this method's {@code @Transactional} boundary still spans one row and nothing else. The connection
     * underneath it is opened by {@link PacedMailBatch} on the first {@code send} that gets a slot, so the worker whose
     * slice this row opens will pay that connect inside this method's own transaction, and every later row of the slice
     * will not. Nothing about either changes the ordering below - the mark is still written before the one message this
     * call sends, so a failure still loses at most the message genuinely in flight.
     *
     * <p>
     * The return value is empty for every path except one: a chat post that actually reached its destination, in which
     * case it carries that destination's id. Nothing here does anything with it - it exists solely for
     * {@link DrainWorkers#outcomeOf} to read once this method's transaction has committed, which is the one moment the
     * chat health write can safely be made from. See {@link #sendToDestination}'s own javadoc for why that write cannot
     * be made from in here instead.
     */
    @Transactional
    Optional<Long> process(OutboxEntity row, MailBatch batch) {

        var suppression = gate.admit(row);
        if (suppression.isPresent()) {
            outbox.markSuppressed(row.getId(), row.getOrganizationId(), suppression.get().name(), clock.instant());
            return Optional.empty();
        }
        if (DeliveryChannel.CHAT.name().equals(row.getChannel())) {
            if (row.getChatDestinationId() == null) {
                expand(row);
                return Optional.empty();
            }
            return sendToDestination(row);
        }
        if (DeliveryChannel.IN_APP.name().equals(row.getChannel())) {
            // The whole send path. There is no transport, no address and nothing that can fail after this mark, so
            // FAILED and ABANDONED are unreachable for an in-app row and its inherited retry machinery is inert.
            // sent_at is what the panel orders by and what it shows as the entry's age.
            outbox.markSent(row.getId(), row.getOrganizationId(), clock.instant());
            return Optional.empty();
        }
        var recipient = resolveRecipient(row);
        if (recipient.email() == null || recipient.email().isBlank()) {
            outbox.markSuppressed(row.getId(), row.getOrganizationId(), SuppressionReason.NO_ADDRESS.name(),
                    clock.instant());
            return Optional.empty();
        }
        var mail = compose(row, recipient);
        outbox.markSent(row.getId(), row.getOrganizationId(), clock.instant());
        batch.send(mail);
        return Optional.empty();
    }

    /**
     * Everything the mail is made of, and nothing that leaves the process. Split from the delivery call so
     * {@link #process} can write {@code markSent} between the two: a render failure - a missing template, a fact this
     * deploy's {@link FactRehydration} cannot repair - must still fail the row before any mark is written, exactly as
     * it did when this method also sent.
     */
    private OutgoingMail compose(OutboxEntity row, RecipientDirectory.Recipient recipient) {

        var facts = FactRehydration.parse(jsonMapper, row.getFacts());
        var token = unsubscribeTokenFor(row);
        facts.put(UnsubscribeLinks.FACT_KEY, UnsubscribeLinks.pageUrl(publicUrl.publicBaseUrl(), token));
        // The recipient's own clock, resolved beside the address it was already being resolved with, so the hour this
        // message states is the hour on the wall where it is read. Null passes through as null: MomentLine then
        // renders on the session's own clock, which is a true answer where a guessed zone would not be.
        facts.put(MomentLine.READER_ZONE_FACT, recipient.timeZone());
        var rendered = renderer.render(row.getTemplate(), facts);
        var unsubscribeApiUrl = publicUrl.publicBaseUrl() + UNSUBSCRIBE_API_PATH + token;
        return new OutgoingMail(recipient.email(), rendered.subject(), rendered.html(), rendered.text(),
                unsubscribeApiUrl);
    }

    /**
     * The placeholder is a fan-out instruction, not a post. It resolves the community's destinations at the moment it
     * is claimed - which is what keeps a destination added inside a message's lead window able to receive that message
     * - writes one delivery for each, and retires as {@link SuppressionReason#EXPANDED}. Both writes share this
     * method's single transaction, so a failure leaves neither.
     *
     * <p>
     * {@link DeliveryGate#admit} already resolved this same placeholder's destinations once, at admission, and admitted
     * it because that read was non-empty - but the two reads do not share a snapshot. Under read-committed isolation
     * the community's last destination can be removed in the window between the gate's read and this method's own, on a
     * different connection and possibly a different transaction entirely. Resolving again rather than carrying the
     * gate's answer therefore is not merely cheap and freshest-at-the-point-of-use, it is load bearing:
     * {@code destinationIds} is checked again here, and an empty result retires the row
     * {@link SuppressionReason#NO_DESTINATION} instead of {@link SuppressionReason#EXPANDED}. Marking it
     * {@code EXPANDED} regardless, the way an unconditional mark would, records "this fanned out" for a placeholder
     * that in fact produced no children - exactly the failure "why did nothing post to #eng-leads" exists to make
     * answerable from the record, defeated by the record itself.
     */
    private void expand(OutboxEntity row) {

        var now = clock.instant();
        var destinationIds = chatDestinations.destinationsFor(row.getOrganizationId(), row.getSubjectRef());
        if (destinationIds.isEmpty()) {
            outbox.markSuppressed(row.getId(), row.getOrganizationId(), SuppressionReason.NO_DESTINATION.name(), now);
            return;
        }
        outbox.expandChatRow(row, destinationIds, now);
        outbox.markSuppressed(row.getId(), row.getOrganizationId(), SuppressionReason.EXPANDED.name(), now);
    }

    /**
     * One post to one destination. Identical in shape to the single-channel version it replaces, except that the status
     * is resolved by destination id rather than by subject, and the health write names the destination that actually
     * received it.
     *
     * <p>
     * Not the same ordering as the mail path: {@link #compose} renders before {@code markSent}, while here the mark is
     * written first and the render - resolving the message type, rehydrating the facts, and {@link ChatTransport}
     * re-running the allowlist check against the resolved URL - happens after. The outcome is the same either way,
     * which is the property that actually matters: this whole method runs inside {@link #process}'s single transaction,
     * so anything that fails after the mark still fails before that transaction commits, and the mark rolls back with
     * everything else. Nobody ever sees a {@code SENT} row for a message that was never actually posted, exactly as on
     * the mail path - only the order of the steps that gets there differs.
     *
     * <p>
     * The status is resolved again here rather than carried from the gate. It costs one read and closes nothing
     * important, but it keeps the destination read at the moment of send, which is where every other governance answer
     * in this module is read. The mapping from a non-{@code Ready} status to a {@link SuppressionReason} is the exact
     * one {@link DeliveryGate#admitChat} uses, in {@link ChatChannelSuppression} - see that class's own javadoc for why
     * both callers share it rather than each carrying their own copy.
     *
     * <p>
     * Nothing here catches {@link ChatSendException}: it must roll back this row's own {@code markSent}, exactly like a
     * transport failure on the mail path, and propagate to {@link DrainWorkers}, which is where the failure is recorded
     * - on a connection this row's own rolled-back transaction never touched. Recording it here would roll back with
     * the row it describes, and the consecutive-failure count that marks a channel broken would never advance.
     *
     * <p>
     * The cast to {@link ChatChannelStatus.Ready} below is safe because {@link ChatChannelSuppression#forStatus}
     * returns empty for exactly one case, {@code Ready} - so an absent suppression means {@code status} can be nothing
     * else.
     *
     * <p>
     * Unlike the single-channel version this replaces, the health write for a successful post is not made here.
     * {@code ChatDestinationsPort#recordDelivered} is ultimately {@code ChatHealthRecorder.recordDelivered}, itself
     * {@code @Transactional} and reached through a real cross-bean proxy - calling it from in here would make it a
     * transaction PARTICIPATING in {@link #process}'s own {@code REQUIRED} boundary, not an independent one. Spring
     * marks a participating transaction's ambient transaction rollback-only the instant the inner call throws
     * ({@code AbstractPlatformTransactionManager}'s {@code globalRollbackOnParticipationFailure}, true by default), and
     * catching that exception one frame up would not undo the mark: this method's own {@code markSent} would still roll
     * back at commit, now behind an {@code UnexpectedRollbackException} instead of the original failure - the exact
     * duplicate-post window this method exists to close, reopened by the very write meant to record that it didn't.
     * Returning the destination id instead lets {@link DrainWorkers#outcomeOf} make that write from its own stack,
     * after this transaction has already committed rather than merely returned - see
     * {@code DrainWorkers.recordChatDelivered}'s own javadoc.
     */
    private Optional<Long> sendToDestination(OutboxEntity row) {

        var destinationId = row.getChatDestinationId();
        var status = chatDestinations.statusFor(row.getOrganizationId(), destinationId);
        var suppression = ChatChannelSuppression.forStatus(status);
        if (suppression.isPresent()) {
            outbox.markSuppressed(row.getId(), row.getOrganizationId(), suppression.get().name(), clock.instant());
            return Optional.empty();
        }
        var ready = (ChatChannelStatus.Ready) status;
        var type = MessageType.parse(row.getMessageType())
                .orElseThrow(() -> new IllegalStateException("Unknown message type: " + row.getMessageType()));
        var facts = FactRehydration.parse(jsonMapper, row.getFacts());
        outbox.markSent(row.getId(), row.getOrganizationId(), clock.instant());
        chatTransport.post(ready.url(), type, facts);
        return Optional.of(destinationId);
    }

    /**
     * Minted here rather than carried through {@code facts} from fan-out: this is the one place that already knows both
     * the row's {@code user_id} and its {@code message_type}, and the row deliberately carries no address for
     * {@link UnsubscribeTokens} to sign over either - see {@link OutboxDrain}'s class javadoc's note on why the gate
     * runs before address resolution for the same shape of reasoning. One token, signed once, is what both
     * {@link #compose}'s two URLs are built from.
     *
     * <p>
     * {@link DeliveryGate#admit} already suppresses {@link SuppressionReason#UNKNOWN_TYPE} before a row ever reaches
     * this method, so a real deploy cannot hit the throw below - it is a backstop against that invariant breaking, not
     * a path this deploy's own data can reach.
     */
    private String unsubscribeTokenFor(OutboxEntity row) {

        var type = MessageType.parse(row.getMessageType())
                .orElseThrow(() -> new IllegalStateException("Unknown message type: " + row.getMessageType()));
        return unsubscribeTokens.sign(row.getUserId(), row.getOrganizationId(), type);
    }

    /**
     * The whole recipient rather than their address alone: their clock travels the same way, and resolving it a second
     * time from somewhere else is how the address a message went to and the hour it stated would come to describe two
     * different people.
     *
     * <p>
     * A blank address means genuinely no usable address for a member the directory can see -
     * {@link SuppressionReason#NO_ADDRESS}, terminal, correct. A user id the directory cannot see at all - {@code
     * Presence.ERASED} - is a different fact and must not collapse into the same terminal suppression:
     * {@link RecipientDirectory#recipientsOf} is total by construction (one result per requested id, always), so a
     * member who was genuinely erased and an id that came back empty because {@code app.current_org} was never set by
     * whoever wired up the caller are, on this shape alone, indistinguishable. Suppressing the second case forever,
     * silently, the first time that scope is wired wrong is a worse failure than retrying it: throwing here instead
     * lets it fail the row (retryable, and diagnosable through the log line in {@link DrainWorkers}) rather than
     * reading as an ordinary, permanent life-cycle event.
     */
    private RecipientDirectory.Recipient resolveRecipient(OutboxEntity row) {

        var recipient = recipientDirectory.recipientsOf(row.getOrganizationId(), List.of(row.getUserId())).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("identity returned no result for this row's recipient"));
        if (recipient.presence() == RecipientDirectory.Presence.ERASED) {
            throw new IllegalStateException("identity has no visible record for this row's recipient");
        }
        return recipient;
    }
}
