package dev.axonia.recognition.domain;

import java.time.Instant;

import dev.axonia.recognition.api.ContributionType;
import lombok.experimental.UtilityClass;

@UtilityClass
public class RecognitionMother {

    public Recognition.RecognitionBuilder any() {

        return Recognition.builder().organizationId("default").contributionType(ContributionType.SLOT_PRESENTED)
                .contributionId(100L).communityId(1L).title("Resilience patterns talk")
                .occurredAt(Instant.parse("2026-06-01T10:00:00Z"));
    }
}
