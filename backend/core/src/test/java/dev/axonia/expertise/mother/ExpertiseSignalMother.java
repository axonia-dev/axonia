package dev.axonia.expertise.mother;

import java.time.Instant;

import dev.axonia.expertise.ExpertiseSignal;
import dev.axonia.expertise.SignalType;
import dev.axonia.expertise.SourceType;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ExpertiseSignalMother {

    public ExpertiseSignal.ExpertiseSignalBuilder any() {

        return ExpertiseSignal.builder().id(1L).organizationId("org-1").communityId(1L).userId("user-1").tag("kafka")
                .signalType(SignalType.PRESENTED).sourceType(SourceType.SLOT).sourceId(1L)
                .occurredAt(Instant.parse("2026-01-01T00:00:00Z"));
    }
}
