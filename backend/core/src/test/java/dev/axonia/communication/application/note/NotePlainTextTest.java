package dev.axonia.communication.application.note;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotePlainTextTest {

    private static String text(String markdown) {

        return NotePlainText.of(NoteParser.parse(markdown));
    }

    @Test
    void aParagraphThenAListIsSeparatedByABlankLine() {

        assertThat(text("Two incidents.\n\n- **Quota alerts** page the team\n- see [notes](https://e.com)"))
                .isEqualTo("Two incidents.\n\n- Quota alerts page the team\n- see notes (https://e.com)");
    }

    @Test
    void aLinkWhoseLabelIsItsUrlIsWrittenOnce() {

        assertThat(text("See https://example.com/a now")).isEqualTo("See https://example.com/a now");
    }

    @Test
    void lineBreaksAndItalicsAreKeptAsPlainWords() {

        assertThat(text("One *two*\nThree")).isEqualTo("One two\nThree");
    }

    @Test
    void numberedItemsCountAndANestedLevelIsIndentedTwoSpaces() {

        assertThat(text("1. a\n   - b\n   - c\n2. d")).isEqualTo("1. a\n  - b\n  - c\n2. d");
    }

    @Test
    void aContinuedItemLineAlignsUnderItsText() {

        assertThat(text("- item\ncontinued")).isEqualTo("- item\n  continued");
    }

    @Test
    void anEmptyNoteIsEmpty() {

        assertThat(text(null)).isEmpty();
    }

    @Test
    void anItemHoldingTextAndListsKeepsAllOfIt() {

        assertThat(text(NoteParserTest.MIXED_ITEM)).isEqualTo("- a\n  3. x\n  1. y\n  2. b\n  7. q");
    }

    @Test
    void textBetweenAndAfterNestedListsKeepsItsPlace() {

        assertThat(text("- a\n  - b\n\n  middle\n\n  - c\n\n  end")).isEqualTo("- a\n  - b\n  middle\n  - c\n  end");
    }

    @Test
    void anItemWithNoTextBeforeItsListKeepsItsMarker() {

        assertThat(text("-\n  - b")).isEqualTo("- \n  - b");
    }
}
