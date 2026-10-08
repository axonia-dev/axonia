package dev.axonia.selfhosted.internal;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestOperations;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSetBasedJWKSource;
import com.nimbusds.jose.jwk.source.JWKSetCacheRefreshEvaluator;
import com.nimbusds.jose.jwk.source.JWKSetSource;
import com.nimbusds.jose.jwk.source.JWKSetUnavailableException;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.JSONObjectUtils;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The issuer's signing keys, read from the {@code jwks_uri} of its discovery document, both fetched when the first
 * token arrives. Nimbus keeps them for {@link Lifetimes#cache()} and refreshes them on a schedule, off every request,
 * {@link Lifetimes#refreshAhead()} plus {@link Lifetimes#refreshWait()} before they lapse, so a token whose key is held
 * never waits on the issuer. That holds while each call to the issuer ends within half of those two together: a refresh
 * that runs into the deadline still ends before the keys lapse, and so does the next.
 *
 * <p>
 * A refresh that fails, whatever the issuer answered, keeps the keys of the last one that worked for
 * {@link Lifetimes#outage()} after it, a withdrawn key included; past that, a token is refused as the issuer being
 * unavailable until a refresh works again. A token whose key is not held asks for a refresh, waiting at most
 * {@link Lifetimes#refreshWait()} for one already under way.
 *
 * <p>
 * The issuer is asked at most once every {@link #BETWEEN_FETCHES}, so a stream of tokens naming keys nobody published
 * costs it one request per interval. When a token's key is not among those held, the last fetch decides the answer: the
 * issuer could not be reached (no connection, a timeout, a 5xx), so the token is refused as the issuer being
 * unavailable; or it answered, so the key is unknown and the token is rejected. An issuer that refuses with a 4xx or
 * sends a key set that does not parse has answered. With no keys at all, every token is refused as the issuer being
 * unavailable.
 *
 * <p>
 * A key endpoint that answers 404 or 410 has moved: it answered all the same, and the next fetch reads the discovery
 * document again for its {@code jwks_uri}.
 */
@Slf4j
final class IssuerKeys implements JWKSource<SecurityContext>, Closeable {

    private static final Duration BETWEEN_FETCHES = Duration.ofSeconds(10);

    private static final MediaType JWK_SET = new MediaType("application", "jwk-set+json");

    private final Fetch fetch;

    private final JWKSetBasedJWKSource<SecurityContext> held;

    IssuerKeys(String issuer, RestOperations issuerRequests, Clock clock, Lifetimes lifetimes) {

        fetch = new Fetch(issuer, issuerRequests, clock);
        var refreshes = Thread.ofPlatform().daemon().name("issuer-keys-", 0).factory();
        held = (JWKSetBasedJWKSource<SecurityContext>) JWKSourceBuilder.create(fetch)
                .cache(lifetimes.cache().toMillis(), lifetimes.refreshWait().toMillis())
                .refreshAheadCache(lifetimes.refreshAhead().toMillis(), null,
                        Executors.newSingleThreadExecutor(refreshes), true,
                        Executors.newSingleThreadScheduledExecutor(refreshes), true)
                .rateLimited(false).outageTolerant(lifetimes.outage().toMillis()).build();
    }

    @Override
    public List<JWK> get(JWKSelector selector, @Nullable SecurityContext context) throws KeySourceException {

        List<JWK> keys;
        try {
            keys = held.get(selector, context);
        } catch (KeySourceException e) {
            throw new Unavailable(e);
        }
        if (keys.isEmpty() && fetch.unreachable) {
            throw new Unavailable(null);
        }
        return keys;
    }

    @Override
    public void close() throws IOException {

        held.close();
    }

    /**
     * How long the keys are held, how long before they lapse a scheduled refresh starts, how long a request waits for a
     * refresh under way, and how long after the last refresh that worked a failing one keeps the keys it had.
     */
    record Lifetimes(Duration cache, Duration refreshAhead, Duration refreshWait, Duration outage) {

        static final Lifetimes STANDARD = new Lifetimes(Duration.ofMinutes(5), Duration.ofSeconds(30),
                Duration.ofSeconds(5), Duration.ofMinutes(15));
    }

    /** A token whose key cannot be had because the issuer cannot be reached, or because no key was ever fetched. */
    static final class Unavailable extends KeySourceException {

        private static final long serialVersionUID = 1L;

        Unavailable(@Nullable Throwable cause) {

            super("The issuer's keys cannot be fetched", cause);
        }
    }

    /**
     * One request to the issuer, at most once every {@link #BETWEEN_FETCHES}. Nimbus's cache calls it under its refresh
     * lock, one thread at a time. The issuer could not be reached when it answered with a 5xx or when any I/O failed, a
     * call cut by its deadline while the answer arrived included. A fetch interrupted, as a refresh is when the keys
     * are closed, says nothing of the issuer: it logs no warning and leaves the last verdict as it was.
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class Fetch implements JWKSetSource<SecurityContext> {

        private final String issuer;

        private final RestOperations issuerRequests;

        private final Clock clock;

        private volatile @Nullable URI keysUri;

        private volatile Instant nextFetch = Instant.MIN;

        private volatile boolean unreachable;

        @Override
        public JWKSet getJWKSet(JWKSetCacheRefreshEvaluator refreshEvaluator, long currentTime,
                @Nullable SecurityContext context) throws KeySourceException {

            if (clock.instant().isBefore(nextFetch)) {
                throw new JWKSetUnavailableException(
                        "The issuer's keys were fetched less than " + BETWEEN_FETCHES.toSeconds() + " s ago");
            }
            try {
                var keys = JWKSet.parse(body(keysUri(), MediaType.APPLICATION_JSON, JWK_SET));
                unreachable = false;
                return keys;
            } catch (RuntimeException | ParseException e) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new JWKSetUnavailableException("The fetch of the issuer's keys was interrupted", e);
                }
                unreachable = unreachable(e);
                if (e instanceof HttpClientErrorException.NotFound || e instanceof HttpClientErrorException.Gone) {
                    keysUri = null;
                }
                throw failed(e);
            } finally {
                nextFetch = clock.instant().plus(BETWEEN_FETCHES);
            }
        }

        private URI keysUri() throws ParseException {

            var known = keysUri;
            if (known != null) {
                return known;
            }
            var discovery = URI
                    .create(StringUtils.trimTrailingCharacter(issuer, '/') + "/.well-known/openid-configuration");
            var configuration = JSONObjectUtils.parse(body(discovery, MediaType.APPLICATION_JSON));
            var named = JSONObjectUtils.getString(configuration, "issuer");
            if (!issuer.equals(named)) {
                throw new IllegalStateException("The discovery document names the issuer " + named + ", not " + issuer);
            }
            var keys = JSONObjectUtils.getURI(configuration, "jwks_uri");
            if (keys == null) {
                throw new IllegalStateException("The discovery document of " + issuer + " names no jwks_uri");
            }
            if (!discovery.getScheme().equals(keys.getScheme())) {
                throw new IllegalStateException(
                        "The discovery document of " + issuer + " names keys over another scheme: " + keys);
            }
            keysUri = keys;
            return keys;
        }

        private static boolean unreachable(Exception failure) {

            return failure instanceof HttpServerErrorException
                    || Stream.iterate((Throwable) failure, Objects::nonNull, Throwable::getCause)
                            .anyMatch(IOException.class::isInstance);
        }

        private String body(URI uri, MediaType... accepted) {

            var response = issuerRequests.exchange(RequestEntity.get(uri).accept(accepted).build(), String.class);
            return Objects.requireNonNullElse(response.getBody(), "");
        }

        private JWKSetUnavailableException failed(Exception cause) {

            log.warn(
                    "The identity provider's keys could not be fetched; the next attempt is in {} s at the earliest: {}",
                    BETWEEN_FETCHES.toSeconds(), NestedExceptionUtils.getMostSpecificCause(cause).toString());
            return new JWKSetUnavailableException("The issuer's keys could not be fetched", cause);
        }

        @Override
        public void close() {

            // nothing to release: every request is complete when it returns
        }
    }
}
