package dev.axonia.communication.application.note;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.axonia.communication.application.note.FormattedNote.BulletList;
import dev.axonia.communication.application.note.FormattedNote.Emphasis;
import dev.axonia.communication.application.note.FormattedNote.Item;
import dev.axonia.communication.application.note.FormattedNote.LineBreak;
import dev.axonia.communication.application.note.FormattedNote.Link;
import dev.axonia.communication.application.note.FormattedNote.Paragraph;
import dev.axonia.communication.application.note.FormattedNote.Strong;
import dev.axonia.communication.application.note.FormattedNote.Text;

import static org.assertj.core.api.Assertions.assertThat;

class FormattedNoteTest {

    @Test
    void aParagraphWithoutVisibleTextIsNoBlock() {

        var note = new FormattedNote(List.of(new Paragraph(List.of(new LineBreak(), new Text(" "))),
                new Paragraph(List.of(new Strong(List.of()), new Emphasis(List.of()))),
                new Paragraph(List.of(new Link("https://e.com", List.of())))));

        assertThat(note.isEmpty()).isTrue();
    }

    @Test
    void textInsideAnyInlineOrAnyListKeepsItsBlock() {

        var note = new FormattedNote(List.of(new Paragraph(List.of(new Strong(List.of(new Text("a"))))),
                new Paragraph(List.of(new Emphasis(List.of(new Text("b"))))),
                new Paragraph(List.of(new Link("https://e.com", List.of(new Text("c"))))),
                new BulletList(List.of(new Item(List.of())))));

        assertThat(note.blocks()).hasSize(4);
    }
}
