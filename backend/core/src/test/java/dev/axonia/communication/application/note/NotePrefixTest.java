package dev.axonia.communication.application.note;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.axonia.communication.application.note.FormattedNote.Block;
import dev.axonia.communication.application.note.FormattedNote.BulletList;
import dev.axonia.communication.application.note.FormattedNote.Emphasis;
import dev.axonia.communication.application.note.FormattedNote.Inline;
import dev.axonia.communication.application.note.FormattedNote.Item;
import dev.axonia.communication.application.note.FormattedNote.LineBreak;
import dev.axonia.communication.application.note.FormattedNote.Link;
import dev.axonia.communication.application.note.FormattedNote.NumberedList;
import dev.axonia.communication.application.note.FormattedNote.Paragraph;
import dev.axonia.communication.application.note.FormattedNote.Strong;
import dev.axonia.communication.application.note.FormattedNote.Text;

import static org.assertj.core.api.Assertions.assertThat;

class NotePrefixTest {

    private static final Link LINK = new Link("https://e.com", List.of(new Text("label")));

    private static Paragraph paragraph(Inline... inlines) {

        return new Paragraph(List.of(inlines));
    }

    private static Item item(String text) {

        return new Item(List.of(paragraph(new Text(text))));
    }

    private static Block prefix(Block block, int budget) {

        return NotePrefix.of(block, budget).orElse(null);
    }

    @Test
    void textIsCutAtTheLastWordThatFits() {

        var block = paragraph(new Text("alpha beta gamma"));

        assertThat(prefix(block, 12)).isEqualTo(paragraph(new Text("alpha beta")));
        assertThat(prefix(block, 10)).isEqualTo(paragraph(new Text("alpha beta")));
        assertThat(prefix(block, 16)).isEqualTo(block);
    }

    @Test
    void aWordLongerThanTheBudgetIsCutInsideIt() {

        assertThat(prefix(paragraph(new Text("x".repeat(20))), 5)).isEqualTo(paragraph(new Text("xxxxx")));
    }

    @Test
    void aCutNeverSplitsACharacterOutsideTheBasicPlane() {

        var cut = (Paragraph) prefix(paragraph(new Text("😀".repeat(20))), 12);

        assertThat(cut).isEqualTo(paragraph(new Text("😀".repeat(12))));
    }

    @Test
    void nothingFitsIsNoBlock() {

        assertThat(NotePrefix.of(paragraph(new Text("abc")), 0)).isEmpty();
        assertThat(NotePrefix.of(paragraph(new Text(" abc def")), 1)).isEmpty();
        assertThat(NotePrefix.of(new BulletList(List.of(item("a"))), 0)).isEmpty();
    }

    @Test
    void aLinkIsKeptWholeOrNotAtAll() {

        var block = paragraph(new Text("see "), LINK, new Text(" now"));

        assertThat(prefix(block, 6)).isEqualTo(paragraph(new Text("see ")));
        assertThat(prefix(block, 9)).isEqualTo(paragraph(new Text("see "), LINK));
    }

    @Test
    void boldAndItalicsKeepTheirWrapperAroundWhatFits() {

        assertThat(prefix(paragraph(new Strong(List.of(new Text("bold words here")))), 9))
                .isEqualTo(paragraph(new Strong(List.of(new Text("bold")))));
        assertThat(prefix(paragraph(new Text("ab"), new Emphasis(List.of(new Text("cd")))), 2))
                .isEqualTo(paragraph(new Text("ab")));
        assertThat(prefix(paragraph(new Text("ab"), new Emphasis(List.of(new Text("cd")))), 3))
                .isEqualTo(paragraph(new Text("ab"), new Emphasis(List.of(new Text("c")))));
    }

    @Test
    void aLineBreakCountsOne() {

        var block = paragraph(new Text("a"), new LineBreak(), new Text("b"));

        assertThat(prefix(block, 2)).isEqualTo(paragraph(new Text("a"), new LineBreak()));
        assertThat(prefix(block, 1)).isEqualTo(paragraph(new Text("a")));
    }

    @Test
    void aListKeepsTheItemsThatFitAndStopsAtTheFirstThatDoesNot() {

        var tooLong = new Item(List.of(paragraph(LINK)));

        assertThat(prefix(new BulletList(List.of(item("ab"), tooLong, item("c"))), 3))
                .isEqualTo(new BulletList(List.of(item("ab"))));
        assertThat(prefix(new BulletList(List.of(item("ab"), item("cd"))), 2))
                .isEqualTo(new BulletList(List.of(item("ab"))));
    }

    @Test
    void anEmptyItemAndAnItemHoldingOnlyAListAreKept() {

        var block = new BulletList(
                List.of(new Item(List.of()), new Item(List.of(new NumberedList(List.of(item("a")))))));

        assertThat(prefix(block, 1)).isEqualTo(block);
    }

    @Test
    void aNestedListIsKeptAsFarAsItFits() {

        var block = new NumberedList(
                List.of(new Item(List.of(paragraph(new Text("a")), new BulletList(List.of(item("b")))))));

        assertThat(prefix(block, 2)).isEqualTo(block);
        assertThat(prefix(block, 1)).isEqualTo(new NumberedList(List.of(item("a"))));
    }

    @Test
    void textAfterANestedListIsKeptOnlyOnceTheListFitsWhole() {

        var block = new BulletList(List.of(new Item(List.of(paragraph(new Text("a")),
                new BulletList(List.of(item("b"), item("c"))), paragraph(new Text("d"))))));

        assertThat(prefix(block, 4)).isEqualTo(block);
        assertThat(prefix(block, 2)).isEqualTo(new BulletList(
                List.of(new Item(List.of(paragraph(new Text("a")), new BulletList(List.of(item("b"))))))));
    }

    @Test
    void theLengthCountsEveryCharacterOfTextAndEveryBreak() {

        var block = new BulletList(List.of(new Item(List.of(
                paragraph(new Text("😀b"), new LineBreak(), new Strong(List.of(new Text("c"))),
                        new Emphasis(List.of(new Text("d"))), LINK),
                new NumberedList(List.of(item("ef"))), paragraph(new Text("g"))))));

        assertThat(NotePrefix.lengthOf(block)).isEqualTo(13);
        assertThat(NotePrefix.lengthOf(paragraph(new Text("abc")))).isEqualTo(3);
    }
}
