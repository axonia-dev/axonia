package dev.axonia.shared.api.secret;

import java.security.SecureRandom;
import java.util.Base64;

/** A high-entropy, URL-safe bearer secret carrying a caller-chosen prefix. Shared by every module that mints one. */
public final class OpaqueSecrets {

    private static final SecureRandom RANDOM = new SecureRandom();

    private OpaqueSecrets() {
    }

    public static String generate(String prefix) {

        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
