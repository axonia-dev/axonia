package dev.axonia.shared.api.schedule;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.axonia.shared.api.metrics.MeterContribution;
import dev.axonia.shared.api.schedule.SweepHeartbeat.Sweep;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SweepHeartbeatTest {

    private static final Instant STARTED_AT = Instant.parse("2026-08-23T10:00:00Z");

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final Clock clock = Clock.fixed(STARTED_AT, ZoneOffset.UTC);

    private final SweepHeartbeat heartbeat = new SweepHeartbeat(registry, clock);

    @Test
    void countsASuccessfulRun() {

        heartbeat.record(Sweep.COMMUNICATION_DRAIN, () -> {
        });

        assertThat(runsOf("communication-drain", "success")).isEqualTo(1.0);
    }

    @Test
    void recordsWhenTheRunLastSucceeded() {

        heartbeat.record(Sweep.COMMUNICATION_DRAIN, () -> {
        });

        assertThat(lastSuccessOf("communication-drain")).isEqualTo(STARTED_AT.getEpochSecond());
    }

    @Test
    void countsAFailureAndRethrowsIt() {

        assertThatThrownBy(() -> heartbeat.record(Sweep.COMMUNICATION_DRAIN, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(runsOf("communication-drain", "failure")).isEqualTo(1.0);
    }

    @Test
    void leavesTheLastSuccessAloneWhenARunFails() {

        heartbeat.record(Sweep.COMMUNICATION_DRAIN, () -> {
        });
        assertThatThrownBy(() -> heartbeat.record(Sweep.COMMUNICATION_DRAIN, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(lastSuccessOf("communication-drain")).isEqualTo(STARTED_AT.getEpochSecond());
    }

    /**
     * The hole a lazily-registered gauge leaves. A sweep broken since the deploy has never succeeded, so it would emit
     * no series at all, so {@code time() - axonia_sweep_last_success_seconds} would match nothing and the alert would
     * stay quiet for exactly the failure it exists to catch. Seeding at zero instead would page on every redeploy,
     * since a 24-hour sweep's first run is a day away.
     */
    @Test
    void publishesAGaugeSeededAtProcessStartForASweepThatHasNeverRun() {

        assertThat(lastSuccessOf("communication-retention")).isEqualTo(STARTED_AT.getEpochSecond());
    }

    @Test
    void publishesBothRunCountersAtZeroForASweepThatHasNeverRun() {

        assertThat(runsOf("agent-connection-retention", "success")).isZero();
        assertThat(runsOf("agent-connection-retention", "failure")).isZero();
    }

    /**
     * Asserted against {@link Sweep#open()} rather than against a hand-written list, so an open sweep cannot be
     * declared without being registered: the map this reads is built from the same list.
     */
    @Test
    void everySweepIsWatchedFromProcessStart() {

        assertThat(registry.get("axonia.sweep.last.success.seconds").gauges())
                .extracting(gauge -> gauge.getId().getTag("sweep"))
                .containsExactlyInAnyOrderElementsOf(Sweep.open().stream().map(Sweep::tag).toList());
    }

    /** The strings the alert rules match on, pinned so a rename cannot pass silently. */
    @Test
    void carriesTheContractTagOfEverySweep() {

        assertThat(Sweep.open().stream().map(Sweep::tag)).containsExactly("communication-produce",
                "communication-drain", "communication-retention", "agent-connection-retention");
    }

    @Test
    void registersAContributedSweepBesideTheOpenOnes() {

        // given
        var contributed = new Sweep("contributed-sweep");
        MeterContribution contribution = new MeterContribution() {

            @Override
            public List<Sweep> sweeps() {

                return List.of(contributed);
            }
        };
        var heartbeat = new SweepHeartbeat(registry, clock, List.of(contribution));

        // when
        heartbeat.record(contributed, () -> {
        });

        // then
        assertThat(registry.get("axonia.sweep.runs").tag("sweep", "contributed-sweep").tag("outcome", "success")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void refusesASweepNobodyContributed() {

        var heartbeat = new SweepHeartbeat(registry, clock);

        assertThatThrownBy(() -> heartbeat.record(new Sweep("billing-sweep"), () -> {
        })).hasMessageStartingWith("No meter is registered for billing-sweep");
    }

    private double runsOf(String sweep, String outcome) {

        return registry.get("axonia.sweep.runs").tag("sweep", sweep).tag("outcome", outcome).counter().count();
    }

    private double lastSuccessOf(String sweep) {

        return registry.get("axonia.sweep.last.success.seconds").tag("sweep", sweep).gauge().value();
    }
}
