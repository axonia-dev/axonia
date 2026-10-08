package dev.axonia.installation.internal;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dev.axonia.communication.DeliveryOutcome;
import dev.axonia.communication.MailOutcome;
import dev.axonia.communication.MailRefusal;
import dev.axonia.communication.MailServer;
import dev.axonia.communication.MailServerState;
import lombok.extern.slf4j.Slf4j;

/**
 * The mail server delivery sends through: the stored settings under the deployment's, read when the context starts,
 * again by a revision check on the scheduler's thread every five seconds and after a save here has committed, and
 * answered from memory, its state built once per read. Holds no lock: a statement here may wait on the row a save is
 * writing, and nothing a save waits on may be held meanwhile.
 */
@Slf4j
@Component
class StoredMailServer implements MailServer, SmartInitializingSingleton {

    private final MailServerResolver resolver;

    private final MailServerSettingsTable table;

    private final SettingsKeyRotation rotation;

    private final Clock clock;

    private final AtomicReference<Published> published = new AtomicReference<>();

    private final AtomicReference<String> recordedFailure = new AtomicReference<>();

    StoredMailServer(MailServerResolver resolver, MailServerSettingsTable table, SettingsKeyRotation rotation,
            Clock clock) {

        this.resolver = resolver;
        this.table = table;
        this.rotation = rotation;
        this.clock = clock;
    }

    /** A read and the state built from it once, so no caller ever builds or validates settings again. */
    private record Published(ResolvedMailServer resolved, MailServerState state) {

        long revision() {

            return resolved.row().revision();
        }
    }

    /** Before any scheduled task, so no outbox drain ever sees the state unread. */
    @Override
    public void afterSingletonsInstantiated() {

        reload();
    }

    /** Asked inside a row's transaction: no SQL, no lock, only the state last read. */
    @Override
    public MailServerState current() {

        return published.get().state();
    }

    /**
     * On the sweep thread, outside any transaction; writes only a change. Two writes of one change are harmless, so the
     * comparison and the write need no lock. A recipient refused is that recipient's, not the server's. An outcome
     * about other settings than the ones held, from a batch that connected before a save or a clear, says nothing about
     * them, and the write holds to the revision held, so a save committing meanwhile is never marked failing.
     */
    @Override
    public void recordOutcome(DeliveryOutcome reported) {

        var held = published.get();
        if (!(held.state() instanceof MailServerState.Configured configured) || !reported.isAbout(configured.settings())
                || isRecipientRefused(reported.outcome())) {
            return;
        }
        var failure = reported.outcome() instanceof MailOutcome.Refused failed ? failed.reason().code() : null;
        var recorded = recordedFailure.get();
        if (Objects.equals(failure, recorded)) {
            return;
        }
        try {
            if (failure == null) {
                table.clearFailure(held.revision());
            } else {
                table.recordFailure(failure, clock.instant().truncatedTo(ChronoUnit.MILLIS), held.revision());
            }
            recordedFailure.compareAndSet(recorded, failure);
        } catch (DataAccessException e) {
            log.warn("The mail server's delivery state could not be recorded: {}", e.getMessage());
        }
    }

    private static boolean isRecipientRefused(MailOutcome outcome) {

        return outcome instanceof MailOutcome.Refused refused && refused.reason() == MailRefusal.RECIPIENT_REFUSED;
    }

    /**
     * How a save on another instance reaches this one; the delivery state comes along. The read replaces what was held
     * when the check began whatever its revision, so a database restored to an older revision is taken too, unless a
     * save here published meanwhile. A row read under another key this instance holds is re-sealed under its own, so
     * the row settles under the key of the instances that remain after a rotation. Keeps the last state on error.
     */
    @Scheduled(initialDelayString = "${axonia.installation.mail-server-check:PT5S}", fixedDelayString = "${axonia.installation.mail-server-check:PT5S}")
    public void checkRevision() {

        try {
            var held = published.get();
            var watermark = table.watermark();
            if (watermark.revision() != held.revision()) {
                published.compareAndSet(held, withState(resolver.resolve(), held));
            }
            rotation.sealUnderTheCurrentKey(watermark);
            recordedFailure.set(watermark.lastFailureReason());
        } catch (DataAccessException e) {
            log.warn("The mail server's revision could not be checked: {}", e.getMessage());
        }
    }

    /** After a save or a clear on this instance has committed, so its next answer is the new one. */
    void reload() {

        var previous = published.get();
        var read = withState(resolver.resolve(), previous);
        var held = published.accumulateAndGet(read,
                (current, next) -> current == null || next.revision() >= current.revision() ? next : current);
        if (held == read) {
            recordedFailure.set(read.resolved().row().lastFailureReason());
        }
    }

    /** Settings delivery would refuse fail this one read, logged, and the last state read is kept. */
    private static Published withState(ResolvedMailServer read, Published previous) {

        try {
            return new Published(read, read.state());
        } catch (IllegalArgumentException e) {
            log.warn("The stored mail server settings were refused ({}); the last ones read are kept.", e.getMessage());
            return new Published(read, previous == null ? MailServerState.notConfigured() : previous.state());
        }
    }
}
