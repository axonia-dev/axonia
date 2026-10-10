package dev.axonia.expertise.application;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.expertise.application.port.out.OrganizationContext;
import dev.axonia.expertise.application.port.out.SignalProjection;
import dev.axonia.expertise.domain.SignalRow;
import dev.axonia.expertise.domain.SignalSource;
import dev.axonia.expertise.domain.SignalType;
import dev.axonia.expertise.domain.SourceType;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivitySignalsTest {

    @Mock
    private SignalProjection signalProjection;

    @Mock
    private OrganizationContext organizationContext;

    @InjectMocks
    private ActivitySignals activitySignals;

    @Test
    void replacingASourceHandsItsRowsToTheProjection() {

        // given
        when(organizationContext.organizationId()).thenReturn("acme");
        var rows = List.of(new SignalRow("alice", "java", SignalType.OFFERED, Instant.EPOCH));

        // when
        activitySignals.replaceSource(1L, SourceType.TOPIC, 10L, rows);

        // then
        verify(signalProjection).replaceSource(new SignalSource("acme", 1L, SourceType.TOPIC, 10L), rows);
    }

    @Test
    void deletingASourceDropsItFromTheProjection() {

        // given
        when(organizationContext.organizationId()).thenReturn("acme");

        // when
        activitySignals.deleteSource(SourceType.SLOT, 20L);

        // then
        verify(signalProjection).deleteSource("acme", SourceType.SLOT, 20L);
    }
}
