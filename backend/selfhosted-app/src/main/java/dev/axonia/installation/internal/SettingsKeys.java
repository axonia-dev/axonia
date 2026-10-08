package dev.axonia.installation.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** The current settings key and the previous ones a deployment still supplies while it rotates. */
final class SettingsKeys {

    static final String CURRENT_VARIABLE = "AXONIA_SETTINGS_KEY";

    static final String PREVIOUS_VARIABLE = "AXONIA_SETTINGS_PREVIOUS_KEYS";

    private final SettingsKey current;

    private final List<SettingsKey> previous;

    private SettingsKeys(SettingsKey current, List<SettingsKey> previous) {

        this.current = current;
        this.previous = List.copyOf(previous);
    }

    static SettingsKeys parse(String currentKey, String previousKeys) {

        var current = SettingsKey.decode(currentKey).orElseThrow(() -> new IllegalStateException(
                CURRENT_VARIABLE + " must be 32 random bytes in base64, as openssl rand -base64 32 prints them."));
        var entries = previousKeys == null || previousKeys.isBlank() ? new String[0] : previousKeys.split(",");
        var previous = new ArrayList<SettingsKey>();
        for (var index = 0; index < entries.length; index++) {
            var position = index + 1;
            previous.add(
                    SettingsKey.decode(entries[index]).orElseThrow(() -> new IllegalStateException(PREVIOUS_VARIABLE
                            + " holds a key that is not 32 random bytes in base64 (entry " + position + ").")));
        }
        return new SettingsKeys(current, previous);
    }

    SettingsKey current() {

        return current;
    }

    Optional<SettingsKey> byId(String id) {

        return Stream.concat(Stream.of(current), previous.stream()).filter(key -> key.id().equals(id)).findFirst();
    }

    @Override
    public String toString() {

        return "SettingsKeys[current=" + current.id() + ", previous=" + previous.size() + "]";
    }
}
