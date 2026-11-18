package dev.axonia.communication.adapter.out.chat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.axonia.communication.application.note.FormattedNote;
import dev.axonia.communication.application.note.FormattedNote.LineBreak;
import dev.axonia.communication.application.note.FormattedNote.Paragraph;
import dev.axonia.communication.application.note.FormattedNote.Strong;
import dev.axonia.communication.application.note.FormattedNote.Text;
import dev.axonia.communication.application.note.NoteParser;

import static org.assertj.core.api.Assertions.assertThat;

class SlackNoteTest {

    private static String slack(String markdown) {

        return SlackNote.of(NoteParser.parse(markdown), ChatPost.MAX_NOTE_LENGTH);
    }

    @Test
    void boldOrItalicsAcrossALineBreakAreWrappedLineByLine() {

        assertThat(slack("**a\nb** and *c\nd*")).isEqualTo("*a*\n*b* and _c_\n_d_");
        assertThat(SlackNote.of(
                new FormattedNote(List.of(new Paragraph(List.of(new Strong(List.of(new Text("a"), new LineBreak())))))),
                ChatPost.MAX_NOTE_LENGTH)).isEqualTo("*a*\n");
    }

    @Test
    void boldItalicsAndLinksUseMrkdwn() {

        assertThat(slack("**Bold** _it_ [x](https://e.com)")).isEqualTo("*Bold* _it_ <https://e.com|x>");
    }

    @Test
    void listsUseBulletsNestedCirclesAndNumbers() {

        assertThat(slack("- one\n  - two")).isEqualTo("• one\n    ◦ two");
        assertThat(slack("1. one\n2. two")).isEqualTo("1. one\n2. two");
    }

    @Test
    void aContinuedItemAlignsUnderItsText() {

        assertThat(slack("- one\ncont")).isEqualTo("• one\n  cont");
    }

    @Test
    void memberTextIsEscaped() {

        assertThat(slack("a < b & c")).isEqualTo("a &lt; b &amp; c");
        assertThat(slack("<https://evil.test|Click>")).doesNotContain("<https://evil.test|");
    }

    @Test
    void aLinkLabelCannotCloseOrSplitTheLink() {

        var rendered = slack("[a|b>c](https://e.com/?x=1&y=2)");

        assertThat(rendered).isEqualTo("<https://e.com/?x=1&amp;y=2|a∣b&gt;c>");
    }

    @Test
    void blocksAreSeparatedByABlankLine() {

        assertThat(slack("One\n\nTwo")).isEqualTo("One\n\nTwo");
    }

    @Test
    void aLongNoteIsCutBetweenBlocksAndEndsWithAnEllipsis() {

        var paragraph = "word ".repeat(59).strip();
        var rendered = SlackNote.of(NoteParser.parse((paragraph + "\n\n").repeat(20)), ChatPost.MAX_NOTE_LENGTH);

        assertThat(rendered).hasSizeLessThanOrEqualTo(ChatPost.MAX_NOTE_LENGTH).endsWith(paragraph + "…");
    }

    @Test
    void anOversizedBlockIsCutAtAWordBoundary() {

        var rendered = SlackNote.of(NoteParser.parse("alpha beta gamma delta epsilon"), 20);

        assertThat(rendered).isEqualTo("alpha beta gamma…");
    }

    @Test
    void anOversizedBlockWithoutSpacesIsCutHard() {

        assertThat(SlackNote.of(NoteParser.parse("x".repeat(50)), 10)).isEqualTo("xxxxxxxxx…");
    }

    @Test
    void aCutNeverLeavesAnOpenLink() {

        var rendered = SlackNote.of(NoteParser.parse("see [label](https://e.com) now"), 12);

        assertThat(rendered).isEqualTo("see…");
    }

    @Test
    void anItemHoldingTextAndListsKeepsAllOfIt() {

        assertThat(slack("- a\n\n  3. x\n\n  1. y\n\n  - b\n\n  7. q"))
                .isEqualTo("• a\n  3. x\n    1. y\n    2. b\n  7. q");
    }

    @Test
    void textBetweenAndAfterNestedListsKeepsItsPlace() {

        assertThat(slack("- a\n  - b\n\n  middle\n\n  - c\n\n  end"))
                .isEqualTo("• a\n    ◦ b\n  middle\n    ◦ c\n  end");
    }

    @Test
    void anItemWithNoTextKeepsItsMarker() {

        assertThat(slack("-\n  - b")).isEqualTo("• \n    ◦ b");
        assertThat(slack("- a\n\n-\n\n- c")).isEqualTo("• a\n• \n• c");
    }

    @Test
    void aCutClosesTheBoldItKeeps() {

        assertThat(SlackNote.of(NoteParser.parse("**bold words here and more**"), 12)).isEqualTo("*bold*…");
    }

    @Test
    void aCutNeverSplitsAnEscapedCharacter() {

        assertThat(SlackNote.of(NoteParser.parse("a & b & c & d"), 10)).isEqualTo("a &amp; b…");
    }

    @Test
    void aCutNeverSplitsACharacterOutsideTheBasicPlane() {

        assertThat(SlackNote.of(NoteParser.parse("😀".repeat(20)), 12)).isEqualTo("😀".repeat(5) + "…");
    }

    @Test
    void aLimitTooSmallForAnyTextLeavesTheEllipsis() {

        assertThat(SlackNote.of(NoteParser.parse("abc"), 1)).isEqualTo("…");
    }
}
