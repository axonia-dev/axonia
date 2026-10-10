package dev.axonia.shared.adapter.in.schedule;

import org.springframework.scheduling.annotation.Scheduled;

import lombok.RequiredArgsConstructor;

/**
 * Asks the registrar, on every instance, to put back any clustered task trigger Quartz left in error. Local rather than
 * clustered on purpose: a recovery that was itself a clustered task could be stopped by the very error it exists to
 * repair.
 */
@RequiredArgsConstructor
public class ClusteredTaskRecovery {

    private final ClusteredTaskRegistrar registrar;

    @Scheduled(initialDelayString = "${axonia.scheduler.error-recovery-interval:5m}", fixedDelayString = "${axonia.scheduler.error-recovery-interval:5m}")
    public void recover() {

        registrar.recover();
    }
}
