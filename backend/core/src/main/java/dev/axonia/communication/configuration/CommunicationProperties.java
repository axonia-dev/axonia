package dev.axonia.communication.configuration;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import dev.axonia.communication.adapter.out.persistence.JdbcOutbox;
import dev.axonia.communication.application.CommunicationSettings;
import dev.axonia.communication.application.CommunicationWindowsAdapter;
import dev.axonia.communication.application.OutboxDrain;
import dev.axonia.communication.application.UnsubscribeTokens;

/**
 * Bound configuration for outbound mail: how many outbox rows one drain tick takes at once, and the three knobs
 * {@link JdbcOutbox#claim} needs to retry a {@code FAILED} row and reclaim a row stranded in {@code SENDING} - see the
 * javadoc there for what each defends against and why a duplicate send is the accepted cost of {@code claimLease}.
 *
 * <p>
 * {@code retryBackoff} and {@code claimLease} exist because a retry after a backoff and a killed sweep's stranded row
 * are not implementable without them - a claim over {@code PENDING} rows alone could do neither.
 *
 * <p>
 * {@code batchSize} and {@code claimLease} are coupled: {@code claimed_at} is stamped once, at claim time, and never
 * renewed while a batch drains, so {@code batchSize * (worst-case time per message)} must stay well under
 * {@code claimLease} or a second instance reclaims and re-sends rows the first is still working through - see
 * {@code application.properties}' comment on the two defaults for the arithmetic.
 *
 * <p>
 * {@code drainBudget} is what bounds a whole drain tick now that {@link OutboxDrain#drain} claims repeatedly rather
 * than once: wall clock, not a row count. {@code CommunicationSweep#drainTick} computes ONE deadline from it, once per
 * tick, and hands that same deadline to every organization's {@link OutboxDrain#drain} call in turn - not a fresh
 * deadline per organization - so the budget belongs to the tick, not to whichever organization happens to be draining.
 * A heavily backlogged organization can still spend the whole of it, but it can no longer add its own full budget on
 * top of every other organization's: two organizations both with a full queue no longer make one tick run 2 x
 * {@code drainBudget}. {@code CommunicationSweep} also rotates which organization the tick starts with, so an
 * organization whose turn a heavy neighbour consumed this tick is the one to go first next time, rather than being
 * permanently last. None of this, by itself, keeps two ticks from overlapping; {@code drainTick}'s own
 * {@code fixedDelay} scheduling is what does that, by not starting the next tick until the previous one has already
 * returned. Nor is the deadline exact: it is only checked between batches, so the tick can run over budget by up to one
 * whole batch. Bounding by time rather than by rows is still the point of the change: the old one-batch-per-tick shape
 * capped throughput at batchSize per interval regardless of how fast the transport actually was.
 *
 * <p>
 * {@code sendRate} is messages per second handed to the transport, across every worker and every tenant. 10 is the mail
 * provider's documented per-account limit; this is the vendor's ceiling expressed as configuration, not a guess.
 *
 * <p>
 * {@code drainWorkers} is how many rows are in flight at once, and it is bounded by two independent ceilings. Each row
 * holds a database connection for the whole duration of its send, so the primary Hikari pool is sized explicitly
 * against this value rather than left on a framework default - see {@code application.properties} for the arithmetic,
 * and raise neither alone. Each worker also holds one SMTP connection for the length of its slice, and providers cap
 * simultaneous connections per account separately from the message rate, so {@code sendRate} says nothing about this
 * one. It also loosens the {@code claimLease} arithmetic above: the time a batch takes becomes
 * {@code batchSize / drainWorkers} rows' worth of worst case, not {@code batchSize}.
 *
 * <p>
 * {@code unsubscribeSecret} is the HMAC key {@link UnsubscribeTokens} signs and verifies with; its {@code Pattern}
 * exists for the same reason {@code EdgeProperties.sharedSecrets} carries one - the configuration binder does NOT fail
 * on an unresolved {@code ${...}} placeholder, it hands over the literal placeholder text, which would otherwise sign
 * every token in production with a string committed to this repository. The origin {@link UnsubscribeTokens}'s links
 * point at is not this module's own - see {@code PublicUrlProperties} - because more than one context needs the exact
 * same one.
 *
 * <p>
 * {@code wrapUpMaxAge} and {@code awaitingCloseMaxAge} are the floors {@link CommunicationWindowsAdapter} exposes
 * cross-module as {@code CommunicationWindows} - see that port's javadoc for what an unbounded producer query costs. 30
 * days for both: long enough that a session genuinely still worth mailing about (a slow-to-release recording, an
 * organizer on leave who has not closed a session out) is not silently dropped, short enough that a roster change
 * months later cannot still be re-mailing or re-nudging about it.
 *
 * <p>
 * {@code outboxRetention} is how long a message and its deliveries survive after being written, and
 * {@code retentionBatchSize} is how many messages one pass expires per organization. The window stays here rather than
 * joining {@code CommunicationWindows}: no other module needs it, because the expired-message record leaves
 * {@code events} unchanged.
 */
@Validated
@ConfigurationProperties(prefix = "axonia.communication")
public record CommunicationProperties(@DefaultValue("20") @Min(1) int batchSize,
        @DefaultValue("45s") Duration drainBudget, @DefaultValue("10") @Min(1) int sendRate,
        @DefaultValue("4") @Min(1) int drainWorkers, @DefaultValue("5") @Min(1) int maxAttempts,
        @DefaultValue("5m") Duration retryBackoff, @DefaultValue("15m") Duration claimLease,
        @DefaultValue("30d") Duration wrapUpMaxAge, @DefaultValue("30d") Duration awaitingCloseMaxAge,
        @DefaultValue("90d") Duration outboxRetention, @DefaultValue("200") @Min(1) int retentionBatchSize,
        @NotBlank @Pattern(regexp = "^(?!.*\\$\\{).+$", message = "must not contain an unresolved placeholder") String unsubscribeSecret)
        implements
            CommunicationSettings {
}
