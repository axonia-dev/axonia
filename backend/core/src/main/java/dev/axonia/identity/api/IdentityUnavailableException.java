package dev.axonia.identity.api;

/**
 * The identity provider could not be reached, or answered in a way an adapter reading it could not interpret.
 *
 * <p>
 * Every failure mode such an adapter can hit - a connection failure, a timeout, an HTTP error status, a malformed token
 * response - is wrapped into this one port-owned type, so a caller degrades on the port's own vocabulary instead of
 * reaching past it into one adapter's transport library.
 *
 * <p>
 * {@code timeout} distinguishes a request that started but did not finish in time from every other kind of failure,
 * because a support screen has something more specific to say about the first.
 */
public class IdentityUnavailableException extends RuntimeException {

    private final boolean timeout;

    private IdentityUnavailableException(String message, Throwable cause, boolean timeout) {
        super(message, cause);
        this.timeout = timeout;
    }

    public static IdentityUnavailableException timeout(Throwable cause) {
        return new IdentityUnavailableException(cause.getMessage(), cause, true);
    }

    public static IdentityUnavailableException flatFailure(Throwable cause) {
        return new IdentityUnavailableException(cause.getMessage(), cause, false);
    }

    public boolean isTimeout() {
        return timeout;
    }
}
