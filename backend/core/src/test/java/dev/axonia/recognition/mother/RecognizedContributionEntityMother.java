package dev.axonia.recognition.mother;

import java.time.Instant;

import dev.axonia.recognition.ContributionType;
import dev.axonia.recognition.internal.RecognizedContributionEntity;
import lombok.experimental.UtilityClass;

@UtilityClass
public class RecognizedContributionEntityMother {

    public RecognizedContributionEntity.RecognizedContributionEntityBuilder any() {

        return RecognizedContributionEntity.builder().organizationId("default")
                .contributionType(ContributionType.SLOT_PRESENTED).contributionId(100L).communityId(1L)
                .title("Resilience patterns talk").occurredAt(Instant.parse("2026-06-01T10:00:00Z"));
    }
}
