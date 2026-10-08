package dev.axonia.config;

import org.springframework.security.core.AuthenticationException;

import com.nimbusds.jose.KeySourceException;

/**
 * The bearer could not be checked because its issuer cannot be reached. An {@link AuthenticationException} that is not
 * an {@code AuthenticationServiceException}, so the bearer filter hands it to the entry point instead of rethrowing it.
 */
class IssuerUnavailableException extends AuthenticationException {

    static final String CODE = "issuer_unavailable";

    /**
     * How soon to ask again: the Self-hosted {@code IssuerKeys} asks the issuer at most once every ten seconds, and the
     * Cloud's decoder retries on the next request.
     */
    static final String RETRY_AFTER_SECONDS = "10";

    IssuerUnavailableException(Throwable cause) {

        super("The token's issuer cannot be reached", cause);
    }

    /** Whether the issuer's keys could not be fetched, the one way a built decoder fails for want of its issuer. */
    static boolean isKeyFetchFailure(Throwable failure) {

        for (var cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof KeySourceException) {
                return true;
            }
        }
        return false;
    }
}
