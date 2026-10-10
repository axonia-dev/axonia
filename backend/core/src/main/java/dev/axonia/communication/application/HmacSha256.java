package dev.axonia.communication.application;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import lombok.experimental.UtilityClass;

/**
 * Wraps {@code javax.crypto.Mac}'s checked exceptions, isolated into its own tiny class so that
 * {@link UnsubscribeTokens}'s own token-format and verification logic - what {@code UnsubscribeTokensTest} actually
 * exercises - stays legible on its own. {@code algorithm} is a parameter, not the hardcoded {@code "HmacSHA256"} the
 * one production call site always passes, purely so a test can drive the catch block with a name no JCA provider
 * registers: {@code HmacSHA256} itself is mandated by the Java Security Standard Algorithm Names spec to always be
 * available, so nothing a test controls can make the real call site's catch fire on its own.
 *
 * <p>
 * {@code @UtilityClass} (as {@code OrganizationScope} uses) rather than a hand-written private constructor: Lombok
 * marks the generated one {@code @lombok.Generated}, which JaCoCo's coverage analysis already skips, so there is no
 * uncoverable constructor line to work around either.
 */
@UtilityClass
class HmacSha256 {

    private static final String ALGORITHM = "HmacSHA256";

    static byte[] sign(byte[] secret, byte[] payload) {

        return sign(ALGORITHM, secret, payload);
    }

    static byte[] sign(String algorithm, byte[] secret, byte[] payload) {

        try {
            var mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secret, algorithm));
            return mac.doFinal(payload);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(algorithm + " is unavailable on this JVM", e);
        }
    }
}
