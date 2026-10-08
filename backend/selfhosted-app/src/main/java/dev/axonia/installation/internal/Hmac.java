package dev.axonia.installation.internal;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import lombok.experimental.UtilityClass;

/** HMAC-SHA256; the algorithm is a parameter only so a test can reach the catch. */
@UtilityClass
class Hmac {

    private static final String ALGORITHM = "HmacSHA256";

    static byte[] sign(byte[] key, byte[] message) {

        return sign(ALGORITHM, key, message);
    }

    static byte[] sign(String algorithm, byte[] key, byte[] message) {

        try {
            var mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
            return mac.doFinal(message);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(algorithm + " is unavailable on this JVM", e);
        }
    }
}
