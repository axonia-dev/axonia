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

class TeamsNoteTest {

    private static String teams(String markdown) {

        return TeamsNote.of(NoteParser.parse(markdown), ChatPost.MAX_NOTE_LENGTH);
    }

    @Test
    void boldOrItalicsAcrossALineBreakAreWrappedLineByLine() {

        assertThat(teams("**a\nb** and *c\nd*")).isEqualTo("**a**\n\n**b** and _c_\n\n_d_");
        assertThat(TeamsNote.of(
                new FormattedNote(List.of(new Paragraph(List.of(new Strong(List.of(new Text("a"), new LineBreak())))))),
                ChatPost.MAX_NOTE_LENGTH)).isEqualTo("**a**\n\n");
    }

    @Test
    void boldItalicsAndLinksUseCardMarkdown() {

        assertThat(teams("**Bold** _it_ [x](https://e.com)")).isEqualTo("**Bold** _it_ [x](https://e.com)");
    }

    @Test
    void listItemsAreSeparatedByCarriageReturnsAsMicrosoftDocuments() {

        assertThat(teams("- one\n- two")).isEqualTo("- one\r- two");
        assertThat(teams("1. one\n2. two")).isEqualTo("1. one\r2. two");
        assertThat(teams("- one\n  - two")).isEqualTo("- one\r  - two");
    }

    @Test
    void lineBreaksAndBlocksBecomeBlankLines() {

        assertThat(teams("a\nb")).isEqualTo("a\n\nb");
        assertThat(teams("a\n\nb")).isEqualTo("a\n\nb");
    }

    @Test
    void memberTextIsEscapedWithTheCardEscapeSet() {

        assertThat(teams("a \\* b `c` \\[d\\]")).isEqualTo("a \\* b \\`c\\` \\[d\\]");
        assertThat(teams("\\[Click\\](https://evil.test)")).startsWith("\\[Click\\]");
    }

    @Test
    void aYearOrDashAtLineStartStaysLiteral() {

        assertThat(teams("2025. was a year")).isEqualTo("2025\\. was a year");
    }

    @Test
    void aUrlCannotCloseItsLink() {

        assertThat(teams("[x](https://e.com/a_(b))")).isEqualTo("[x](https://e.com/a_%28b%29)");
    }

    @Test
    void aLongNoteIsCutBetweenBlocksAndEndsWithAnEllipsis() {

        var paragraph = "word ".repeat(59).strip();
        var rendered = TeamsNote.of(NoteParser.parse((paragraph + "\n\n").repeat(20)), ChatPost.MAX_NOTE_LENGTH);

        assertThat(rendered).hasSizeLessThanOrEqualTo(ChatPost.MAX_NOTE_LENGTH).endsWith(paragraph + "…");
    }

    @Test
    void anItemHoldingTextAndListsKeepsAllOfItAndItsNumbersLiteral() {

        assertThat(teams("- a\n\n  3. x\n\n  1. y\n\n  - b\n\n  7. q"))
                .isEqualTo("- a\n\n3\\. x\r  1. y\r  2. b\r  7\\. q");
    }

    @Test
    void textBetweenAndAfterNestedListsKeepsItsPlace() {

        assertThat(teams("- a\n  - b\n\n  middle\n\n  - c\n\n  end")).isEqualTo("- a\r  - b\r  middle\r  - c\r  end");
    }

    @Test
    void anItemWithNoTextKeepsItsMarker() {

        assertThat(teams("-\n  - b")).isEqualTo("- \r  - b");
        assertThat(teams("- a\n\n-\n\n- c")).isEqualTo("- a\r- \r- c");
    }

    @Test
    void aDashOrPlusAtLineStartStaysLiteral() {

        assertThat(teams("\\- one\n\\+ two")).isEqualTo("\\- one\n\n\\+ two");
    }

    @Test
    void aCutNeverLeavesAnOpenLink() {

        assertThat(TeamsNote.of(NoteParser.parse("words words [label](https://e.com) tail"), 20))
                .isEqualTo("words words…");
    }
}
