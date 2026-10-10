package dev.axonia.events.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import lombok.experimental.UtilityClass;

/**
 * The bearer a calendar subscription URL carries. Minted once, handed to the member once, and never stored: only its
 * SHA-256 reaches the database, so a dump of {@code calendar_subscription} yields no working feed URL.
 *
 * <p>
 * A plain hash rather than a slow KDF is correct here and only here: the input is 32 bytes of {@link SecureRandom}
 * output rather than a human-chosen secret, so there is no dictionary to run and nothing for a work factor to buy.
 */
@UtilityClass
public class SubscriptionTokens {

    private static final int TOKEN_BYTES = 32;
    private static final String ALGORITHM = "SHA-256";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    public String mint() {

        var bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    public String hash(String token) {

        return hash(ALGORITHM, token);
    }

    /**
     * {@code algorithm} is a parameter, not the hardcoded {@code "SHA-256"} the one production call site always passes,
     * purely so a test can drive the catch block with a name no JCA provider registers - the same shape, and for the
     * same reason, as {@code communication.application.HmacSha256}. {@code SHA-256} itself is mandated by the Java
     * Security Standard Algorithm Names spec to always be available, so nothing a test controls can make the real call
     * site's catch fire on its own.
     */
    String hash(String algorithm, String token) {

        try {
            var digest = MessageDigest.getInstance(algorithm);
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " is required by every JVM this runs on", e);
        }
    }
}
