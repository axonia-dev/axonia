package dev.axonia.events.internal;

import java.time.Instant;

import org.springframework.stereotype.Component;

import dev.axonia.communication.MessageProducer;
import lombok.RequiredArgsConstructor;

/**
 * The one entry point the communication sweep calls, once per organization per tick, inside
 * {@code OrganizationScope.runAs}. Fans out to the three producers - {@link UpcomingSessionProducer},
 * {@link WrapUpProducer}, {@link AwaitingCloseProducer} - each of which owns one message type's due-work query and
 * recipient list. Order among the three does not matter: each writes only outbox rows under its own message type, and
 * every insert relies on the same unique key to make a re-run a no-op, so nothing here needs to coordinate them beyond
 * calling each in turn.
 *
 * <p>
 * Implements {@code communication}'s {@link MessageProducer} port rather than being called by name: the sweep lives in
 * {@code communication.internal}, which cannot import an {@code events.internal} type - this class stays
 * package-private, exactly like {@code MessageSubjectPolicyAdapter} implementing {@code MessageSubjectPolicy} - and
 * Spring collects the sweep's {@code MessageProducer} dependency by type into a list, which does not require any
 * implementing class itself to be public. This is one element of that list; {@code roundup}'s quarterly producer is
 * another, and neither knows the other exists.
 */
@Component
@RequiredArgsConstructor
class SessionProducers implements MessageProducer {

    private final UpcomingSessionProducer upcomingSessionProducer;
    private final WrapUpProducer wrapUpProducer;
    private final AwaitingCloseProducer awaitingCloseProducer;

    @Override
    public void produce(Instant now) {

        upcomingSessionProducer.produce(now);
        wrapUpProducer.produce(now);
        awaitingCloseProducer.produce(now);
    }
}
