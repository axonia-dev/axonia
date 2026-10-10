package dev.axonia.shared.api.secret;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Hashes a high-entropy random bearer secret for O(1) lookup by hash. Shared by every module that stores a bearer by
 * hash rather than in the clear - identity's personal access tokens and SCIM tokens among them.
 */
public final class TokenHasher {

    private TokenHasher() {
    }

    public static String sha256Hex(String plaintext) {
        return sha256Hex(plaintext.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Hex(byte[] bytes) {
        return HexFormat.of().formatHex(sha256(bytes));
    }

    public static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
