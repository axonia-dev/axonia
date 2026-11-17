package dev.axonia.roundup.domain;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuildTintsTest {

    @Test
    void aCommunityWithoutAnAccentIsTintedFromTheNeutralGrey() {

        assertThat(GuildTints.of(null)).isEqualTo(
                Map.of("accent", "#786a7e", "blockBackground", "#f6f5f6", "blockBorder", "#e9e7ea", "bandDark",
                        "#312835", "chipBackground", "#f3f2f3", "chipText", "#5e5264", "linkText", "#675a6c"));
    }

    @Test
    void aShortHexIsExpandedBeforeItIsMixed() {

        assertThat(GuildTints.of("#abc")).isEqualTo(
                Map.of("accent", "#aabbcc", "blockBackground", "#f9fafb", "blockBorder", "#f1f4f7", "bandDark",
                        "#40414d", "chipBackground", "#f7f9fa", "chipText", "#818b9a", "linkText", "#8f9bab"));
    }

    @Test
    void anUppercaseAccentComesOutLowercase() {

        assertThat(GuildTints.of("#DE3B78")).isEqualTo(
                Map.of("accent", "#de3b78", "blockBackground", "#fdf1f6", "blockBorder", "#fae0e9", "bandDark",
                        "#4f1a33", "chipBackground", "#fcedf3", "chipText", "#a5315f", "linkText", "#b83468"));
    }

    @Test
    void anAccentThatIsNotAHexFallsBackToTheNeutralGrey() {

        assertThat(GuildTints.of("violet")).containsEntry("accent", "#786a7e");
    }
}
