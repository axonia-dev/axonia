package dev.axonia.shared.api.colour;

import java.util.List;

/**
 * The shell's avatar trio. A person's colour hangs off {@code String#hashCode}, which is specified, so somebody who
 * appears in two cards wears one colour in both.
 */
public final class PersonTints {

    private static final List<String> AVATAR_TINTS = List.of("#de3b78", "#6e3ceb", "#1f9d63");

    private PersonTints() {
    }

    public static String of(String name) {

        return AVATAR_TINTS.get(Math.floorMod(name.hashCode(), AVATAR_TINTS.size()));
    }
}
