package dev.axonia.events.internal;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IcsWriterTest {

    @Test
    void aShortLineIsEmittedWholeWithCrlf() {

        assertThat(IcsWriter.line("SUMMARY", "Frontend Dojo")).isEqualTo("SUMMARY:Frontend Dojo\r\n");
    }

    @Test
    void aLongLineIsFoldedAtSeventyFiveOctetsWithALeadingSpaceOnEachContinuation() {

        var value = "x".repeat(200);

        var folded = IcsWriter.line("DESCRIPTION", value);

        var lines = folded.split("\r\n");
        for (var i = 0; i < lines.length; i++) {
            assertThat(lines[i].getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(75);
            if (i > 0) {
                assertThat(lines[i]).startsWith(" ");
            }
        }
        assertThat(String.join("",
                java.util.Arrays.stream(lines).map(l -> l.startsWith(" ") ? l.substring(1) : l).toList()))
                .isEqualTo("DESCRIPTION:" + value);
    }

    @Test
    void foldingCountsOctetsAndNeverSplitsAMultiByteCharacter() {

        var value = "é".repeat(80);

        var folded = IcsWriter.line("SUMMARY", value);

        var lines = folded.split("\r\n");
        for (var line : lines) {
            assertThat(line.getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(75);
        }
        assertThat(folded).doesNotContain("�");
        assertThat(String.join("",
                java.util.Arrays.stream(lines).map(l -> l.startsWith(" ") ? l.substring(1) : l).toList()))
                .isEqualTo("SUMMARY:" + value);
    }

    @Test
    void foldingBacktracksCorrectlyForThreeByteCharacters() {

        var value = "日".repeat(80);

        var folded = IcsWriter.line("SUMMARY", value);

        var lines = folded.split("\r\n");
        for (var line : lines) {
            assertThat(line.getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(75);
        }
        assertThat(folded).doesNotContain("�");
        assertThat(String.join("",
                java.util.Arrays.stream(lines).map(l -> l.startsWith(" ") ? l.substring(1) : l).toList()))
                .isEqualTo("SUMMARY:" + value);
    }

    @Test
    void foldingBacktracksCorrectlyForFourByteCharacters() {

        var value = "😀".repeat(80);

        var folded = IcsWriter.line("SUMMARY", value);

        var lines = folded.split("\r\n");
        for (var line : lines) {
            assertThat(line.getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(75);
        }
        assertThat(folded).doesNotContain("�");
        assertThat(String.join("",
                java.util.Arrays.stream(lines).map(l -> l.startsWith(" ") ? l.substring(1) : l).toList()))
                .isEqualTo("SUMMARY:" + value);
    }

    @Test
    void aLineOfExactlySeventyFiveOctetsStaysWhole() {

        var value = "x".repeat(67);

        assertThat(("SUMMARY:" + value).getBytes(StandardCharsets.UTF_8)).hasSize(75);
        assertThat(IcsWriter.line("SUMMARY", value)).isEqualTo("SUMMARY:" + value + "\r\n");
    }

    @Test
    void aLineOfSeventySixOctetsFolds() {

        var value = "x".repeat(68);

        assertThat(("SUMMARY:" + value).getBytes(StandardCharsets.UTF_8)).hasSize(76);
        assertThat(IcsWriter.line("SUMMARY", value).split("\r\n")).hasSize(2);
    }

    @Test
    void anEmptyValueYieldsJustTheNameAndColon() {

        assertThat(IcsWriter.line("SUMMARY", "")).isEqualTo("SUMMARY:\r\n");
    }

    /**
     * The injection this writer has to be incapable of, fed in raw rather than through any caller: a property is
     * terminated by a CRLF and by nothing else, so a value carrying one used to end its own property and turn
     * everything after it into calendar structure a subscriber trusts. The value here is exactly the shape a stored
     * join link would have taken, and the assertion is on the line COUNT, which no {@code contains} could have caught.
     */
    @Test
    void aRawCarriageReturnLineFeedInAValueCannotProduceASecondLine() {

        var injected = IcsWriter.line("CONFERENCE", "https://x.example/a\r\nDESCRIPTION:x");

        assertThat(injected).isEqualTo("CONFERENCE:https://x.example/aDESCRIPTION:x\r\n");
        assertThat(injected.split("\r\n")).hasSize(1);
    }

    /** A lone LF is the same injection in every consumer that unfolds leniently, and the same non-answer here. */
    @Test
    void aLoneLineFeedInAValueIsDroppedRatherThanEmitted() {

        assertThat(IcsWriter.line("SUMMARY", "a\nb")).isEqualTo("SUMMARY:ab\r\n");
    }

    /** Every other control character goes the same way, so nothing has to enumerate which ones a client mishandles. */
    @Test
    void anyOtherControlCharacterIsDroppedToo() {

        assertThat(IcsWriter.line("SUMMARY", "a\tb\u000Bc")).isEqualTo("SUMMARY:abc\r\n");
    }

    /**
     * Stripping happens before folding, so a value that only crosses 75 octets because of its control characters is not
     * folded on their account, and a folded line's own CRLFs are the writer's rather than the value's.
     */
    @Test
    void strippingHappensBeforeFoldingSoAFoldedLineCarriesOnlyTheWritersOwnBreaks() {

        var value = "x".repeat(100).replace("xxxxx", "xxxx\n");

        var folded = IcsWriter.line("DESCRIPTION", value);

        assertThat(folded.split("\r\n")).allSatisfy(line -> assertThat(line).doesNotContain("\n"));
        assertThat(folded.replace("\r\n ", "").replace("\r\n", "")).isEqualTo("DESCRIPTION:" + "x".repeat(80));
    }

    @Test
    void escapesTheFourCharactersTheFormatReserves() {

        assertThat(IcsWriter.escapeText("a,b;c\\d\ne")).isEqualTo("a\\,b\\;c\\\\d\\ne");
    }

    @Test
    void escapesACarriageReturnLineFeedPairAsALineFeed() {

        assertThat(IcsWriter.escapeText("a\r\nb")).isEqualTo("a\\nb");
    }

    @Test
    void escapesALoneCarriageReturnAsALineFeed() {

        assertThat(IcsWriter.escapeText("a\rb")).isEqualTo("a\\nb");
    }

    @Test
    void rendersAnInstantInUtcBasicFormat() {

        assertThat(IcsWriter.utc(Instant.parse("2026-08-18T16:00:00Z"))).isEqualTo("20260818T160000Z");
    }

    @Test
    void rendersACivilDateInBasicFormatWithNoTimeAndNoZone() {

        assertThat(IcsWriter.date(LocalDate.of(2026, 8, 13))).isEqualTo("20260813");
    }
}
