package dev.axonia.communication.api.spi;

import java.time.Instant;

import dev.axonia.communication.api.CommunicationPort;

/**
 * Port the communication sweep calls once per organization per tick (inside {@code OrganizationScope.runAs}) to let a
 * bounded context that decides who cares about something and what it says enqueue its own due work, without
 * {@code communication} ever learning what triggered any of it - the same dependency inversion
 * {@link MessageSubjectPolicy} already uses to keep the module graph acyclic.
 *
 * <p>
 * Declared here, in the module the sweep lives in, and implemented once per module that produces messages - today,
 * {@code events}' {@code SessionProducers} and {@code roundup}'s quarterly producer, which the sweep injects as a list
 * and calls in turn without learning what either is about. {@code events} already depends on {@code communication} to
 * call {@link CommunicationPort#enqueue}, so an implementation living in {@code events} adds no new edge; declaring
 * this port the other way round - {@code communication} importing an {@code events} type so the sweep could call
 * {@code SessionProducers} directly - would create the cycle {@code ApplicationModules.verify()} rejects, and would
 * also make {@code SessionProducers} a type the sweep could only reach if it were public, which it has no other reason
 * to be.
 */
public interface MessageProducer {

    /** Enqueues whatever is due, for the Organization the caller is already scoped to via {@code OrganizationScope}. */
    void produce(Instant now);
}
