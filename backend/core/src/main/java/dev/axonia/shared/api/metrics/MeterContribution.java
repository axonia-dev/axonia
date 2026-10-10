package dev.axonia.shared.api.metrics;

import java.util.List;

import dev.axonia.shared.api.metrics.IntegrationMetrics.Integration;
import dev.axonia.shared.api.metrics.UsageCounters.Event;
import dev.axonia.shared.api.schedule.SweepHeartbeat.Sweep;

/**
 * The sweeps, outbound integrations and product events a module records beyond the open vocabulary each meter class
 * declares itself. Every contributed entry is registered at startup, so its series exists before the first occurrence,
 * and recording one nobody contributed fails at the call rather than writing a series no dashboard reads.
 */
public interface MeterContribution {

    default List<Sweep> sweeps() {

        return List.of();
    }

    default List<Integration> integrations() {

        return List.of();
    }

    default List<Event> events() {

        return List.of();
    }
}
