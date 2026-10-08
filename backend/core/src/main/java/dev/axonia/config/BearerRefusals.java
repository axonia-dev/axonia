package dev.axonia.config;

import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Says in the log why a bearer token was refused, at WARN, once a minute per reason: the reason, and the issuer and
 * audience the token claims, read without verifying it and only for the log. The token itself is never logged, and the
 * answer to the request is left as it is.
 *
 * <p>
 * A token refused only because it expired is routine, since every client renews around it, and is not said here; Spring
 * logs it at DEBUG. Every other refusal (audience, issuer, signature or unknown key, type, an application's token, a
 * malformed one, or one used before its time) is a misconfiguration or an attack, and is.
 *
 * <p>
 * Numbers do not make a new reason, so a token used a second later is the same reason. At most {@link #REASONS} reasons
 * are remembered in a minute, and once they are all taken that is said once and other reasons wait for the minute to
 * end: a stream of tokens refused for ever new reasons neither floods the log nor grows memory.
 */
@Slf4j
@RequiredArgsConstructor
final class BearerRefusals {

    static final Duration WINDOW = Duration.ofMinutes(1);

    static final int REASONS = 32;

    private static final int LONGEST_VALUE = 200;

    /** How Spring's {@code JwtTimestampValidator} describes an expired token. */
    private static final String EXPIRED = "Jwt expired at ";

    private static final Pattern NUMBERS = Pattern.compile("\\d+");

    /**
     * Every control (C0, C1, DEL), format (bidi overrides, zero-width), unpaired surrogate, line and paragraph
     * separator character of the whole of Unicode: none can break or reorder a log line, whatever reads it.
     */
    private static final Pattern UNPRINTABLE = Pattern.compile("[\\p{Cc}\\p{Cf}\\p{Cs}\\p{Zl}\\p{Zp}]");

    private final Clock clock;

    private final Map<String, Instant> logged = new HashMap<>();

    private boolean saidFull;

    void refused(Authentication bearer, AuthenticationException refusal) {

        if (expiredOnly(refusal)) {
            return;
        }
        var reason = printable(reasonOf(refusal));
        if (!firstInWindow(NUMBERS.matcher(reason).replaceAll("#"))) {
            return;
        }
        var claims = claimsOf(bearer);
        log.warn("Refused a bearer token: {} (iss={}, aud={})", reason,
                claims == null ? "unreadable" : printable(claims.getIssuer()),
                claims == null ? "unreadable" : printable(String.valueOf(claims.getAudience())));
    }

    private synchronized boolean firstInWindow(String reason) {

        var now = clock.instant();
        logged.values().removeIf(at -> !now.isBefore(at.plus(WINDOW)));
        if (logged.containsKey(reason)) {
            return false;
        }
        if (logged.size() >= REASONS) {
            if (!saidFull) {
                saidFull = true;
                log.warn(
                        "Refused bearer tokens for {} reasons within a minute; other reasons are not logged until it ends",
                        REASONS);
            }
            return false;
        }
        saidFull = false;
        logged.put(reason, now);
        return true;
    }

    private static boolean expiredOnly(AuthenticationException refusal) {

        return refusal.getCause() instanceof JwtValidationException invalid
                && invalid.getErrors().stream().map(OAuth2Error::getDescription)
                        .allMatch(description -> description != null && description.startsWith(EXPIRED));
    }

    private static String reasonOf(AuthenticationException refusal) {

        if (refusal.getCause() instanceof JwtValidationException invalid) {
            var descriptions = invalid.getErrors().stream().map(OAuth2Error::getDescription).filter(Objects::nonNull)
                    .collect(Collectors.joining("; "));
            if (!descriptions.isEmpty()) {
                return descriptions;
            }
        }
        return Objects.requireNonNullElse(refusal.getMessage(), refusal.getClass().getSimpleName());
    }

    private static @Nullable JWTClaimsSet claimsOf(Authentication bearer) {

        if (!(bearer instanceof BearerTokenAuthenticationToken token)) {
            return null;
        }
        try {
            return JWTParser.parse(token.getToken()).getJWTClaimsSet();
        } catch (ParseException e) {
            return null;
        }
    }

    static String printable(@Nullable String value) {

        if (value == null) {
            return "none";
        }
        var shown = UNPRINTABLE.matcher(value).replaceAll("?");
        return shown.codePointCount(0, shown.length()) > LONGEST_VALUE
                ? shown.substring(0, shown.offsetByCodePoints(0, LONGEST_VALUE)) + "..."
                : shown;
    }
}
