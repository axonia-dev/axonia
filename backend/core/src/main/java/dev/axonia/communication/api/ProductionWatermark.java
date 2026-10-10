package dev.axonia.communication.api;

import java.time.Instant;

/**
 * A producer's own record of how far it has already claimed. Every producer - today, {@code events}'
 * {@code UpcomingSessionProducer}, {@code WrapUpProducer} and {@code AwaitingCloseProducer}'s two arms - already has a
 * due instant that never changes once computed ({@code scheduled_at - leadDays}, {@code wrapUpReleaseAt},
 * {@code scheduled_at + 1 day}, {@code scheduled_at + 7 days}). {@link #since} answers "what is the latest due instant
 * I have already claimed", so a producer's own query can narrow its due-work window to {@code due ∈ (since, now]}
 * instead of re-deriving every record in a rolling max-age window on every tick, and {@link #advanceTo} records that
 * this tick's {@code now} is the new answer once the tick's enqueues have landed.
 *
 * <p>
 * Declared here, in {@code communication}, for the identical reason {@link CommunicationPort} and
 * {@link CommunicationWindows} are: the sweep's machinery lives in this module, {@code events} already depends on it to
 * call {@link CommunicationPort#enqueue}, and a port declared the other way round would create the module cycle
 * {@code ApplicationModules.verify()} rejects. One row per {@code (organization_id, producer_key)} backs both methods;
 * {@code producer_key} is a producer's own stable identifier, not a {@link MessageType} - {@code
 * AwaitingCloseProducer}'s two arms share one message type but need two independent watermarks, since each fires on its
 * own due instant.
 */
public interface ProductionWatermark {

    /**
     * The exclusive lower bound a producer should claim from: the stored watermark for {@code producerKey}, or
     * {@code floor} when no row exists yet - the first-run and long-outage backstop, computed by the caller from its
     * own {@code CommunicationWindows} max-age (or, for a producer with no natural lookback risk, a caller-chosen
     * constant such as {@link java.time.Instant#EPOCH}).
     */
    Instant since(String producerKey, Instant floor);

    /**
     * Records {@code now} as {@code producerKey}'s new watermark. The caller must call this only after every row this
     * tick's due window produced has already been durably enqueued, and from within the same transaction as those
     * enqueues - see {@code WrapUpProducer}'s class javadoc for what a crash between the two would otherwise cost.
     */
    void advanceTo(String producerKey, Instant now);
}
