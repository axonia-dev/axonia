package dev.axonia.recognition.domain;

import java.time.Instant;

import lombok.Builder;

/** One member's love of one {@link Recognition}, every stored column carried so a removal round-trips exactly. */
@Builder(toBuilder = true)
public record GivenLove(Long id, String organizationId, Long recognizedContributionId, String loverUserId,
        Instant createdAt, Instant updatedAt, Long version) {
}
