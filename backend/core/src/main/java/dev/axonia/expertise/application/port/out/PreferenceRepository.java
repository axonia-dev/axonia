package dev.axonia.expertise.application.port.out;

import java.util.Set;

import dev.axonia.expertise.domain.SignalRef;

/**
 * Read/write port for per-user expertise preferences: opt-out, hidden tags, and dismissed signals.
 *
 * <p>
 * All methods operate under RLS (the caller must have set {@code app.current_org}). The {@code org} parameter is passed
 * explicitly as a defence-in-depth measure.
 */
public interface PreferenceRepository {

    // ------------------------------------------------------------------
    // Opt-out
    // ------------------------------------------------------------------

    boolean isOptedOut(String org, String userId);

    /**
     * Inserts (optedOut=true) or deletes (optedOut=false) the opt-out row. Idempotent.
     */
    void setOptOut(String org, String userId, boolean optedOut);

    // ------------------------------------------------------------------
    // Hidden tags
    // ------------------------------------------------------------------

    Set<String> hiddenTags(String org, String userId);

    /** Inserts the hidden-tag row; ON CONFLICT DO NOTHING so idempotent. */
    void hideTag(String org, String userId, String canonicalTag);

    void unhideTag(String org, String userId, String canonicalTag);

    // ------------------------------------------------------------------
    // Dismissed signals
    // ------------------------------------------------------------------

    /** Inserts the dismissed-signal row; ON CONFLICT DO NOTHING so idempotent. */
    void dismissSignal(String org, String userId, SignalRef ref, String canonicalTag);
}
