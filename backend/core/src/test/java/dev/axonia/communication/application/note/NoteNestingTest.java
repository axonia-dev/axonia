package dev.axonia.communication.application.note;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class NoteNestingTest {

    private static String html(String markdown) {

        return NoteHtml.canonical(NoteParser.parse(markdown));
    }

    @Test
    void twoHundredMarksInOneRunStillParse() {

        var markdown = "*a ".repeat(100) + "b" + " c*".repeat(100);

        assertThat(NoteNesting.isTooDeep(markdown)).isFalse();
        assertThat(html(markdown)).startsWith("<p><em>a <em>a ");
    }

    @Test
    void moreThanTwoHundredMarksInOneRunReadAsPlainText() {

        var markdown = " *a\n" + "*a ".repeat(100) + "b" + " c*".repeat(100) + "  \n\n\t[x](https://a.com)  \n";

        assertThat(NoteNesting.isTooDeep(markdown)).isTrue();
        assertThat(html(markdown))
                .isEqualTo("<p>*a<br>" + "*a ".repeat(100) + "b" + " c*".repeat(100) + "</p><p>[x](https://a.com)</p>");
    }

    @Test
    void marksCountPerRunBetweenBlankLines() {

        var run = "**a** [b](https://b.com) _c_\n".repeat(20);

        assertThat(NoteNesting.isTooDeep(run + "\n" + run)).isFalse();
        assertThat(NoteNesting.isTooDeep(run + run)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"snake_case_Name_2_x", "UPPER_CASE", "v2_3"})
    void anUnderscoreInsideAWordIsNoMark(String word) {

        assertThat(NoteNesting.isTooDeep((word + " ").repeat(201))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"_a", "a_", "a _b", "a_ b", "{_a", "a_{", "^_a", "a_^", "/_a", "a_:", "]"})
    void anUnderscoreAtAWordEdgeIsAMark(String text) {

        assertThat(NoteNesting.isTooDeep((text + " ").repeat(200) + text)).isTrue();
    }

    @Test
    void listsNestThirtyTwoLevelsBeforeReadingAsPlainText() {

        assertThat(NoteNesting.isTooDeep("- ".repeat(32) + "x")).isFalse();
        assertThat(NoteNesting.isTooDeep("- ".repeat(33) + "x")).isTrue();
        assertThat(NoteNesting.isTooDeep(" ".repeat(64) + "x")).isFalse();
        assertThat(NoteNesting.isTooDeep(" ".repeat(66) + "x")).isTrue();
        assertThat(NoteNesting.isTooDeep("\t".repeat(16) + " 1. x")).isTrue();
        assertThat(NoteNesting.isTooDeep("  \t".repeat(16) + "x")).isFalse();
        assertThat(NoteNesting.isTooDeep("1) ".repeat(33))).isTrue();
        assertThat(NoteNesting.isTooDeep("-x ".repeat(33))).isFalse();
    }

    @Test
    void aSpacedRuleStaysTheTextTyped() {

        assertThat(html("a\n_ _ _\n\n  ***  \n-\t-\t-")).isEqualTo("<p>a<br>_ _ _</p><p>***<br>-\t-\t-</p>");
    }
}
