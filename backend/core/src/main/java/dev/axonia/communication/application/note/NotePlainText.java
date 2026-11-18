package dev.axonia.communication.application.note;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

/** The note for the text part of a mail: markers dropped, links spelled out, lists as dashes and numbers. */
public final class NotePlainText {

    private static final String INDENT = "  ";

    private NotePlainText() {
    }

    public static String of(FormattedNote note) {

        return note.blocks().stream().map(block -> block(block, "")).collect(Collectors.joining("\n\n"));
    }

    private static String block(Block block, String indent) {

        return switch (block) {
            case Paragraph paragraph -> inlines(paragraph.inlines());
            case BulletList list -> items(list.items(), false, indent);
            case NumberedList list -> items(list.items(), true, indent);
        };
    }

    private static String items(List<Item> items, boolean numbered, String indent) {

        return IntStream.range(0, items.size())
                .mapToObj(i -> item(items.get(i), numbered ? (i + 1) + ". " : "- ", indent))
                .collect(Collectors.joining("\n"));
    }

    private static String item(Item item, String marker, String indent) {

        var continuation = indent + " ".repeat(marker.length());
        var lines = new StringBuilder(indent).append(marker);
        var parts = item.parts();
        for (var j = 0; j < parts.size(); j++) {
            if (parts.get(j) instanceof Paragraph paragraph) {
                lines.append(j == 0 ? "" : "\n" + continuation)
                        .append(inlines(paragraph.inlines()).replace("\n", "\n" + continuation));
            } else {
                lines.append('\n').append(block(parts.get(j), indent + INDENT));
            }
        }
        return lines.toString();
    }

    private static String inlines(List<Inline> inlines) {

        return inlines.stream().map(NotePlainText::inline).collect(Collectors.joining());
    }

    private static String inline(Inline inline) {

        return switch (inline) {
            case Text text -> text.value();
            case LineBreak ignored -> "\n";
            case Strong strong -> inlines(strong.children());
            case Emphasis emphasis -> inlines(emphasis.children());
            case Link link -> link(link);
        };
    }

    private static String link(Link link) {

        var label = inlines(link.children());
        return label.equals(link.url()) ? label : label + " (" + link.url() + ")";
    }
}
