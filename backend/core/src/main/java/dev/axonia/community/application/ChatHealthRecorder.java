package dev.axonia.community.application;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.communication.api.ChatFailureKind;
import dev.axonia.communication.api.spi.ChatDestinationsPort.ChatFailure;
import dev.axonia.community.application.port.out.ChatDestinationStore;
import lombok.RequiredArgsConstructor;

/**
 * The two chat health writes, each its own bean method rather than a method on {@code ChatDestinationService} that
 * another of its own methods calls: {@code @Transactional} only ever works through a real proxy invocation, and a
 * sibling call on {@code this} would bypass that proxy entirely.
 *
 * <p>
 * A destination that no longer exists is not an error here. A leader can remove one while a delivery for it is in
 * flight, and a health write that threw would fail a row whose real outcome was already decided.
 */
@Component
@RequiredArgsConstructor
public class ChatHealthRecorder {

    private final ChatDestinationStore destinations;

    /**
     * A revoked webhook is unambiguous, so GONE breaks the destination on the first strike. Ordinary weather increments
     * instead, and {@code brokenAfter} consecutive failed attempts declare it broken. Any success resets the count.
     */
    @Transactional
    public void recordFailure(String organizationId, long destinationId, ChatFailure failure, int brokenAfter) {

        destinations.find(organizationId, destinationId).ifPresent(entity -> {
            var failures = entity.getConsecutiveFailures() + 1;
            var broken = failure.kind() == ChatFailureKind.GONE || failures >= brokenAfter;
            destinations.update(entity.toBuilder().consecutiveFailures(failures).broken(broken)
                    .lastFailureAt(failure.at()).lastError(failure.error()).build());
        });
    }

    @Transactional
    public void recordDelivered(String organizationId, long destinationId, Instant at) {

        destinations.find(organizationId, destinationId).ifPresent(entity -> destinations.update(
                entity.toBuilder().lastPostedAt(at).consecutiveFailures(0).broken(false).lastError(null).build()));
    }
}
