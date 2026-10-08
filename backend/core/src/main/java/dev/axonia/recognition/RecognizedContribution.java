package dev.axonia.recognition;

import java.time.Instant;

import lombok.Builder;

@Builder
public record RecognizedContribution(ContributionType contributionType, Long contributionId, Long communityId,
        String communitySlug, String communityName, String title, Instant occurredAt, long loveCount, Long sessionId,
        Long typeId, String typeLabel, String typeIcon, String typeAccentColor) {
}
