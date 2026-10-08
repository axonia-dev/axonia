package dev.axonia;

/**
 * Whether the edge gate is actually refusing unstamped requests right now.
 *
 * <p>
 * The gate registers itself behind {@code @ConditionalOnProperty}, so when it is off there is no bean, no log line and
 * no other trace of it: the edge stamps regardless of enforcement, so a diagnostic reading the stamp still reports
 * {@code edge-stamp}, the rate-limit counters still move, and the containment check still passes. An origin left wide
 * open therefore looks identical to a closed one from every other signal. This is the one that differs.
 *
 * <p>
 * It exists as a one-method port in the shared kernel rather than as a getter on the properties record because
 * {@code dev.axonia.config.EdgeProperties} is package-private and should stay that way: another module needs the single
 * fact this reports, not the secrets alongside it.
 */
@FunctionalInterface
public interface EdgeGateStatus {

    /** True when a request arriving without a valid edge stamp is refused. */
    boolean enforcing();
}
