package dev.axonia.events.domain;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.axonia.RepositoryRoot;

import static org.assertj.core.api.Assertions.assertThat;

class AccentTintTest {

    private static final long COMMUNITY_ID = 7L;

    /**
     * Read from the repository rather than off the classpath: the shell is a runtime template resource, but what is
     * being pinned is its source text. It lives in this test's own module, so the path is resolved relative to the
     * module's working directory.
     */
    private static final Path SHELL_HTML = Path.of("src", "main", "resources", "templates", "mail", "_shell.html");

    /**
     * The app half of the same claim: the tokens {@code KindBadge.tsx} mixes over, resolved from the repository root
     * the same way {@code KindHueTest} reads the real {@code kind-hue.ts}.
     */
    private static final Path GLOBALS_CSS = RepositoryRoot.file("frontend/src/styles/globals.css");

    private static final Path KIND_BADGE_TSX = RepositoryRoot.file("frontend/src/components/patterns/KindBadge.tsx");

    /**
     * The mix arithmetic is plain per-channel rounding, so it is pinned exactly. The brand hue is the case that matters
     * most: it is what most communities will wear, and it clears the contrast floor at the unclamped 80% weight only
     * narrowly, so a change to either mix would move this value.
     */
    @Test
    void theBrandAccentMixesToTheSamePairTheAppWouldRender() {

        var tint = AccentTint.of("#de3b78", COMMUNITY_ID);

        assertThat(tint.background()).isEqualTo("#fae2eb");
        assertThat(tint.foreground()).isEqualTo("#b83468");
    }

    /**
     * A pale tenant accent is the case the clamp exists for: at the app's 80% weight the ink is a light yellow on an
     * almost-white tint, which the app survives (it has a theme, a hover state and a surrounding page) and a mail does
     * not. Asserted as a property rather than a hex, because the exact stopping weight is an implementation detail and
     * the requirement is legibility.
     */
    @Test
    void aPaleTenantAccentIsDarkenedUntilItIsLegible() {

        var tint = AccentTint.of("#ffe066", COMMUNITY_ID);

        assertThat(AccentTint.contrast(tint.foreground(), tint.background())).isGreaterThanOrEqualTo(4.5);
        assertThat(tint.foreground()).isNotEqualTo("#d3b859");
    }

    /**
     * Most of the eight {@link KindHue} palette hues clamp before reaching 4.5:1 at the unclamped 80% weight, so this
     * does not assert that the walk stops early - it asserts the actual requirement, that every hue ends up legible
     * against its own background regardless of how many weights the walk had to try.
     */
    @Test
    void everyFallbackHueEndsUpLegibleAgainstItsOwnTint() {

        for (var index = 0; index < 8; index++) {
            var tint = AccentTint.of(null, index);
            assertThat(AccentTint.contrast(tint.foreground(), tint.background())).as("hue for community %d", index)
                    .isGreaterThanOrEqualTo(4.5);
        }
    }

    @Test
    void aCommunityWithNoAccentFallsBackToItsHashedHue() {

        assertThat(AccentTint.of(null, 7L)).isEqualTo(AccentTint.of("#9b3cc4", 7L));
    }

    /**
     * {@code accent_color} is a free-text column with no format check, so a value that is not {@code #rrggbb} is a real
     * row shape rather than a hypothetical. It falls back rather than throwing: a malformed accent must not stop a
     * member's mail from going out.
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "red", "#abc", "#12345g", "1aa6a0"})
    void aMalformedAccentFallsBackRatherThanFailingTheRender(String accentColor) {

        assertThat(AccentTint.of(accentColor, 7L)).isEqualTo(AccentTint.of(null, 7L));
    }

    /**
     * Both mixes are computed against colours this class cannot see: the monogram is rendered over the mail card, and
     * its ink walks toward the card's own body colour. Nothing at runtime would notice the card changing underneath it
     * - the tint would simply be mixed over a surface it no longer sits on, and the "same colour, not a similar one"
     * guarantee would quietly become false. So the card's two values are asserted against the real {@code _shell.html},
     * matched with the surrounding declaration rather than as bare hexes, since {@code #ffffff} alone appears on every
     * avatar in the file and would pass while the card had moved on.
     */
    @Test
    void theSurfaceAndForegroundAreTheMailCardsOwn() throws Exception {

        var shell = Files.readString(SHELL_HTML);

        assertThat(shell).as("the white card the monogram sits on").contains(
                "background-color:" + AccentTint.SURFACE_HEX + "; border:1px solid #ede4e9; border-radius:16px;");
        assertThat(shell).as("the card's own body ink the monogram's ink walks toward")
                .contains("letter-spacing:-0.01em; color:" + AccentTint.FOREGROUND_HEX + ";");
    }

    /**
     * The other half of the same claim. The mail card is only one of the two surfaces these constants stand in for:
     * this class exists because mail cannot evaluate {@code KindBadge.tsx}'s {@code color-mix(in srgb, var(--k) 15%,
     * var(--surface))}, so it precomputes it - which is only the same colour while {@code --surface} and {@code
     * --foreground} still hold these values. Pinning the mail card alone would leave a frontend token change to drift
     * silently, which is exactly the asymmetry this test was added to remove.
     *
     * <p>
     * Anchored on the declaration, not the bare hex: {@code #ffffff} is also {@code --primary-foreground} and three
     * other tokens in the same block, and a bare-hex assertion would pass while {@code --surface} itself had moved. The
     * mix weights are asserted against {@code KindBadge.tsx} for the same reason - a 15/80 split that changed there
     * would leave this class computing a colour the app no longer renders.
     */
    @Test
    void theSurfaceAndForegroundAreStillTheFrontendTokensTheyPrecompute() throws Exception {

        assertThat(Files.readString(GLOBALS_CSS)).as("the app tokens KindBadge mixes over")
                .contains("--surface: " + AccentTint.SURFACE_HEX + ";")
                .contains("--foreground: " + AccentTint.FOREGROUND_HEX + ";");
        assertThat(Files.readString(KIND_BADGE_TSX)).as("the mix this class precomputes")
                .contains("color-mix(in_srgb,var(--k)_15%,var(--surface))")
                .contains("color-mix(in_srgb,var(--k)_80%,var(--foreground))");
    }
}
