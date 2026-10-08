package dev.axonia.installation.internal;

import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/** One settings key: 32 bytes the deployment supplies, named by an identifier derived from it. */
final class SettingsKey {

    static final int BYTES = 32;

    private static final String ID_PURPOSE = "axonia/settings/key-id";

    private static final int ID_BYTES = 4;

    private final byte[] material;

    private final String id;

    private SettingsKey(byte[] material) {

        this.material = material.clone();
        this.id = HexFormat.of().formatHex(Hkdf.derive(material, ID_PURPOSE, ID_BYTES));
    }

    static Optional<SettingsKey> decode(String base64) {

        if (base64 == null) {
            return Optional.empty();
        }
        try {
            var material = Base64.getDecoder().decode(base64.strip());
            return material.length == BYTES ? Optional.of(new SettingsKey(material)) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    String id() {

        return id;
    }

    /** A key of its own for each purpose, so no two uses of the settings key ever share one. */
    byte[] derive(String purpose) {

        return Hkdf.derive(material, purpose, BYTES);
    }

    @Override
    public String toString() {

        return "SettingsKey[" + id + "]";
    }
}
