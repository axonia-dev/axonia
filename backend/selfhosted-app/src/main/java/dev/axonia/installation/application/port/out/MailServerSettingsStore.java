package dev.axonia.installation.application.port.out;

import java.time.Instant;

import dev.axonia.installation.domain.MailServerRow;
import dev.axonia.installation.domain.MailServerWatermark;

/** The one stored mail server; every write is conditional on what was read, so two instances never undo each other. */
public interface MailServerSettingsStore {

    MailServerRow read();

    MailServerWatermark watermark();

    /**
     * Only on the revision read and the envelope read, so a re-seal never overwrites a save or another instance's
     * re-seal; it moves no revision.
     */
    boolean replacePasswordSecret(long revision, String sealedBefore, String sealedNow);

    boolean replaceTestedFingerprint(long revision, String before, String now);

    /**
     * Keeps when failures began while the reason changes; a delivery state never moves the revision, and is written
     * only on the revision of the settings it is about.
     */
    boolean recordFailure(String reason, Instant at, long revision);

    boolean clearFailure(long revision);

    /** A save or a clear, only on the revision it read; moves the revision and forgets the delivery state. */
    boolean write(MailServerRow next, Instant at);
}
