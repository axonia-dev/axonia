package dev.axonia.expertise.domain;

import java.time.Instant;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ExpertiseSignalMother {

    public ExpertiseSignal.ExpertiseSignalBuilder any() {

        return ExpertiseSignal.builder().id(1L).organizationId("org-1").communityId(1L).userId("user-1").tag("kafka")
                .signalType(SignalType.PRESENTED).sourceType(SourceType.SLOT).sourceId(1L)
                .occurredAt(Instant.parse("2026-01-01T00:00:00Z"));
    }
}
