package dev.axonia.installation.internal;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

import org.springframework.stereotype.Component;

/** Seals a stored secret as {@code v1:<key id>:<base64url of nonce and AES-256-GCM ciphertext>}. */
@Component
class SettingsCipher {

    static final String VERSION = "v1";

    private static final String PURPOSE = "axonia/settings/seal";

    private static final int NONCE_BYTES = 12;

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final SettingsKeys keys;

    private final SecureRandom random = new SecureRandom();

    SettingsCipher(SettingsKeys keys) {

        this.keys = keys;
    }

    String seal(SecretField field, String plaintext) {

        var key = keys.current();
        var nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        var ciphertext = AesGcm.STANDARD.encrypt(key.derive(PURPOSE), nonce, field.associatedData(),
                plaintext.getBytes(StandardCharsets.UTF_8));
        var payload = Arrays.copyOf(nonce, NONCE_BYTES + ciphertext.length);
        System.arraycopy(ciphertext, 0, payload, NONCE_BYTES, ciphertext.length);
        return String.join(":", VERSION, key.id(), ENCODER.encodeToString(payload));
    }

    /** Empty when the envelope is malformed, names a key this installation does not hold, or was altered. */
    Optional<String> open(SecretField field, String envelope) {

        var parts = envelope.split(":", 3);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            return Optional.empty();
        }
        return keys.byId(parts[1]).flatMap(key -> decrypt(key, field, parts[2]));
    }

    /** Whether a key id names a key this installation holds other than its current one, so it can re-seal from it. */
    boolean holdsAsPrevious(String keyId) {

        return keyId != null && !keyId.equals(keys.current().id()) && keys.byId(keyId).isPresent();
    }

    boolean sealedWithCurrentKey(String envelope) {

        return envelope.startsWith(VERSION + ":" + keys.current().id() + ":");
    }

    /** The envelope sealed again under the current key, when it is readable and sealed under another one. */
    Optional<String> resealed(SecretField field, String envelope) {

        if (sealedWithCurrentKey(envelope)) {
            return Optional.empty();
        }
        return open(field, envelope).map(plaintext -> seal(field, plaintext));
    }

    private static Optional<String> decrypt(SettingsKey key, SecretField field, String encoded) {

        byte[] payload;
        try {
            payload = Base64.getUrlDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (payload.length <= NONCE_BYTES) {
            return Optional.empty();
        }
        return AesGcm.STANDARD
                .decrypt(key.derive(PURPOSE), Arrays.copyOf(payload, NONCE_BYTES), field.associatedData(),
                        Arrays.copyOfRange(payload, NONCE_BYTES, payload.length))
                .map(plaintext -> new String(plaintext, StandardCharsets.UTF_8));
    }
}
