package dev.axonia.events.domain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.axonia.RepositoryRoot;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A community that has chosen no accent must wear the same colour in a mail that it wears in the app, which is the
 * whole reason this hash is ported rather than replaced with any other deterministic default. The parameterized cases
 * pin the arithmetic; {@link #theEightHuesMatchTheFrontendsOwnPaletteInOrder()} pins the palette and order against the
 * real frontend file, so deleting or reordering a hue there fails here rather than silently drifting.
 */
class KindHueTest {

    /**
     * Read from the repository rather than from the classpath: Vite rewrites {@code new URL(..., import.meta.url)} into
     * an asset import, and the mirror-image trap here is assuming a frontend source file is a backend test resource. It
     * is not, so the path is resolved from the repository root.
     */
    private static final Path KIND_HUE_TS = RepositoryRoot.file("frontend/src/lib/kind-hue.ts");

    @ParameterizedTest
    @CsvSource({"1,#6e3ceb", "7,#9b3cc4", "42,#3b82de", "8,#de3b78"})
    void aKeyHashesToTheSameHueTheAppWouldPick(String key, String expected) {

        assertThat(KindHue.of(key)).isEqualTo(expected);
    }

    @Test
    void anEmptyKeyIsTheFirstHueRatherThanAnIndexError() {

        assertThat(KindHue.of("")).isEqualTo("#de3b78");
    }

    @Test
    void theEightHuesMatchTheFrontendsOwnPaletteInOrder() throws Exception {

        var source = Files.readString(KIND_HUE_TS);
        var hues = List.of("#de3b78", "#6e3ceb", "#d9871f", "#1aa6a0", "#f2603c", "#1f9d63", "#3b82de", "#9b3cc4");

        assertThat(source).contains(hues);
        assertThat(hues.stream().map(KindHue::indexOfHue).toList()).containsExactly(0, 1, 2, 3, 4, 5, 6, 7);

        var positions = hues.stream().map(source::indexOf).toList();
        for (int i = 1; i < positions.size(); i++) {
            assertThat(positions.get(i)).isGreaterThan(positions.get(i - 1));
        }
    }
}
