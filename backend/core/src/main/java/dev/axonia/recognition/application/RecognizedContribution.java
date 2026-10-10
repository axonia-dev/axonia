package dev.axonia.recognition.application;

import java.time.Instant;

import dev.axonia.recognition.api.ContributionType;
import lombok.Builder;

@Builder
public record RecognizedContribution(ContributionType contributionType, Long contributionId, Long communityId,
        String communitySlug, String communityName, String title, Instant occurredAt, long loveCount, Long sessionId,
        Long typeId, String typeLabel, String typeIcon, String typeAccentColor) {
}
