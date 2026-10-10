package dev.axonia.installation.domain;

/**
 * What each revision check reads: the revision, the delivery state, and the identifiers of the keys the password and
 * the tested fingerprint are under (null when none is stored).
 */
public record MailServerWatermark(long revision, String lastFailureReason, String passwordKeyId,
        String fingerprintKeyId) {
}
