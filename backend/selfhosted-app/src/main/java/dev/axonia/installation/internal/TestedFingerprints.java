package dev.axonia.installation.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * The fingerprint of the values a save was tested with, stored as {@code v1:<key id>:<HMAC>} so a reader of the
 * database cannot test password guesses against it.
 */
@Component
class TestedFingerprints {

    private static final String PURPOSE = "axonia/settings/tested-fingerprint";

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final SettingsKeys keys;

    TestedFingerprints(SettingsKeys keys) {

        this.keys = keys;
    }

    String digest(String fingerprint) {

        return digest(keys.current(), fingerprint);
    }

    boolean matches(String stored, String fingerprint) {

        var parts = stored.split(":", 3);
        return parts.length == 3 && SettingsCipher.VERSION.equals(parts[0])
                && keys.byId(parts[1]).map(key -> sameText(digest(key, fingerprint), stored)).orElse(false);
    }

    /** The digest under the current key, when the stored one is another key's and still matches. */
    Optional<String> rekeyed(String stored, String fingerprint) {

        if (stored.startsWith(SettingsCipher.VERSION + ":" + keys.current().id() + ":")
                || !matches(stored, fingerprint)) {
            return Optional.empty();
        }
        return Optional.of(digest(fingerprint));
    }

    private static String digest(SettingsKey key, String fingerprint) {

        var mac = Hmac.sign(key.derive(PURPOSE), fingerprint.getBytes(StandardCharsets.UTF_8));
        return String.join(":", SettingsCipher.VERSION, key.id(), ENCODER.encodeToString(mac));
    }

    private static boolean sameText(String a, String b) {

        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
