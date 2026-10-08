package dev.axonia.communication.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.axonia.communication.MessageType;

/**
 * Signs and verifies the bearer credential a mail footer's unsubscribe link carries: an HMAC-SHA256 over
 * {@code userId + ":" + organizationId + ":" + type.code()}, so a leaked link can flip one member's preference for one
 * message type in one Organization and nothing else - not read anything, not act as anybody, not touch any other
 * community the same person belongs to. See {@link UnsubscribeClaim} for why the Organization rides along.
 *
 * <p>
 * Both the payload and the signature are base64url-encoded before they become the token: an unencoded payload would put
 * the recipient's user id and Organization id in plaintext, forever, in every mail body a copy of this link ends up in
 * - a forwarded mail, a list archive, a corporate mail scanner. This is not a security boundary (the signature is what
 * verification actually relies on, and there is nothing sensitive about the fact that some user id exists), but the
 * disclosure costs nothing to remove. {@code verify} recomputes the HMAC over whatever payload the token claims and
 * compares it to the carried signature with {@link MessageDigest#isEqual}, a constant-time comparison - a plain
 * {@code equals} would let an attacker recover the correct signature one byte at a time from how long each wrong guess
 * takes to be rejected, the classic timing-oracle shape.
 *
 * <p>
 * {@code sign} refuses a {@code userId} or {@code organizationId} containing the field separator: {@code
 * parseClaim}'s fixed three-way split would otherwise silently misparse the payload, and a token that fails to verify
 * is a safe failure (a denial) where a wrongly parsed one - flipping a preference for the wrong field values - would
 * not be.
 *
 * <p>
 * There is no expiry. A token this codebase minted a year ago still unsubscribes cleanly if somebody finally clicks it,
 * and that is the point of "unsubscribe", not a gap in it: the one thing a leaked or stale token can ever do is
 * something the member would have been entitled to do anyway.
 */
@Component
public class UnsubscribeTokens {

    private static final char FIELD_SEPARATOR = ':';
    private static final char TOKEN_SEPARATOR = '.';
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final CommunicationProperties properties;

    UnsubscribeTokens(CommunicationProperties properties) {

        this.properties = properties;
    }

    /**
     * A URL-safe token: {@code userId}, {@code organizationId} and {@code type} encoded (not encrypted - only signed)
     * against tampering.
     */
    public String sign(String userId, String organizationId, MessageType type) {

        requireNoSeparator(userId, "userId");
        requireNoSeparator(organizationId, "organizationId");
        var payloadBytes = payloadOf(userId, organizationId, type).getBytes(StandardCharsets.UTF_8);
        return ENCODER.encodeToString(payloadBytes) + TOKEN_SEPARATOR + ENCODER.encodeToString(hmac(payloadBytes));
    }

    /** Empty for anything not signed by this exact secret, moments ago or years ago, tampered or merely malformed. */
    public Optional<UnsubscribeClaim> verify(String token) {

        var separator = token.lastIndexOf(TOKEN_SEPARATOR);
        if (separator < 0) {
            return Optional.empty();
        }
        var payload = decode(token.substring(0, separator));
        var signature = decode(token.substring(separator + 1));
        if (payload.isEmpty() || signature.isEmpty() || !matches(payload.get(), signature.get())) {
            return Optional.empty();
        }
        return parseClaim(new String(payload.get(), StandardCharsets.UTF_8));
    }

    private boolean matches(byte[] payload, byte[] signature) {

        return MessageDigest.isEqual(hmac(payload), signature);
    }

    private static Optional<byte[]> decode(String encoded) {

        try {
            return Optional.of(DECODER.decode(encoded));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String payloadOf(String userId, String organizationId, MessageType type) {

        return userId + FIELD_SEPARATOR + organizationId + FIELD_SEPARATOR + type.code();
    }

    private static void requireNoSeparator(String value, String fieldName) {

        if (value.indexOf(FIELD_SEPARATOR) >= 0) {
            throw new IllegalArgumentException(fieldName + " must not contain '" + FIELD_SEPARATOR + "'");
        }
    }

    private static Optional<UnsubscribeClaim> parseClaim(String payload) {

        var parts = payload.split(String.valueOf(FIELD_SEPARATOR), 3);
        if (parts.length != 3) {
            return Optional.empty();
        }
        return MessageType.parse(parts[2]).map(type -> new UnsubscribeClaim(parts[0], parts[1], type));
    }

    private byte[] hmac(byte[] payload) {

        return HmacSha256.sign(properties.unsubscribeSecret().getBytes(StandardCharsets.UTF_8), payload);
    }
}
