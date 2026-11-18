package dev.axonia.communication.adapter.out.chat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import dev.axonia.communication.application.note.FormattedNote;
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

/** The note as Slack mrkdwn. Member text passes through Slack's escape set, so it can never plant a link. */
final class SlackNote {

    private static final String NESTED_INDENT = "    ";

    private SlackNote() {
    }

    static String of(FormattedNote note, int maxLength) {

        return NoteFit.of(note, block -> block(block, ""), "\n\n", maxLength);
    }

    static String escape(String raw) {

        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String block(Block block, String indent) {

        return switch (block) {
            case Paragraph paragraph -> inlines(paragraph.inlines());
            case BulletList list -> items(list.items(), false, indent);
            case NumberedList list -> items(list.items(), true, indent);
        };
    }

    private static String items(List<Item> items, boolean numbered, String indent) {

        var lines = new ArrayList<String>();
        for (var i = 0; i < items.size(); i++) {
            lines.addAll(item(items.get(i), numbered ? (i + 1) + ". " : indent.isEmpty() ? "• " : "◦ ", indent));
        }
        return String.join("\n", lines);
    }

    private static List<String> item(Item item, String marker, String indent) {

        var continuation = indent + " ".repeat(marker.length());
        var parts = item.parts();
        var lines = new ArrayList<String>();
        if (parts.isEmpty() || !(parts.getFirst() instanceof Paragraph)) {
            lines.add(indent + marker);
        }
        for (var j = 0; j < parts.size(); j++) {
            if (parts.get(j) instanceof Paragraph paragraph) {
                var text = inlines(paragraph.inlines()).replace("\n", "\n" + continuation);
                lines.add((j == 0 ? indent + marker : continuation) + text);
            } else {
                lines.add(block(parts.get(j), indent + NESTED_INDENT));
            }
        }
        return lines;
    }

    private static String inlines(List<Inline> inlines) {

        return inlines.stream().map(SlackNote::inline).collect(Collectors.joining());
    }

    private static String inline(Inline inline) {

        return switch (inline) {
            case Text text -> escape(text.value());
            case LineBreak ignored -> "\n";
            case Strong strong -> wrapped("*", strong.children());
            case Emphasis emphasis -> wrapped("_", emphasis.children());
            case Link link -> "<" + url(link.url()) + "|" + label(link) + ">";
        };
    }

    private static String label(Link link) {

        return inlines(link.children()).replace("|", "∣");
    }

    /** Slack honours a marker pair within one line only, so a run that breaks is wrapped line by line. */
    private static String wrapped(String marker, List<Inline> children) {

        return Arrays.stream(inlines(children).split("\n", -1))
                .map(line -> line.isEmpty() ? line : marker + line + marker).collect(Collectors.joining("\n"));
    }

    private static String url(String url) {

        return url.replace("&", "&amp;").replace("<", "%3C").replace(">", "%3E").replace("|", "%7C").replace(" ",
                "%20");
    }
}
